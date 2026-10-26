package com.resqmesh.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class RelayPeerDelivery(
    val attempts: Int = 0,
    val nextAttemptAtMillis: Long = 0L,
    val acknowledged: Boolean = false,
    val lastError: String? = null
)

data class EmergencyRelayQueueItem(
    val report: EmergencyReport,
    val queuedAtMillis: Long,
    val expiresAtMillis: Long,
    val excludedPeerIds: Set<String> = emptySet(),
    val deliveries: Map<String, RelayPeerDelivery> = emptyMap()
) {
    val key: String get() = "${report.id}#${report.contentRevision}"
}

interface EmergencyRelayQueueRepository {
    fun list(nowMillis: Long = System.currentTimeMillis()): List<EmergencyRelayQueueItem>
    fun upsert(item: EmergencyRelayQueueItem): EmergencyRelayQueueItem?
    fun update(
        key: String,
        nowMillis: Long = System.currentTimeMillis(),
        transform: (EmergencyRelayQueueItem) -> EmergencyRelayQueueItem
    ): EmergencyRelayQueueItem?
    fun removeExpired(nowMillis: Long = System.currentTimeMillis())
}

object EmergencyRelayQueueCodec {
    fun encode(items: List<EmergencyRelayQueueItem>): JSONArray = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().apply {
                put(KEY_REPORT, EmergencyReportJsonCodec.encode(item.report))
                put(KEY_QUEUED_AT, item.queuedAtMillis)
                put(KEY_EXPIRES_AT, item.expiresAtMillis)
                put(KEY_EXCLUDED_PEERS, JSONArray(item.excludedPeerIds.toList()))
                put(KEY_DELIVERIES, JSONArray().apply {
                    item.deliveries.forEach { (peerId, delivery) ->
                        put(JSONObject().apply {
                            put(KEY_PEER_ID, peerId)
                            put(KEY_ATTEMPTS, delivery.attempts)
                            put(KEY_NEXT_ATTEMPT, delivery.nextAttemptAtMillis)
                            put(KEY_ACKNOWLEDGED, delivery.acknowledged)
                            delivery.lastError?.let { put(KEY_LAST_ERROR, it) }
                        })
                    }
                })
            })
        }
    }

    fun decode(raw: String?): List<EmergencyRelayQueueItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val report = EmergencyReportJsonCodec.decode(json.optJSONObject(KEY_REPORT)) ?: continue
                    val queuedAt = json.optLong(KEY_QUEUED_AT, Long.MIN_VALUE)
                    val expiresAt = json.optLong(KEY_EXPIRES_AT, Long.MIN_VALUE)
                    if (queuedAt <= 0L || expiresAt <= queuedAt) continue
                    val excluded = buildSet {
                        val peers = json.optJSONArray(KEY_EXCLUDED_PEERS) ?: JSONArray()
                        for (peerIndex in 0 until peers.length()) {
                            peers.optString(peerIndex).trim().takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                    val deliveries = linkedMapOf<String, RelayPeerDelivery>()
                    val deliveryArray = json.optJSONArray(KEY_DELIVERIES) ?: JSONArray()
                    for (deliveryIndex in 0 until deliveryArray.length()) {
                        val deliveryJson = deliveryArray.optJSONObject(deliveryIndex) ?: continue
                        val peerId = deliveryJson.optString(KEY_PEER_ID).trim()
                        val attempts = deliveryJson.optInt(KEY_ATTEMPTS, 0)
                        val nextAttempt = deliveryJson.optLong(KEY_NEXT_ATTEMPT, 0L)
                        if (peerId.isBlank() || attempts < 0 || nextAttempt < 0L) continue
                        deliveries[peerId] = RelayPeerDelivery(
                            attempts = attempts,
                            nextAttemptAtMillis = nextAttempt,
                            acknowledged = deliveryJson.optBoolean(KEY_ACKNOWLEDGED, false),
                            lastError = deliveryJson.optString(KEY_LAST_ERROR)
                                .trim()
                                .takeIf { it.isNotBlank() }
                        )
                    }
                    add(
                        EmergencyRelayQueueItem(
                            report = report,
                            queuedAtMillis = queuedAt,
                            expiresAtMillis = expiresAt,
                            excludedPeerIds = excluded,
                            deliveries = deliveries
                        )
                    )
                }
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private const val KEY_REPORT = "report"
    private const val KEY_QUEUED_AT = "queued_at"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_EXCLUDED_PEERS = "excluded_peers"
    private const val KEY_DELIVERIES = "deliveries"
    private const val KEY_PEER_ID = "peer_id"
    private const val KEY_ATTEMPTS = "attempts"
    private const val KEY_NEXT_ATTEMPT = "next_attempt_at"
    private const val KEY_ACKNOWLEDGED = "acknowledged"
    private const val KEY_LAST_ERROR = "last_error"
}

