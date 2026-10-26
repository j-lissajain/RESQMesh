package com.resqmesh.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object EmergencySeenCodec {
    fun encode(entries: List<EmergencySeenEntry>): JSONArray = JSONArray().apply {
        entries.forEach { entry ->
            put(
                JSONObject().apply {
                    put(KEY_ID, entry.emergencyId)
                    put(KEY_REVISION, entry.contentRevision)
                    put(KEY_FIRST_SEEN, entry.firstSeenAtMillis)
                    put(KEY_LAST_SEEN, entry.lastSeenAtMillis)
                    entry.lastHopId?.let { put(KEY_LAST_HOP_ID, it) }
                    put(KEY_COUNT, entry.sightingCount)
                }
            )
        }
    }

    fun decode(raw: String?): List<EmergencySeenEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val id = json.optString(KEY_ID).trim()
                    val revision = json.optInt(KEY_REVISION, EmergencyRevisionRules.INITIAL_REVISION)
                    val firstSeen = json.optLong(KEY_FIRST_SEEN, Long.MIN_VALUE)
                    val lastSeen = json.optLong(KEY_LAST_SEEN, firstSeen)
                    val count = json.optInt(KEY_COUNT, 1)
                    if (id.isBlank() || !EmergencyRevisionRules.isValid(revision) ||
                        firstSeen <= 0L || lastSeen <= 0L || count <= 0
                    ) continue
                    add(
                        EmergencySeenEntry(
                            emergencyId = id,
                            contentRevision = revision,
                            firstSeenAtMillis = firstSeen,
                            lastSeenAtMillis = lastSeen,
                            lastHopId = json.optString(KEY_LAST_HOP_ID).trim().takeIf { it.isNotBlank() },
                            sightingCount = count
                        )
                    )
                }
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private const val KEY_ID = "emergency_id"
    private const val KEY_REVISION = "content_revision"
    private const val KEY_FIRST_SEEN = "first_seen_at"
    private const val KEY_LAST_SEEN = "last_seen_at"
    private const val KEY_LAST_HOP_ID = "last_hop_id"
    private const val KEY_COUNT = "sighting_count"
}

class EmergencySeenStore(context: Context) : EmergencySeenRepository {
    private val preferences = context.getApplicationContext()
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun list(): List<EmergencySeenEntry> = synchronized(lock) { listLocked() }

    fun contains(
        emergencyId: String,
        contentRevision: Int = EmergencyRevisionRules.INITIAL_REVISION
    ): Boolean = synchronized(lock) {
        EmergencySeenTracker.contains(listLocked(), emergencyId, contentRevision)
    }

    fun containsAnyRevision(emergencyId: String): Boolean = synchronized(lock) {
        EmergencySeenTracker.containsAnyRevision(listLocked(), emergencyId)
    }

    fun record(
        emergencyId: String,
        contentRevision: Int = EmergencyRevisionRules.INITIAL_REVISION,
        atMillis: Long = System.currentTimeMillis(),
        lastHopId: String? = null
    ): List<EmergencySeenEntry> = synchronized(lock) {
        val updated = EmergencySeenTracker.record(
            entries = listLocked(),
            emergencyId = emergencyId,
            contentRevision = contentRevision,
            atMillis = atMillis,
            lastHopId = lastHopId
        )
        persistLocked(updated)
        updated
    }

    override fun recordIfNew(
        emergencyId: String,
        contentRevision: Int,
        atMillis: Long,
        lastHopId: String?
    ): Boolean = synchronized(lock) {
        if (contains(emergencyId, contentRevision)) return@synchronized false
        record(emergencyId, contentRevision, atMillis, lastHopId)
        true
    }

    private fun listLocked(): List<EmergencySeenEntry> = EmergencySeenTracker.trim(
        EmergencySeenCodec.decode(preferences.getString(KEY_ENTRIES, null))
    )

    private fun persistLocked(entries: List<EmergencySeenEntry>) {
        val encoded = EmergencySeenCodec.encode(entries).toString()
        if (preferences.getString(KEY_ENTRIES, null) != encoded) {
            preferences.edit().putString(KEY_ENTRIES, encoded).commit()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "resqmesh_emergency_seen"
        const val KEY_ENTRIES = "entries"
    }
}
