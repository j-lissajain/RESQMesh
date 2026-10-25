package com.resqmesh.app.maps

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.resqmesh.app.data.EmergencyMapMarkerFilter
import com.resqmesh.app.data.EmergencyReport
import kotlin.math.max
import kotlin.math.min

data class OfflineMapCamera(
    val latitude: Double,
    val longitude: Double,
    val zoom: Float
)

@Composable
fun OfflineMapCanvas(
    dataset: OfflineMapDataset?,
    camera: OfflineMapCamera,
    onCameraChange: (OfflineMapCamera) -> Unit,
    selectedLatitude: Double?,
    selectedLongitude: Double?,
    currentLatitude: Double?,
    currentLongitude: Double?,
    reports: List<EmergencyReport>,
    onReportClick: (EmergencyReport) -> Unit,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var interactiveCamera by remember { mutableStateOf(camera) }
    var mapGestureActive by remember { mutableStateOf(false) }
    val bounds = dataset?.bounds ?: NagpurMapData.bounds
    val markerReports = remember(reports) { EmergencyMapMarkerFilter.validMarkers(reports) }
    val latestOnCameraChange by rememberUpdatedState(onCameraChange)
    val latestOnReportClick by rememberUpdatedState(onReportClick)

    LaunchedEffect(camera) {
        if (!mapGestureActive) interactiveCamera = camera
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(bounds, size, markerReports) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    down.consume()
                    val tapPosition = down.position
                    var moved = false
                    mapGestureActive = true
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val hasPressedPointer = event.changes.any { it.pressed }
                            val pan = event.calculatePan()
                            val zoomChange = event.calculateZoom()
                            if (size.width > 0 && size.height > 0 &&
                                (pan != Offset.Zero || zoomChange != 1f)
                            ) {
                                moved = true
                                val nextZoom = (interactiveCamera.zoom * zoomChange).coerceIn(1f, 8f)
                                val pixelsPerLongitude = size.width /
                                    (bounds.maxLongitude - bounds.minLongitude) * nextZoom
                                val pixelsPerLatitude = size.height /
                                    (bounds.maxLatitude - bounds.minLatitude) * nextZoom
                                interactiveCamera = interactiveCamera.copy(
                                    latitude = (interactiveCamera.latitude + pan.y / pixelsPerLatitude)
                                        .coerceIn(bounds.minLatitude, bounds.maxLatitude),
                                    longitude = (interactiveCamera.longitude - pan.x / pixelsPerLongitude)
                                        .coerceIn(bounds.minLongitude, bounds.maxLongitude),
                                    zoom = nextZoom
                                )
                            }
                            event.changes.forEach { it.consume() }
                            if (!hasPressedPointer) break
                        }

                        if (!moved) {
                            findMarkerAt(
                                point = tapPosition,
                                size = size,
                                camera = interactiveCamera,
                                bounds = bounds,
                                reports = markerReports
                            )?.let(latestOnReportClick)
                        }
                    } finally {
                        mapGestureActive = false
                        if (moved) latestOnCameraChange(interactiveCamera)
                    }
                }
            }
    ) {
        drawMap(
            canvas = drawContext.canvas.nativeCanvas,
            width = size.width,
            height = size.height,
            dataset = dataset,
            camera = interactiveCamera,
            selectedLatitude = selectedLatitude,
            selectedLongitude = selectedLongitude,
            currentLatitude = currentLatitude,
            currentLongitude = currentLongitude,
            reports = markerReports
        )
    }
}

