package com.resqmesh.app.maps

data class OfflineMapPoint(
    val latitude: Double,
    val longitude: Double
) {
    fun isValid(): Boolean = latitude.isFinite() && longitude.isFinite()
}

enum class OfflineMapFeatureKind {
    ROAD,
    WATER,
    RAIL
}

data class OfflineMapFeature(
    val points: List<OfflineMapPoint>,
    val kind: OfflineMapFeatureKind,
    val name: String?,
    val minZoom: Float,
    val bounds: MapBounds = points.toMapBounds()
)

data class OfflineMapDataset(
    val bounds: MapBounds,
    val features: List<OfflineMapFeature>,
    val places: List<OfflinePlace>
)

object OfflinePlaceSearch {
    fun search(places: List<OfflinePlace>, query: String, limit: Int = 20): List<OfflinePlace> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()
        return places
            .filter { it.name.lowercase().contains(normalized) }
            .sortedWith(compareBy<OfflinePlace> { !it.name.lowercase().startsWith(normalized) }.thenBy { it.name })
            .take(limit)
    }
}

private fun List<OfflineMapPoint>.toMapBounds(): MapBounds = MapBounds(
    minLatitude = minOf { it.latitude },
    minLongitude = minOf { it.longitude },
    maxLatitude = maxOf { it.latitude },
    maxLongitude = maxOf { it.longitude }
)
