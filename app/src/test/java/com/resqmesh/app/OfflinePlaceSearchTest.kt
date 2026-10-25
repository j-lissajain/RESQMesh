package com.resqmesh.app

import com.resqmesh.app.maps.OfflinePlace
import com.resqmesh.app.maps.OfflinePlaceSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePlaceSearchTest {
    private val places = listOf(
        OfflinePlace("Central Avenue Road", 21.145, 79.11, "Road"),
        OfflinePlace("Cemetery Road", 21.14, 79.08, "Road"),
        OfflinePlace("Deekshabhoomi", 21.12, 79.07, "Landmark")
    )

    @Test
    fun search_isCaseInsensitiveAndSupportsPartialMatches() {
        val results = OfflinePlaceSearch.search(places, "avenue")

        assertEquals(listOf("Central Avenue Road"), results.map { it.name })
    }

    @Test
    fun search_prefersPrefixMatchesAndReturnsEmptyForUnknownPlace() {
        val results = OfflinePlaceSearch.search(places, "central")

        assertTrue(results.isNotEmpty())
        assertEquals("Central Avenue Road", results.first().name)
        assertTrue(OfflinePlaceSearch.search(places, "not in dataset").isEmpty())
    }
}