private fun drawMap(
    canvas: Canvas,
    width: Int,
    height: Int,
    dataset: OfflineMapDataset?,
    camera: OfflineMapCamera,
    selectedLatitude: Double?,
    selectedLongitude: Double?,
    currentLatitude: Double?,
    currentLongitude: Double?,
    reports: List<EmergencyReport>
) {
    canvas.drawColor(Color.rgb(232, 239, 233))
    if (width <= 0 || height <= 0) return

    val bounds = dataset?.bounds ?: NagpurMapData.bounds
    val pixelsPerLongitude = width / (bounds.maxLongitude - bounds.minLongitude) * camera.zoom
    val pixelsPerLatitude = height / (bounds.maxLatitude - bounds.minLatitude) * camera.zoom
    val centerX = width / 2f
    val centerY = height / 2f
    val visibleBounds = MapBounds(
        minLatitude = camera.latitude - height / 2.0 / pixelsPerLatitude,
        minLongitude = camera.longitude - width / 2.0 / pixelsPerLongitude,
        maxLatitude = camera.latitude + height / 2.0 / pixelsPerLatitude,
        maxLongitude = camera.longitude + width / 2.0 / pixelsPerLongitude
    )

    fun screenX(longitude: Double): Float =
        centerX + ((longitude - camera.longitude) * pixelsPerLongitude).toFloat()

    fun screenY(latitude: Double): Float =
        centerY - ((latitude - camera.latitude) * pixelsPerLatitude).toFloat()

    val featurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    dataset?.features?.forEach { feature ->
        if (feature.minZoom > camera.zoom || !feature.bounds.intersects(visibleBounds)) return@forEach
        featurePaint.color = when (feature.kind) {
            OfflineMapFeatureKind.ROAD -> Color.rgb(167, 177, 170)
            OfflineMapFeatureKind.RAIL -> Color.rgb(120, 120, 120)
            OfflineMapFeatureKind.WATER -> Color.rgb(125, 188, 211)
        }
        featurePaint.strokeWidth = when (feature.kind) {
            OfflineMapFeatureKind.ROAD -> max(1f, 1.6f * camera.zoom)
            OfflineMapFeatureKind.RAIL -> max(1f, 1.2f * camera.zoom)
            OfflineMapFeatureKind.WATER -> max(2f, 2.8f * camera.zoom)
        }
        val path = Path()
        feature.points.forEachIndexed { index, point ->
            val x = screenX(point.longitude)
            val y = screenY(point.latitude)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, featurePaint)
    }

    if (camera.zoom >= 1.5f) {
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(55, 76, 66)
            textSize = max(11f, 11f * camera.zoom)
        }
        dataset?.places
            ?.asSequence()
            ?.filter { place -> visibleBounds.contains(place.latitude, place.longitude) }
            ?.take(250)
            ?.forEach { place ->
                canvas.drawText(
                    place.name,
                    screenX(place.longitude) + 4f,
                    screenY(place.latitude) - 4f,
                    labelPaint
                )
            }
    }

    val markerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val markerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    reports.forEach { report ->
        val location = report.location ?: return@forEach
        markerFillPaint.color = severityColor(report.severity.name)
        val x = screenX(location.longitude)
        val y = screenY(location.latitude)
        canvas.drawCircle(x, y, 10f, markerFillPaint)
        canvas.drawCircle(x, y, 10f, markerStrokePaint)
    }

    if (currentLatitude != null && currentLongitude != null) {
        val x = screenX(currentLongitude)
        val y = screenY(currentLatitude)
        markerFillPaint.color = Color.rgb(38, 112, 220)
        canvas.drawCircle(x, y, 8f, markerFillPaint)
        markerStrokePaint.color = Color.rgb(38, 112, 220)
        markerStrokePaint.alpha = 100
        markerStrokePaint.strokeWidth = 3f
        canvas.drawCircle(x, y, 15f, markerStrokePaint)
        markerStrokePaint.color = Color.WHITE
        markerStrokePaint.alpha = 255
        markerStrokePaint.strokeWidth = 2f
    }

    if (selectedLatitude != null && selectedLongitude != null) {
        val x = screenX(selectedLongitude)
        val y = screenY(selectedLatitude)
        markerFillPaint.color = Color.rgb(213, 63, 63)
        canvas.drawCircle(x, y, 12f, markerFillPaint)
        markerFillPaint.color = Color.rgb(213, 63, 63)
        markerFillPaint.strokeWidth = 4f
        canvas.drawLine(x, y - 24f, x, y - 4f, markerFillPaint)
    }

    if (dataset == null) {
        val loadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 16f
        }
        canvas.drawText("Loading local OpenStreetMap data…", 18f, 32f, loadingPaint)
    }
}

private fun findMarkerAt(
    point: Offset,
    size: IntSize,
    camera: OfflineMapCamera,
    bounds: MapBounds,
    reports: List<EmergencyReport>
): EmergencyReport? {
    val pixelsPerLongitude = size.width / (bounds.maxLongitude - bounds.minLongitude) * camera.zoom
    val pixelsPerLatitude = size.height / (bounds.maxLatitude - bounds.minLatitude) * camera.zoom
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    return reports
        .mapNotNull { report ->
            val location = report.location ?: return@mapNotNull null
            val x = centerX + ((location.longitude - camera.longitude) * pixelsPerLongitude).toFloat()
            val y = centerY - ((location.latitude - camera.latitude) * pixelsPerLatitude).toFloat()
            val dx = x - point.x
            val dy = y - point.y
            report to (dx * dx + dy * dy)
        }
        .minByOrNull { it.second }
        ?.takeIf { it.second <= 36f * 36f }
        ?.first
}

private fun MapBounds.intersects(other: MapBounds): Boolean =
    minLatitude <= other.maxLatitude && maxLatitude >= other.minLatitude &&
        minLongitude <= other.maxLongitude && maxLongitude >= other.minLongitude

private fun severityColor(value: String): Int = when (value) {
    "CRITICAL" -> Color.rgb(181, 35, 35)
    "HIGH" -> Color.rgb(223, 102, 36)
    "MEDIUM" -> Color.rgb(212, 165, 44)
    else -> Color.rgb(67, 137, 85)
}
