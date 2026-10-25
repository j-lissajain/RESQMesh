package com.resqmesh.app.data

object EmergencyMapMarkerFilter {
    fun validMarkers(reports: List<EmergencyReport>): List<EmergencyReport> = reports.filter { report ->
        val location = report.location ?: return@filter false
        location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0
    }
}
