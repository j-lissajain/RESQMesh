package com.resqmesh.app.bluetooth

import com.resqmesh.app.data.AssistanceResponse
import com.resqmesh.app.data.AssistanceResponseStore
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencyReportRepository
import com.resqmesh.app.data.EmergencyRevisionRules
import com.resqmesh.app.data.MeshRecord
import com.resqmesh.app.data.MeshRecordCodec
import com.resqmesh.app.data.MeshRecordType
import com.resqmesh.app.data.ResourceRecord
import com.resqmesh.app.data.ResourceStore
import com.resqmesh.app.data.VolunteerProfile
import com.resqmesh.app.data.VolunteerProfileStore
import com.resqmesh.app.data.toAssistanceResponse
import com.resqmesh.app.data.toResourceRecord
import com.resqmesh.app.data.toVolunteerProfile
import com.resqmesh.app.data.toMeshRecord
import com.resqmesh.app.data.EmergencyReportJsonCodec
import com.resqmesh.app.data.EmergencyLifecycleStatus
import android.os.Handler
import android.os.Looper

interface MeshRecordQueueRepository {
    fun list(nowMillis: Long = System.currentTimeMillis()): List<MeshRecord>
    fun upsert(record: MeshRecord, expiresAtMillis: Long): MeshRecord?
    fun remove(key: String)
    fun update(key: String, transform: (MeshQueueEntry) -> MeshQueueEntry): MeshQueueEntry?
}

data class MeshQueueEntry(
    val record: MeshRecord,
    val expiresAtMillis: Long,
    val attemptsByPeer: Map<String, Int> = emptyMap(),
    val acknowledgedPeers: Set<String> = emptySet(),
    val lastError: String? = null
)

class MeshRecordQueueStore(context: android.content.Context) : MeshRecordQueueRepository {
    private val preferences = context.applicationContext.getSharedPreferences("resqmesh_mesh_queue", android.content.Context.MODE_PRIVATE)
    private val lock = Any()

    override fun list(nowMillis: Long): List<MeshRecord> = synchronized(lock) {
        loadEntries().filter { it.expiresAtMillis > nowMillis }.map { it.record }
    }

    fun listEntries(nowMillis: Long = System.currentTimeMillis()): List<MeshQueueEntry> = synchronized(lock) {
        val entries = loadEntries().filter { it.expiresAtMillis > nowMillis }
        persist(entries)
        entries
    }

    override fun upsert(record: MeshRecord, expiresAtMillis: Long): MeshRecord? = synchronized(lock) {
        val entries = loadEntries().toMutableList()
        val index = entries.indexOfFirst { it.record.recordId == record.recordId }
        if (index < 0) entries += MeshQueueEntry(record, expiresAtMillis)
        else if (record.revision > entries[index].record.revision) entries[index] = MeshQueueEntry(record, expiresAtMillis)
        else if (record.revision == entries[index].record.revision) {
            entries[index] = entries[index].copy(record = record)
        }
        persist(entries.takeLast(MAX_ENTRIES))
        entries.firstOrNull { it.record.recordId == record.recordId }?.record
    }

    override fun remove(key: String) = synchronized(lock) {
        persist(loadEntries().filterNot { it.record.key == key })
    }

    override fun update(key: String, transform: (MeshQueueEntry) -> MeshQueueEntry): MeshQueueEntry? = synchronized(lock) {
        val entries = loadEntries().toMutableList()
        val index = entries.indexOfFirst { it.record.key == key }
        if (index < 0) return@synchronized null
        entries[index] = transform(entries[index])
        persist(entries)
        entries[index]
    }

    private fun loadEntries(): List<MeshQueueEntry> = runCatching {
        val array = org.json.JSONArray(preferences.getString("entries", "[]") ?: "[]")
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val payload = item.optString("payload").toByteArray(Charsets.UTF_8)
                val record = MeshRecordCodec.decode(payload) ?: continue
                val attempts = mutableMapOf<String, Int>()
                val attemptJson = item.optJSONObject("attempts") ?: org.json.JSONObject()
                attemptJson.keys().forEach { peer -> attempts[peer] = attemptJson.optInt(peer, 0) }
                val acks = buildSet {
                    val ackArray = item.optJSONArray("acks") ?: org.json.JSONArray()
                    for (j in 0 until ackArray.length()) add(ackArray.optString(j))
                }
                add(MeshQueueEntry(record, item.optLong("expires_at"), attempts, acks, item.optString("last_error").takeIf { it.isNotBlank() }))
            }
        }
    }.getOrElse { emptyList() }

    private fun persist(entries: List<MeshQueueEntry>) {
        val array = org.json.JSONArray().apply {
            entries.distinctBy { it.record.recordId }.take(MAX_ENTRIES).forEach { entry ->
                put(org.json.JSONObject().apply {
                    put("payload", String(MeshRecordCodec.encode(entry.record), Charsets.UTF_8))
                    put("expires_at", entry.expiresAtMillis)
                    put("attempts", org.json.JSONObject().apply { entry.attemptsByPeer.forEach { (k, v) -> put(k, v) } })
                    put("acks", org.json.JSONArray(entry.acknowledgedPeers.toList()))
                    entry.lastError?.let { put("last_error", it) }
                })
            }
        }
        preferences.edit().putString("entries", array.toString()).commit()
    }

    private companion object { const val MAX_ENTRIES = 150 }
}

