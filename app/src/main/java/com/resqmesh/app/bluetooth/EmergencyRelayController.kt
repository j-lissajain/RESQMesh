package com.resqmesh.app.bluetooth

import com.resqmesh.app.data.EmergencyContentTrust
import com.resqmesh.app.data.EmergencyEnvelope
import com.resqmesh.app.data.EmergencyEnvelopeRules
import com.resqmesh.app.data.EmergencyRelayQueueItem
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencyReportStatus
import com.resqmesh.app.data.EmergencyReportValidation
import com.resqmesh.app.data.EmergencyRelayPolicy
import com.resqmesh.app.data.EmergencyReportRepository
import com.resqmesh.app.data.EmergencySeenRepository
import com.resqmesh.app.data.MeshRecord
import com.resqmesh.app.security.MeshTrustState

enum class EmergencyTransportResult {
    ACKNOWLEDGED,
    FAILED,
    DISCONNECTED
}

interface EmergencyRelayTransport {
    fun connectedPeerIds(): Set<String>

    fun sendEmergencyToPeer(
        report: EmergencyReport,
        peerId: String,
        callback: (EmergencyTransportResult, String?) -> Unit
    ): Boolean

    fun sendMeshRecordToPeer(
        record: MeshRecord,
        peerId: String,
        callback: (EmergencyTransportResult, String?) -> Unit
    ): Boolean = false

    fun setMeshRecordReceiver(receiver: ((MeshRecord, String?, MeshTrustState) -> Unit)?) = Unit

    fun addConnectionListener(listener: () -> Unit): () -> Unit = {}
}

fun interface RelayScheduler {
    fun post(delayMillis: Long, action: () -> Unit)

    fun cancelAll() = Unit
}

private class HandlerRelayScheduler : RelayScheduler {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun post(delayMillis: Long, action: () -> Unit) {
        handler.postDelayed(action, delayMillis)
    }

    override fun cancelAll() {
        handler.removeCallbacksAndMessages(null)
    }
}

data class RelayDebugItem(
    val key: String,
    val emergencyId: String,
    val revision: Int,
    val hopCount: Int,
    val remainingTtl: Int,
    val acknowledgedPeers: Int,
    val attempts: Int,
    val lastError: String?
)

data class EmergencyRelayState(
    val queuedItems: List<RelayDebugItem> = emptyList(),
    val waitingForPeer: Int = 0,
    val inFlight: Int = 0,
    val lastError: String? = null
)

/**
 * Application-level, persistent emergency relay coordinator. It owns no UI and
 * only forwards validated content; transport acknowledgements are per peer.
 */
