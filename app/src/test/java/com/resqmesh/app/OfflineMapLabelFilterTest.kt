package com.resqmesh.app

import com.resqmesh.app.maps.MapLabelCandidate
import com.resqmesh.app.maps.OfflineMapLabelFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMapLabelFilterTest {
    @Test
    fun selection_prefersImportantLabelsAndRemovesOverlapAndDuplicates() {
        val selected = OfflineMapLabelFilter.select(
            candidates = listOf(
                MapLabelCandidate("Minor road", 0f, 20f, 70f, 12f, priority = 1),
                MapLabelCandidate("Important landmark", 2f, 20f, 90f, 12f, priority = 5),
                MapLabelCandidate("Important landmark", 120f, 80f, 90f, 12f, priority = 5),
                MapLabelCandidate("Separate place", 140f, 80f, 80f, 12f, priority = 3)
            ),
            maxLabels = 10
        )

        assertEquals(listOf("Important landmark", "Separate place"), selected.map { it.name })
        assertTrue(selected.none { it.name == "Minor road" })
    }
}