class MeshSeenStore(context: android.content.Context) {
    private val preferences = context.applicationContext.getSharedPreferences("resqmesh_mesh_seen", android.content.Context.MODE_PRIVATE)
    private val lock = Any()

    fun recordIfNew(record: MeshRecord): Boolean = synchronized(lock) {
        val seen = runCatching {
            org.json.JSONArray(preferences.getString("keys", "[]") ?: "[]")
        }.getOrElse { org.json.JSONArray() }
        val key = record.key
        for (i in 0 until seen.length()) if (seen.optString(i) == key) return@synchronized false
        seen.put(key)
        while (seen.length() > MAX_ENTRIES) seen.remove(0)
        preferences.edit().putString("keys", seen.toString()).commit()
        true
    }

    private companion object { const val MAX_ENTRIES = 500 }
}

data class MeshRelayState(
    val queued: Int = 0,
    val waitingForPeer: Int = 0,
    val lastError: String? = null
)

class MeshRelayController(
    private val queue: MeshRecordQueueStore,
    private val seen: MeshSeenStore,
    private val transport: EmergencyRelayTransport,
    private val localNodeId: String,
    private val volunteerStore: VolunteerProfileStore,
    private val resourceStore: ResourceStore,
    private val responseStore: AssistanceResponseStore,
    private val emergencyRepository: EmergencyReportRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(MeshRelayState) -> Unit>()
    private val inFlight = mutableSetOf<String>()
    private var removeConnectionListener: (() -> Unit)? = null
    private var closed = false
    private var _state = MeshRelayState()

    init {
        removeConnectionListener = transport.addConnectionListener { enqueueSyncSummary(); pump() }
        publish()
    }

    val state: MeshRelayState get() = _state

    fun addListener(listener: (MeshRelayState) -> Unit): () -> Unit {
        listeners += listener
        listener(_state)
        return { listeners -= listener }
    }

    fun setReceiver() {
        transport.setMeshRecordReceiver { record, peerId -> onIncoming(record, peerId) }
    }

    fun enqueue(record: MeshRecord, excludedPeerId: String? = null) {
        if (closed || MeshRecordCodec.validate(record) != null) return
        queue.upsert(record, nowMillis() + QUEUE_TTL_MILLIS)
        pump(excludedPeerId)
    }

    fun enqueueVolunteer(profile: VolunteerProfile) = enqueue(profile.toMeshRecord())
    fun enqueueResource(resource: ResourceRecord) = enqueue(resource.toMeshRecord())
    fun enqueueResponse(response: AssistanceResponse) = enqueue(response.toMeshRecord())

    fun enqueueEmergencyLifecycleUpdate(report: EmergencyReport) {
        enqueue(
            MeshRecord(
                recordId = report.id,
                type = MeshRecordType.EMERGENCY_UPDATE,
                originId = report.originId ?: localNodeId,
                revision = report.contentRevision,
                createdAtMillis = report.createdAtMillis,
                updatedAtMillis = report.updatedAtMillis,
                body = org.json.JSONObject().apply {
                    put("emergency_id", report.id)
                    put("lifecycle_status", report.lifecycleStatus.name)
                }
            )
        )
    }

    fun onIncoming(record: MeshRecord, peerId: String?) {
        if (closed || !seen.recordIfNew(record)) return
        when (record.type) {
            MeshRecordType.VOLUNTEER -> record.body.toVolunteerProfile(record)?.let(volunteerStore::save)
            MeshRecordType.RESOURCE -> record.body.toResourceRecord(record)?.let(resourceStore::save)
            MeshRecordType.RESPONSE -> record.body.toAssistanceResponse(record)?.let(responseStore::save)
            MeshRecordType.EMERGENCY_UPDATE -> receiveEmergencyUpdate(record)
            MeshRecordType.SYNC_SUMMARY -> receiveSyncSummary(record, peerId)
        }
        val next = MeshRecordCodec.nextHop(record, localNodeId) ?: return
        enqueue(next, peerId)
        publish()
    }

    fun refresh() = pump()

    override fun close() {
        closed = true
        removeConnectionListener?.invoke()
        handler.removeCallbacksAndMessages(null)
        listeners.clear()
    }

    private fun receiveEmergencyUpdate(record: MeshRecord) {
        val emergencyId = record.body.optString("emergency_id")
        val report = emergencyRepository.list().firstOrNull { it.id == emergencyId }
        val status = com.resqmesh.app.data.EmergencyLifecycleStatus.fromStoredValue(record.body.optString("lifecycle_status")) ?: return
        val base = report ?: record.body.optJSONObject("report")?.let(EmergencyReportJsonCodec::decode) ?: return
        val updated = base.copy(
            lifecycleStatus = status,
            contentRevision = record.revision,
            updatedAtMillis = record.updatedAtMillis,
            contentTrust = com.resqmesh.app.data.EmergencyContentTrust.REMOTE_UNVERIFIED
        )
        emergencyRepository.save(updated)
    }

    private fun receiveSyncSummary(record: MeshRecord, peerId: String?) {
        val keys = buildSet {
            val array = record.body.optJSONArray("keys") ?: org.json.JSONArray()
            for (i in 0 until array.length()) add(array.optString(i))
        }
        val local = allLocalRecords()
        local.filter { it.key !in keys }.forEach { enqueue(MeshRecordCodec.nextHop(it, localNodeId) ?: it, peerId) }
    }

    private fun allLocalRecords(): List<MeshRecord> = buildList {
        volunteerStore.list().filter { !it.isRemoteUnverified }.forEach { add(it.toMeshRecord()) }
        resourceStore.list().filter { !it.isRemoteUnverified }.forEach { add(it.toMeshRecord()) }
        responseStore.list().filter { !it.isRemoteUnverified }.forEach { add(it.toMeshRecord()) }
        emergencyRepository.list()
            .filter { it.contentTrust == com.resqmesh.app.data.EmergencyContentTrust.LOCAL_ORIGINATED }
            .forEach { add(emergencyToMeshRecord(it)) }
    }

    private fun emergencyToMeshRecord(report: EmergencyReport): MeshRecord = MeshRecord(
        recordId = report.id,
        type = MeshRecordType.EMERGENCY_UPDATE,
        originId = report.originId ?: localNodeId,
        revision = report.contentRevision,
        createdAtMillis = report.createdAtMillis,
        updatedAtMillis = report.updatedAtMillis,
        body = org.json.JSONObject().apply {
            put("emergency_id", report.id)
            put("lifecycle_status", report.lifecycleStatus.name)
            put("report", EmergencyReportJsonCodec.encode(report))
        }
    )

    private fun enqueueSyncSummary() {
        val keys = allLocalRecords().map { it.key }
        val summary = MeshRecord(
            recordId = "sync-$localNodeId",
            type = MeshRecordType.SYNC_SUMMARY,
            originId = localNodeId,
            revision = EmergencyRevisionRules.INITIAL_REVISION,
            createdAtMillis = nowMillis(),
            updatedAtMillis = nowMillis(),
            body = org.json.JSONObject().apply { put("keys", org.json.JSONArray(keys)) }
        )
        enqueue(summary)
    }

    private fun pump(excludedPeerId: String? = null) {
        if (closed) return
        val peers = transport.connectedPeerIds()
        queue.listEntries(nowMillis()).forEach { entry ->
            val peer = peers.firstOrNull {
                it != excludedPeerId && it !in entry.acknowledgedPeers && (entry.attemptsByPeer[it] ?: 0) < MAX_ATTEMPTS
            } ?: return@forEach
            if (!inFlight.add(entry.record.key)) return@forEach
            queue.update(entry.record.key) {
                it.copy(attemptsByPeer = it.attemptsByPeer + (peer to ((it.attemptsByPeer[peer] ?: 0) + 1)))
            }
            val accepted = transport.sendMeshRecordToPeer(entry.record, peer) { result, error ->
                handler.post {
                    inFlight.remove(entry.record.key)
                    queue.update(entry.record.key) { current ->
                        if (result == EmergencyTransportResult.ACKNOWLEDGED) {
                            current.copy(acknowledgedPeers = current.acknowledgedPeers + peer, lastError = null)
                        } else current.copy(lastError = error ?: result.name)
                    }
                    publish()
                    handler.postDelayed({ pump() }, BACKOFF_MILLIS)
                }
            }
            if (!accepted) inFlight.remove(entry.record.key)
        }
        publish()
    }

    private fun publish() {
        val entries = queue.listEntries(nowMillis())
        val peers = transport.connectedPeerIds()
        _state = MeshRelayState(
            queued = entries.size,
            waitingForPeer = entries.count { entry -> peers.none { it !in entry.acknowledgedPeers } },
            lastError = entries.lastOrNull()?.lastError
        )
        listeners.toList().forEach { it(_state) }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val BACKOFF_MILLIS = 2_000L
        const val QUEUE_TTL_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
