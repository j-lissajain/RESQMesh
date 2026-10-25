package com.resqmesh.app.maps

import com.resqmesh.app.data.EmergencyLocation
import com.resqmesh.app.data.EmergencyLocationSource

data class OfflinePlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: String
)

data class MapBounds(
    val minLatitude: Double,
    val minLongitude: Double,
    val maxLatitude: Double,
    val maxLongitude: Double
) {
    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude in minLatitude..maxLatitude && longitude in minLongitude..maxLongitude
}

object NagpurMapData {
    const val DATASET_DIRECTORY = "nagpur"
    const val DATASET_SOURCE = "OpenStreetMap API map extracts, downloaded October 25, 2026"
    const val ATTRIBUTION = "© OpenStreetMap contributors · ODbL"

    val bounds = MapBounds(
        minLatitude = 21.10,
        minLongitude = 79.00,
        maxLatitude = 21.20,
        maxLongitude = 79.10
    )

    val centerLatitude: Double = (bounds.minLatitude + bounds.maxLatitude) / 2.0
    val centerLongitude: Double = (bounds.minLongitude + bounds.maxLongitude) / 2.0

    fun isCovered(latitude: Double, longitude: Double): Boolean = bounds.contains(latitude, longitude)

    fun selectedLocation(latitude: Double, longitude: Double, timestampMillis: Long): EmergencyLocation =
        EmergencyLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = null,
            timestampMillis = timestampMillis,
            source = EmergencyLocationSource.MAP,
            isVerified = false
        )
}
