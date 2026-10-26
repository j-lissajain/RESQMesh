package com.resqmesh.app

import com.resqmesh.app.data.EmergencySeenCodec
import com.resqmesh.app.data.EmergencySeenEntry
import com.resqmesh.app.data.EmergencySeenTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencySeenTrackerTest {
    @Test
    fun firstSightingIsRecordedByEmergencyId() {
        val updated = EmergencySeenTracker.record(
            entries = emptyList(),
            emergencyId = "emergency-1",
            atMillis = 100L,
            lastHopId = "peer-a"
        )

        assertTrue(EmergencySeenTracker.contains(updated, "emergency-1"))
        assertEquals(1, updated.single().sightingCount)
        assertEquals("peer-a", updated.single().lastHopId)
        assertEquals(100L, updated.single().firstSeenAtMillis)
    }

    @Test
    fun duplicateSightingUpdatesMetadataWithoutBlockingLaterUpdates() {
        val first = EmergencySeenTracker.record(
            entries = emptyList(),
            emergencyId = "emergency-1",
            atMillis = 100L,
            lastHopId = "peer-a"
        )
        val second = EmergencySeenTracker.record(
            entries = first,
            emergencyId = "emergency-1",
            atMillis = 250L,
            lastHopId = "peer-b"
        )

        assertEquals(1, second.size)
        assertEquals(2, second.single().sightingCount)
        assertEquals(100L, second.single().firstSeenAtMillis)
        assertEquals(250L, second.single().lastSeenAtMillis)
        assertEquals("peer-b", second.single().lastHopId)
        assertTrue(EmergencySeenTracker.contains(second, "emergency-1"))
    }

    @Test
    fun trackerDistinguishesContentRevisionsForTheSameEmergency() {
        val revisionOne = EmergencySeenTracker.record(
            entries = emptyList(),
            emergencyId = "emergency-1",
            contentRevision = 1,
            atMillis = 100L
        )
        val revisionTwo = EmergencySeenTracker.record(
            entries = revisionOne,
            emergencyId = "emergency-1",
            contentRevision = 2,
            atMillis = 200L
        )

        assertTrue(EmergencySeenTracker.contains(revisionTwo, "emergency-1", 1))
        assertTrue(EmergencySeenTracker.contains(revisionTwo, "emergency-1", 2))
        assertEquals(2, revisionTwo.size)
    }

    @Test
    fun trackerDoesNotTreatUnknownIdsAsDuplicates() {
        val entries = EmergencySeenTracker.record(
            entries = emptyList(),
            emergencyId = "emergency-1",
            atMillis = 100L
        )
        assertFalse(EmergencySeenTracker.contains(entries, "emergency-2"))
    }

    @Test
    fun oldestEntriesAreEvictedWhenTheCapIsReached() {
        var entries = emptyList<EmergencySeenEntry>()
        repeat(3) { index ->
            entries = EmergencySeenTracker.record(
                entries = entries,
                emergencyId = "emergency-$index",
                atMillis = (index + 1) * 10L,
                maxEntries = 2
            )
        }

        assertEquals(2, entries.size)
        assertFalse(EmergencySeenTracker.contains(entries, "emergency-0"))
        assertTrue(EmergencySeenTracker.contains(entries, "emergency-1"))
        assertTrue(EmergencySeenTracker.contains(entries, "emergency-2"))
    }

    @Test
    fun codecRoundTripsSeenEntries() {
        val original = EmergencySeenTracker.record(
            entries = emptyList(),
            emergencyId = "emergency-1",
            atMillis = 50L,
            lastHopId = "peer-a"
        )
        val restored = EmergencySeenCodec.decode(EmergencySeenCodec.encode(original).toString())
        assertEquals(original, restored)
    }

    @Test
    fun legacyCodecEntriesDefaultToRevisionOne() {
        val restored = EmergencySeenCodec.decode(
            "[{\"emergency_id\":\"legacy\",\"first_seen_at\":10,\"last_seen_at\":10,\"sighting_count\":1}]"
        )

        assertEquals(1, restored.single().contentRevision)
    }
}
