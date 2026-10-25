package com.resqmesh.app

import com.resqmesh.app.data.EmergencyLocation
import com.resqmesh.app.data.EmergencyLocationSource
import com.resqmesh.app.data.EmergencyMapMarkerFilter
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyMapMarkerFilterTest {
    @Test
    fun validMarkers_excludesReportsWithoutCoordinates() {
        val reports = listOf(
            EmergencyReport(
                id = "with-location",
                type = EmergencyType.MEDICAL,
                severity = EmergencySeverity.CRITICAL,
                description = "Help",
                createdAtMillis = 1L,
                location = EmergencyLocation(21.1, 79.1, null, 1L, EmergencyLocationSource.GNSS)
            ),
            EmergencyReport(
                id = "without-location",
                type = EmergencyType.SHELTER,
                severity = EmergencySeverity.LOW,
                description = "Shelter needed",
                createdAtMillis = 2L
            )
        )

        assertEquals(listOf("with-location"), EmergencyMapMarkerFilter.validMarkers(reports).map { it.id })
    }
}