class EmergencyRelayQueueStore(context: Context) : EmergencyRelayQueueRepository {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    override fun list(nowMillis: Long): List<EmergencyRelayQueueItem> =
        synchronized(lock) { pruneLocked(nowMillis) }

    override fun upsert(item: EmergencyRelayQueueItem): EmergencyRelayQueueItem? = synchronized(lock) {
        val current = pruneLocked(item.queuedAtMillis).toMutableList()
        val existingIndex = current.indexOfFirst { it.report.id == item.report.id }
        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            when {
                item.report.contentRevision < existing.report.contentRevision -> existing
                item.report.contentRevision == existing.report.contentRevision -> {
                    val merged = existing.copy(
                        report = existing.report.copy(status = item.report.status),
                        excludedPeerIds = existing.excludedPeerIds + item.excludedPeerIds,
                        deliveries = existing.deliveries + item.deliveries
                    )
                    current[existingIndex] = merged
                    persistLocked(trim(current))
                    merged
                }
                else -> {
                    current[existingIndex] = item
                    persistLocked(trim(current))
                    item
                }
            }
        } else {
            current += item
            persistLocked(trim(current))
            item
        }
    }

    override fun update(
        key: String,
        nowMillis: Long,
        transform: (EmergencyRelayQueueItem) -> EmergencyRelayQueueItem
    ): EmergencyRelayQueueItem? = synchronized(lock) {
        val current = pruneLocked(nowMillis).toMutableList()
        val index = current.indexOfFirst { it.key == key }
        if (index < 0) return@synchronized null
        val updated = transform(current[index])
        current[index] = updated
        persistLocked(trim(current))
        updated
    }

    override fun removeExpired(nowMillis: Long) {
        synchronized(lock) { pruneLocked(nowMillis) }
    }

    private fun pruneLocked(nowMillis: Long): List<EmergencyRelayQueueItem> {
        val loaded = EmergencyRelayQueueCodec.decode(preferences.getString(KEY_ITEMS, null))
        val valid = trim(loaded.filter { it.expiresAtMillis > nowMillis })
        if (valid != loaded) persistLocked(valid)
        return valid
    }

    private fun persistLocked(items: List<EmergencyRelayQueueItem>) {
        val encoded = EmergencyRelayQueueCodec.encode(items).toString()
        if (preferences.getString(KEY_ITEMS, null) != encoded) {
            preferences.edit().putString(KEY_ITEMS, encoded).commit()
        }
    }

    private fun trim(items: List<EmergencyRelayQueueItem>): List<EmergencyRelayQueueItem> =
        items
            .distinctBy { it.key }
            .sortedWith(
                compareByDescending<EmergencyRelayQueueItem> { severityRank(it.report.severity) }
                    .thenByDescending { it.queuedAtMillis }
            )
            .take(MAX_ITEMS)

    private fun severityRank(severity: EmergencySeverity): Int = when (severity) {
        EmergencySeverity.CRITICAL -> 4
        EmergencySeverity.HIGH -> 3
        EmergencySeverity.MEDIUM -> 2
        EmergencySeverity.LOW -> 1
    }

    private companion object {
        const val PREFERENCES_NAME = "resqmesh_emergency_relay_queue"
        const val KEY_ITEMS = "items"
        const val MAX_ITEMS = 100
    }
}
