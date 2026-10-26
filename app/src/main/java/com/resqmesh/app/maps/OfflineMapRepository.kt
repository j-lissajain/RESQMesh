package com.resqmesh.app.maps

import android.content.Context
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.LinkedHashMap
import com.resqmesh.app.R

class OfflineMapRepository(context: Context) {
    private val appContext = context.applicationContext
    private val assetManager = appContext.assets

    fun datasetFiles(): List<String> = assetManager.list(NagpurMapData.DATASET_DIRECTORY).orEmpty()
        .filter { it.endsWith(".osm", ignoreCase = true) }
        .sorted()

    fun hasMapData(): Boolean = datasetFiles().isNotEmpty()

    fun datasetBytes(): Long = datasetFiles().sumOf { fileName ->
        assetManager.open("${NagpurMapData.DATASET_DIRECTORY}/$fileName").use { it.available().toLong() }
    }

    fun datasetDescription(): String = if (hasMapData()) {
        appContext.getString(R.string.map_dataset_description, datasetFiles().size, formatBytes(datasetBytes()))
    } else {
        appContext.getString(R.string.map_dataset_missing)
    }

    fun loadDataset(): OfflineMapDataset {
        val features = mutableListOf<OfflineMapFeature>()
        val places = LinkedHashMap<String, OfflinePlace>()
        datasetFiles().forEach { fileName ->
            parseFile(fileName, features, places)
        }
        return OfflineMapDataset(
            bounds = NagpurMapData.bounds,
            features = features,
            places = places.values.sortedBy { it.name }
        )
    }

    private fun parseFile(
        fileName: String,
        features: MutableList<OfflineMapFeature>,
        places: MutableMap<String, OfflinePlace>
    ) {
        val nodes = HashMap<Long, OfflineMapPoint>()
        val parser = Xml.newPullParser()
        assetManager.open("${NagpurMapData.DATASET_DIRECTORY}/$fileName").use { input ->
            parser.setInput(input, "UTF-8")
            var event = parser.eventType
            var currentNodeId: Long? = null
            var currentNode: OfflineMapPoint? = null
            var currentNodeTags = mutableMapOf<String, String>()
            var currentWayRefs = mutableListOf<Long>()
            var currentWayTags = mutableMapOf<String, String>()
            var inWay = false
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "node" -> {
                            currentNodeId = parser.getAttributeValue(null, "id")?.toLongOrNull()
                            currentNode = OfflineMapPoint(
                                latitude = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: Double.NaN,
                                longitude = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: Double.NaN
                            )
                            currentNodeTags = mutableMapOf()
                        }

                        "way" -> {
                            inWay = true
                            currentWayRefs = mutableListOf()
                            currentWayTags = mutableMapOf()
                        }

                        "nd" -> if (inWay) {
                            parser.getAttributeValue(null, "ref")?.toLongOrNull()?.let(currentWayRefs::add)
                        }

                        "tag" -> {
                            val key = parser.getAttributeValue(null, "k")
                            val value = parser.getAttributeValue(null, "v")
                            if (key != null && value != null) {
                                if (inWay) currentWayTags[key] = value else currentNodeTags[key] = value
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> when (parser.name) {
                        "node" -> {
                            val id = currentNodeId
                            val node = currentNode
                            if (id != null && node != null && node.isValid()) {
                                nodes[id] = node
                                addNamedPlace(node, currentNodeTags, places)
                            }
                            currentNodeId = null
                            currentNode = null
                            currentNodeTags = mutableMapOf()
                        }

                        "way" -> {
                            val points = currentWayRefs.mapNotNull(nodes::get)
                            if (points.size >= 2) {
                                classifyWay(points, currentWayTags)?.let(features::add)
                                addNamedWayPlace(points, currentWayTags, places)
                            }
                            inWay = false
                            currentWayRefs = mutableListOf()
                            currentWayTags = mutableMapOf()
                        }
                    }
                }
                event = parser.next()
            }
        }
    }

    private fun addNamedPlace(
        point: OfflineMapPoint,
        tags: Map<String, String>,
        places: MutableMap<String, OfflinePlace>
    ) {
        val name = tags["name"]?.trim().orEmpty()
        if (name.isBlank() || !NagpurMapData.isCovered(point.latitude, point.longitude)) return
        val category = when {
            tags["place"] != null -> tags["place"].orEmpty()
            tags["amenity"] != null -> tags["amenity"].orEmpty()
            tags["tourism"] != null -> tags["tourism"].orEmpty()
            tags["railway"] != null -> tags["railway"].orEmpty()
            else -> "Place"
        }
        places.putIfAbsent("$name|${point.latitude}|${point.longitude}", OfflinePlace(name, point.latitude, point.longitude, category))
    }

    private fun addNamedWayPlace(
        points: List<OfflineMapPoint>,
        tags: Map<String, String>,
        places: MutableMap<String, OfflinePlace>
    ) {
        val name = tags["name"]?.trim().orEmpty()
        if (name.isBlank()) return
        val center = points[points.size / 2]
        val category = when {
            tags["highway"] != null -> "Road"
            tags["waterway"] != null -> "Waterway"
            tags["railway"] != null -> "Railway"
            else -> "Feature"
        }
        places.putIfAbsent("$name|${center.latitude}|${center.longitude}", OfflinePlace(name, center.latitude, center.longitude, category))
    }

    private fun classifyWay(
        points: List<OfflineMapPoint>,
        tags: Map<String, String>
    ): OfflineMapFeature? = when {
        tags["highway"] != null -> OfflineMapFeature(
            points = points,
            kind = OfflineMapFeatureKind.ROAD,
            name = tags["name"],
            minZoom = roadMinZoom(tags["highway"])
        )
        tags["waterway"] != null -> OfflineMapFeature(
            points = points,
            kind = OfflineMapFeatureKind.WATER,
            name = tags["name"],
            minZoom = 1f
        )
        tags["natural"] == "water" || tags["landuse"] == "reservoir" -> {
            OfflineMapFeature(
                points = points,
                kind = OfflineMapFeatureKind.WATER,
                name = tags["name"],
                minZoom = 1f
            )
        }
        tags["railway"] != null -> OfflineMapFeature(
            points = points,
            kind = OfflineMapFeatureKind.RAIL,
            name = tags["name"],
            minZoom = 1.2f
        )
        else -> null
    }

    private fun roadMinZoom(highway: String?): Float = when (highway) {
        "motorway", "trunk", "primary" -> 1f
        "secondary" -> 1.1f
        "tertiary", "unclassified" -> 1.4f
        "residential", "living_street" -> 1.8f
        else -> 2.5f
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}