class EmergencyRelayController(
    private val repository: EmergencyReportRepository,
    private val seenStore: EmergencySeenRepository,
    private val queueStore: com.resqmesh.app.data.EmergencyRelayQueueRepository,
    private val transport: EmergencyRelayTransport,
    private val localNodeId: String,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val scheduler: RelayScheduler = HandlerRelayScheduler()
) : AutoCloseable {
    private val listeners = mutableSetOf<(EmergencyRelayState) -> Unit>()
    private val inFlight = mutableSetOf<String>()
    private var connectionListenerRemove: (() -> Unit)? = null
    private var closed = false
    private var _state = EmergencyRelayState()

    val state: EmergencyRelayState
        get() = _state

    init {
        connectionListenerRemove = transport.addConnectionListener { schedulePump(0L) }
        publishState()
    }

    fun addListener(listener: (EmergencyRelayState) -> Unit): () -> Unit {
        if (closed) {
            listener(_state)
            return {}
        }
        listeners += listener
        listener(_state)
        return { listeners -= listener }
    }

    fun enqueue(report: EmergencyReport, excludedPeerId: String? = null): Boolean {
        if (closed || !isValid(report) || !canBeQueuedForForwarding(report)) {
            if (report.contentTrust == EmergencyContentTrust.LOCAL_ORIGINATED &&
                report.status != EmergencyReportStatus.RECEIVED_FROM_PEER
            ) {
                repository.updateStatus(report.id, EmergencyReportStatus.TRANSMISSION_FAILED)
            }
            setError("Emergency could not be queued because its content or envelope is invalid or exceeds wire size limits.")
            return false
        }
        val now = nowMillis()
        val item = EmergencyRelayQueueItem(
            report = report,
            queuedAtMillis = now,
            expiresAtMillis = now + QUEUE_TTL_MILLIS,
            excludedPeerIds = excludedPeerId?.trim()?.takeIf { it.isNotBlank() }?.let(::setOf)
                ?: emptySet()
        )
        queueStore.upsert(item)
        if (report.contentTrust == EmergencyContentTrust.LOCAL_ORIGINATED &&
            report.status != EmergencyReportStatus.RECEIVED_FROM_PEER
        ) {
            repository.updateStatus(
                report.id,
                if (transport.connectedPeerIds().isEmpty()) {
                    EmergencyReportStatus.WAITING_FOR_PEER
                } else {
                    EmergencyReportStatus.QUEUED_FOR_FORWARDING
                }
            )
        }
        publishState()
        pump()
        return true
    }

    fun onIncoming(report: EmergencyReport, actualPeerId: String?) {
        if (closed || !isValid(report) || report.contentTrust !in setOf(
                EmergencyContentTrust.REMOTE_UNVERIFIED,
                EmergencyContentTrust.REMOTE_AUTHENTICATED
            )
        ) {
            setError("An untrusted emergency payload was rejected before relay.")
            return
        }

        if (!seenStore.recordIfNew(
                emergencyId = report.id,
                contentRevision = report.contentRevision,
                atMillis = nowMillis(),
                lastHopId = report.lastHopId ?: actualPeerId
            )
        ) {
            actualPeerId?.let { blockPeer(report.id, report.contentRevision, it) }
            return
        }

        val persisted = repository.save(report)
        val accepted = persisted?.let {
            it.contentRevision == report.contentRevision &&
                it.contentTrust in setOf(
                    EmergencyContentTrust.REMOTE_UNVERIFIED,
                    EmergencyContentTrust.REMOTE_AUTHENTICATED
                ) &&
                !com.resqmesh.app.data.EmergencyReportMerge.isTrustedLocalContent(it)
        } == true
        if (!accepted) return

        val nextEnvelope = EmergencyRelayPolicy.nextEnvelope(report, localNodeId)
        if (nextEnvelope == null) {
            publishState()
            return
        }

        enqueue(
            report = persisted.copy(
                status = EmergencyReportStatus.RECEIVED_FROM_PEER,
                receivedFromPeerId = actualPeerId,
                hopCount = nextEnvelope.hopCount,
                remainingTtl = nextEnvelope.remainingTtl,
                lastHopId = nextEnvelope.lastHopId
            ),
            excludedPeerId = actualPeerId
        )
    }

    fun refresh() {
        if (!closed) pump()
    }

    override fun close() {
        if (closed) return
        closed = true
        connectionListenerRemove?.invoke()
        connectionListenerRemove = null
        scheduler.cancelAll()
        listeners.clear()
    }

    private fun pump() {
        if (closed) return
        val now = nowMillis()
        queueStore.removeExpired(now)
        val connectedPeers = transport.connectedPeerIds()
        queueStore.list(now).forEach { item ->
            val candidate = EmergencyRelayPolicy.eligiblePeers(
                activePeerIds = connectedPeers,
                immediateSenderId = item.report.receivedFromPeerId,
                excludedPeerIds = item.excludedPeerIds,
                deliveries = item.deliveries,
                nowMillis = now
            ).firstOrNull()
                ?: return@forEach
            startAttempt(item, candidate, now)
        }
        publishState()
    }

    private fun startAttempt(item: EmergencyRelayQueueItem, peerId: String, now: Long) {
        if (!inFlight.add(item.key)) return
        val existing = item.deliveries[peerId] ?: com.resqmesh.app.data.RelayPeerDelivery()
        val updated = item.copy(
            deliveries = item.deliveries + (
                peerId to existing.copy(
                    attempts = existing.attempts + 1,
                    nextAttemptAtMillis = now + backoffMillis(existing.attempts + 1),
                    lastError = null
                )
            )
        )
        queueStore.update(item.key, now) { updated }
        if (updated.report.contentTrust == EmergencyContentTrust.LOCAL_ORIGINATED) {
            repository.updateStatus(updated.report.id, EmergencyReportStatus.TRANSMISSION_IN_PROGRESS)
        }
        val accepted = transport.sendEmergencyToPeer(updated.report, peerId) { result, error ->
            scheduler.post(0L) { finishAttempt(updated.key, peerId, result, error) }
        }
        if (!accepted) finishAttempt(updated.key, peerId, EmergencyTransportResult.FAILED, "Peer is no longer available.")
    }

    private fun finishAttempt(
        key: String,
        peerId: String,
        result: EmergencyTransportResult,
        error: String?
    ) {
        if (closed) return
        inFlight.remove(key)
        val updated = queueStore.update(key, nowMillis()) { item ->
            val delivery = item.deliveries[peerId] ?: com.resqmesh.app.data.RelayPeerDelivery()
            item.copy(
                deliveries = item.deliveries + (
                    peerId to delivery.copy(
                        acknowledged = result == EmergencyTransportResult.ACKNOWLEDGED,
                        lastError = if (result == EmergencyTransportResult.ACKNOWLEDGED) null else error
                            ?: result.name
                    )
                )
            )
        }
        if (result == EmergencyTransportResult.ACKNOWLEDGED &&
            updated?.report?.contentTrust == EmergencyContentTrust.LOCAL_ORIGINATED
        ) {
            repository.updateStatus(updated.report.id, EmergencyReportStatus.TRANSPORT_ACKNOWLEDGED)
        } else if (result != EmergencyTransportResult.ACKNOWLEDGED) {
            updated?.report?.let { report ->
                if (report.contentTrust == EmergencyContentTrust.LOCAL_ORIGINATED) {
                    repository.updateStatus(report.id, EmergencyReportStatus.TRANSMISSION_FAILED)
                }
            }
            if (error != null) setError(error)
        }
        publishState()
        schedulePump(backoffMillis(updated?.deliveries?.get(peerId)?.attempts ?: 1))
    }

    private fun blockPeer(emergencyId: String, revision: Int, peerId: String) {
        val key = "$emergencyId#$revision"
        queueStore.update(key, nowMillis()) { it.copy(excludedPeerIds = it.excludedPeerIds + peerId) }
    }

    private fun schedulePump(delayMillis: Long) {
        if (!closed) scheduler.post(delayMillis.coerceAtMost(EmergencyRelayPolicy.MAX_BACKOFF_MILLIS), ::pump)
    }

    private fun publishState() {
        val items = queueStore.list(nowMillis())
        val debug = items.map { item ->
            RelayDebugItem(
                key = item.key,
                emergencyId = item.report.id,
                revision = item.report.contentRevision,
                hopCount = item.report.hopCount,
                remainingTtl = item.report.remainingTtl,
                acknowledgedPeers = item.deliveries.values.count { it.acknowledged },
                attempts = item.deliveries.values.sumOf { it.attempts },
                lastError = item.deliveries.values.mapNotNull { it.lastError }.lastOrNull()
            )
        }
        val connected = transport.connectedPeerIds()
        val waiting = items.count { item ->
            connected.none { peer ->
                peer !in item.excludedPeerIds && item.deliveries[peer]?.acknowledged != true
            }
        }
        val snapshot = EmergencyRelayState(
            queuedItems = debug,
            waitingForPeer = waiting,
            inFlight = inFlight.size,
            lastError = _state.lastError
        )
        _state = snapshot
        listeners.toList().forEach { it(snapshot) }
    }

    private fun setError(message: String) {
        _state = _state.copy(lastError = message)
        publishState()
    }

    private fun isValid(report: EmergencyReport): Boolean {
        if (report.id.isBlank() || !EmergencyReportValidation.isDescriptionValid(report.description)) return false
        if (EmergencyEnvelopeRules.validate(report.hopCount, report.remainingTtl, report.lastHopId) != null) return false
        return runCatching { com.resqmesh.app.data.EmergencyWireCodec.encode(report, report.originId) }.isSuccess
    }

    private fun canBeQueuedForForwarding(report: EmergencyReport): Boolean =
        EmergencyEnvelopeRules.canForward(
            EmergencyEnvelope(report.hopCount, report.remainingTtl, report.lastHopId)
        )

    private fun backoffMillis(attempt: Int): Long {
        return EmergencyRelayPolicy.backoffMillis(attempt)
    }

    private companion object {
        const val QUEUE_TTL_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
