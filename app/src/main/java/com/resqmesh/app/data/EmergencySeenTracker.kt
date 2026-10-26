package com.resqmesh.app.data

data class EmergencySeenEntry(
    val emergencyId: String,
    val contentRevision: Int = EmergencyRevisionRules.INITIAL_REVISION,
    val firstSeenAtMillis: Long,
    val lastSeenAtMillis: Long,
    val lastHopId: String?,
    val sightingCount: Int
)

/**
 * Persistent duplicate tracking by emergency ID and content revision, independent
 * of BLE frame IDs. Recording one revision does not block a later revision.
 */
object EmergencySeenTracker {
    const val MAX_ENTRIES = 200

    fun contains(
        entries: List<EmergencySeenEntry>,
        emergencyId: String,
        contentRevision: Int = EmergencyRevisionRules.INITIAL_REVISION
    ): Boolean = entries.any {
        it.emergencyId == emergencyId && it.contentRevision == contentRevision
    }

    fun containsAnyRevision(entries: List<EmergencySeenEntry>, emergencyId: String): Boolean =
        entries.any { it.emergencyId == emergencyId }

    fun record(
        entries: List<EmergencySeenEntry>,
        emergencyId: String,
        contentRevision: Int = EmergencyRevisionRules.INITIAL_REVISION,
        atMillis: Long,
        lastHopId: String? = null,
        maxEntries: Int = MAX_ENTRIES
    ): List<EmergencySeenEntry> {
        val id = emergencyId.trim()
        if (id.isEmpty() || atMillis <= 0L || !EmergencyRevisionRules.isValid(contentRevision)) return entries
        val hopId = EmergencyEnvelopeRules.normalize(lastHopId)
        val current = entries.firstOrNull {
            it.emergencyId == id && it.contentRevision == contentRevision
        }
        val updated = if (current == null) {
            EmergencySeenEntry(
                emergencyId = id,
                contentRevision = contentRevision,
                firstSeenAtMillis = atMillis,
                lastSeenAtMillis = atMillis,
                lastHopId = hopId,
                sightingCount = 1
            )
        } else {
            current.copy(
                lastSeenAtMillis = maxOf(current.lastSeenAtMillis, atMillis),
                lastHopId = hopId ?: current.lastHopId,
                sightingCount = current.sightingCount + 1
            )
        }
        return trim(
            entries = entries.filterNot {
                it.emergencyId == id && it.contentRevision == contentRevision
            } + updated,
            maxEntries = maxEntries
        )
    }

    fun trim(entries: List<EmergencySeenEntry>, maxEntries: Int = MAX_ENTRIES): List<EmergencySeenEntry> =
        entries.sortedByDescending { it.lastSeenAtMillis }.take(maxEntries.coerceAtLeast(1))
}
