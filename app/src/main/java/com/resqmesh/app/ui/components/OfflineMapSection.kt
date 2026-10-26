package com.resqmesh.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencyLocation
import com.resqmesh.app.location.DeviceLocationReading
import com.resqmesh.app.location.LocationEngineState
import com.resqmesh.app.maps.NagpurMapData
import com.resqmesh.app.maps.OfflineMapCanvas
import com.resqmesh.app.maps.OfflineMapCamera
import com.resqmesh.app.maps.OfflineMapDataset
import com.resqmesh.app.maps.OfflinePlaceSearch
import com.resqmesh.app.ui.theme.EmergencyAmber
import androidx.compose.ui.res.stringResource
import com.resqmesh.app.R
import java.util.Locale

@Composable
fun OfflineNagpurMapCard(
    dataset: OfflineMapDataset?,
    datasetDescription: String,
    camera: OfflineMapCamera,
    onCameraChange: (OfflineMapCamera) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    locationState: LocationEngineState,
    reports: List<EmergencyReport>,
    onCenterOnLocation: () -> Unit,
    onOpenPicker: () -> Unit,
    onReportClick: (EmergencyReport) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchResults = OfflinePlaceSearch.search(dataset?.places.orEmpty(), searchQuery)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                         text = stringResource(R.string.offline_nagpur_map),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = datasetDescription,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                     text = stringResource(R.string.offline),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                 label = { Text(stringResource(R.string.search_local_places)) },
                 placeholder = { Text(stringResource(R.string.road_neighborhood_landmark)) }
            )
            if (searchQuery.isNotBlank()) {
                if (searchResults.isEmpty()) {
                    Text(
                         text = stringResource(R.string.no_matching_place),
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
                        searchResults.take(5).forEach { place ->
                            TextButton(
                                onClick = {
                                    onCameraChange(
                                        camera.copy(
                                            latitude = place.latitude,
                                            longitude = place.longitude,
                                            zoom = 3f
                                        )
                                    )
                                    onSearchQueryChange(place.name)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(text = place.name, color = MaterialTheme.colorScheme.onSurface)
                                    Text(text = place.category, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp),
                contentAlignment = Alignment.Center
            ) {
                OfflineMapCanvas(
                    dataset = dataset,
                    camera = camera,
                    onCameraChange = onCameraChange,
                    selectedLatitude = null,
                    selectedLongitude = null,
                    currentLatitude = locationState.reading?.latitude,
                    currentLongitude = locationState.reading?.longitude,
                    reports = reports,
                    onReportClick = onReportClick,
                    modifier = Modifier.fillMaxWidth().height(330.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                 text = stringResource(R.string.map_coverage, NagpurMapData.bounds.minLatitude, NagpurMapData.bounds.maxLatitude, NagpurMapData.bounds.minLongitude, NagpurMapData.bounds.maxLongitude),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            locationState.reading?.let { reading ->
                if (!NagpurMapData.isCovered(reading.latitude, reading.longitude)) {
                    Text(
                         text = stringResource(R.string.outside_map_coverage),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                 text = stringResource(R.string.map_legend),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = "${NagpurMapData.ATTRIBUTION}. Map data is bundled locally; no online tiles are requested.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onCenterOnLocation, modifier = Modifier.weight(1f)) {
                     Text(stringResource(R.string.center_my_location))
                }
                Button(
                    onClick = onOpenPicker,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyAmber, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                     Text(stringResource(R.string.choose_on_map), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun OfflineMapPickerDialog(
    dataset: OfflineMapDataset?,
    initialCamera: OfflineMapCamera,
    locationReading: DeviceLocationReading?,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    var camera by remember { mutableStateOf(initialCamera) }
    var searchQuery by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val searchResults = OfflinePlaceSearch.search(dataset?.places.orEmpty(), searchQuery)

    AlertDialog(
        onDismissRequest = onDismiss,
         title = { Text(stringResource(R.string.choose_emergency_location)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                     text = stringResource(R.string.map_picker_description),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                     label = { Text(stringResource(R.string.search_offline_places)) }
                )
                searchResults.take(4).forEach { place ->
                    TextButton(
                        onClick = {
                            camera = camera.copy(latitude = place.latitude, longitude = place.longitude, zoom = 3f)
                            searchQuery = place.name
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${place.name} · ${place.category}", modifier = Modifier.fillMaxWidth())
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OfflineMapCanvas(
                        dataset = dataset,
                        camera = camera,
                        onCameraChange = { camera = it },
                        selectedLatitude = camera.latitude,
                        selectedLongitude = camera.longitude,
                        currentLatitude = locationReading?.latitude,
                        currentLongitude = locationReading?.longitude,
                        reports = emptyList(),
                        onReportClick = {},
                        modifier = Modifier.fillMaxWidth().height(300.dp)
                    )
                    Text(text = "+", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.headlineMedium)
                }
                Text(
                     text = stringResource(R.string.selected_coordinates, "%.6f".format(Locale.US, camera.latitude), "%.6f".format(Locale.US, camera.longitude)),
                    style = MaterialTheme.typography.bodySmall
                )
                error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (dataset?.features?.isNotEmpty() != true) {
                         error = context.getString(R.string.map_data_loading)
                    } else if (!NagpurMapData.isCovered(camera.latitude, camera.longitude)) {
                         error = context.getString(R.string.outside_offline_coverage)
                    } else {
                        onConfirm(camera.latitude, camera.longitude)
                    }
                },
                enabled = dataset?.features?.isNotEmpty() == true
            ) {
                 Text(stringResource(R.string.confirm_location))
            }
        },
         dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun OfflineMapScreen(
    dataset: OfflineMapDataset?,
    datasetDescription: String,
    locationState: LocationEngineState,
    reports: List<EmergencyReport>,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onReportClick: (EmergencyReport) -> Unit
) {
    var latitude by rememberSaveable { mutableStateOf(NagpurMapData.centerLatitude) }
    var longitude by rememberSaveable { mutableStateOf(NagpurMapData.centerLongitude) }
    var zoom by rememberSaveable { mutableStateOf(1f) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val camera = OfflineMapCamera(latitude, longitude, zoom)
    val searchResults = OfflinePlaceSearch.search(dataset?.places.orEmpty(), searchQuery)
    var searchedLatitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var searchedLongitude by rememberSaveable { mutableStateOf<Double?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                 Text(stringResource(R.string.offline_map), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(datasetDescription, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
             Text(stringResource(R.string.offline), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
             label = { Text(stringResource(R.string.search_nagpur_places)) },
             placeholder = { Text(stringResource(R.string.road_neighborhood_landmark)) }
        )
        if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                    searchResults.take(4).forEach { place ->
                        TextButton(
                            onClick = {
                                latitude = place.latitude
                                longitude = place.longitude
                                zoom = 5f
                                searchedLatitude = place.latitude
                                searchedLongitude = place.longitude
                                searchQuery = place.name
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(place.name, modifier = Modifier.fillMaxWidth())
                                Text(place.category, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        } else if (searchQuery.isNotBlank()) {
            Text(
                "No matching place in the local dataset.",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            OfflineMapCanvas(
                dataset = dataset,
                camera = camera,
                onCameraChange = {
                    latitude = it.latitude
                    longitude = it.longitude
                    zoom = it.zoom
                },
                selectedLatitude = searchedLatitude,
                selectedLongitude = searchedLongitude,
                currentLatitude = locationState.reading?.latitude,
                currentLongitude = locationState.reading?.longitude,
                reports = reports,
                onReportClick = onReportClick,
                maxZoom = 20f,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { zoom = (zoom * 1.6f).coerceAtMost(20f) },
                    modifier = Modifier.height(42.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) { Text("+") }
                Button(
                    onClick = { zoom = (zoom / 1.6f).coerceAtLeast(1f) },
                    modifier = Modifier.height(42.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) { Text("−") }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    locationState.reading?.let {
                        latitude = it.latitude
                        longitude = it.longitude
                        zoom = 5f
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Center on location") }
            Text(
                text = "Zoom ${"%.1f".format(Locale.US, zoom)}×",
                modifier = Modifier.align(Alignment.CenterVertically),
                style = MaterialTheme.typography.labelMedium
            )
        }
        Text(
            text = "${NagpurMapData.ATTRIBUTION} · Coverage ${NagpurMapData.bounds.minLatitude}–${NagpurMapData.bounds.maxLatitude}°N",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun OfflineLocationPickerScreen(
    dataset: OfflineMapDataset?,
    locationState: LocationEngineState,
    initialLocation: EmergencyLocation?,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onBack: () -> Unit,
    onConfirm: (EmergencyLocation) -> Unit
) {
    var latitude by rememberSaveable { mutableStateOf(initialLocation?.latitude ?: NagpurMapData.centerLatitude) }
    var longitude by rememberSaveable { mutableStateOf(initialLocation?.longitude ?: NagpurMapData.centerLongitude) }
    var zoom by rememberSaveable { mutableStateOf(5f) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val camera = OfflineMapCamera(latitude, longitude, zoom)
    val searchResults = OfflinePlaceSearch.search(dataset?.places.orEmpty(), searchQuery)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Select emergency location", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text(
            "Move the center pin. The selected coordinate is manually selected and unverified.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search local place names") }
        )
        searchResults.take(3).forEach { place ->
            TextButton(
                onClick = {
                    latitude = place.latitude
                    longitude = place.longitude
                    zoom = 6f
                    searchQuery = place.name
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("${place.name} · ${place.category}", modifier = Modifier.fillMaxWidth()) }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            OfflineMapCanvas(
                dataset = dataset,
                camera = camera,
                onCameraChange = {
                    latitude = it.latitude
                    longitude = it.longitude
                    zoom = it.zoom
                },
                selectedLatitude = latitude,
                selectedLongitude = longitude,
                currentLatitude = locationState.reading?.latitude,
                currentLongitude = locationState.reading?.longitude,
                reports = emptyList(),
                onReportClick = {},
                maxZoom = 20f,
                modifier = Modifier.fillMaxSize()
            )
            Text("+", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.headlineMedium)
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(onClick = { zoom = (zoom * 1.6f).coerceAtMost(20f) }) { Text("+") }
                Button(onClick = { zoom = (zoom / 1.6f).coerceAtLeast(1f) }) { Text("−") }
            }
        }
        Text(
            "Selected: ${"%.6f".format(Locale.US, latitude)}, ${"%.6f".format(Locale.US, longitude)}",
            style = MaterialTheme.typography.bodySmall
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = {
                if (!NagpurMapData.isCovered(latitude, longitude)) {
                    error = "The selected point is outside bundled offline coverage."
                } else {
                    onConfirm(NagpurMapData.selectedLocation(latitude, longitude, System.currentTimeMillis()))
                }
            },
            enabled = dataset?.features?.isNotEmpty() == true,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Confirm location", fontWeight = FontWeight.Bold) }
    }
}
