package com.resqmesh.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.resqmesh.app.data.EmergencyLocation
import com.resqmesh.app.data.EmergencyLocationSource
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencyReportStatus
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
import com.resqmesh.app.data.EmergencyReportValidation
import com.resqmesh.app.data.LocalEmergencyRepository
import com.resqmesh.app.bluetooth.BleMessagingManager
import com.resqmesh.app.bluetooth.BleConnectionStatus
import com.resqmesh.app.bluetooth.EmergencyTransmissionState
import com.resqmesh.app.location.DeviceLocationReading
import com.resqmesh.app.location.LocationEngineState
import com.resqmesh.app.location.LocationPermissions
import com.resqmesh.app.location.LocationStatus
import com.resqmesh.app.location.OfflineLocationEngine
import com.resqmesh.app.ui.theme.EmergencyAmber
import com.resqmesh.app.ui.theme.EmergencySurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun EmergencyReportingSection(
    repository: LocalEmergencyRepository,
    locationEngine: OfflineLocationEngine,
    messagingManager: BleMessagingManager,
    reportsSnapshot: List<EmergencyReport> = repository.list(),
    mapLocation: EmergencyLocation? = null,
    onChooseOnMap: () -> Unit = {},
    onClearMapSelection: () -> Unit = {},
    onReportsChanged: (List<EmergencyReport>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var locationState by remember { mutableStateOf(locationEngine.state) }
    var reports by remember(reportsSnapshot) { mutableStateOf(reportsSnapshot) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var selectedReport by remember { mutableStateOf<EmergencyReport?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var messagingState by remember { mutableStateOf(messagingManager.state) }

    DisposableEffect(locationEngine) {
        val removeListener = locationEngine.addListener { updatedState -> locationState = updatedState }
        onDispose(removeListener)
    }

    DisposableEffect(messagingManager) {
        val removeListener = messagingManager.addListener { messagingState = it }
        onDispose(removeListener)
    }

    LaunchedEffect(messagingState.emergencyTransmissions) {
        messagingState.emergencyTransmissions.forEach { (emergencyId, transmissionState) ->
            val status = when (transmissionState) {
                EmergencyTransmissionState.TRANSPORT_ACKNOWLEDGED -> EmergencyReportStatus.TRANSPORT_ACKNOWLEDGED
                EmergencyTransmissionState.FAILED,
                EmergencyTransmissionState.DISCONNECTED -> EmergencyReportStatus.TRANSMISSION_FAILED
                else -> null
            }
            if (status != null) repository.updateStatus(emergencyId, status)
        }
        reports = repository.list()
        onReportsChanged(reports)
    }

    LaunchedEffect(locationState.reading?.timestampMillis, locationState.isRefreshing) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (LocationPermissions.hasAny(context)) {
            locationEngine.refresh()
        } else {
            locationState = locationState.copy(
                status = LocationStatus.PERMISSION_REQUIRED,
                message = "Location permission was denied. Reports can still be saved without a location or with manual coordinates."
            )
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = EmergencySurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Emergency reports",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Saved on this device only. Reports are not transmitted or broadcast in this batch.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(14.dp))
            LocationStatusCard(
                state = locationState,
                nowMillis = nowMillis,
                onRefresh = {
                    if (LocationPermissions.hasAny(context)) {
                        locationEngine.refresh()
                    } else {
                        locationPermissionLauncher.launch(LocationPermissions.requiredPermissions())
                    }
                },
                onClear = locationEngine::clear
            )

            Button(
                onClick = {
                    onClearMapSelection()
                    showCreateDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmergencyAmber,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 13.dp)
            ) {
                Text(text = "Create emergency", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (reports.isEmpty()) {
                Text(
                    text = "No locally saved emergencies yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reports.forEach { report ->
                        ReportSummaryCard(
                            report = report,
                            transmissionState = messagingState.emergencyTransmissions[report.id],
                            canSend = messagingState.connectedPeerId != null &&
                                messagingState.connectionStatus == BleConnectionStatus.CONNECTED,
                            onClick = { selectedReport = report },
                            onSend = {
                                val sent = messagingManager.sendEmergency(report)
                                if (!sent && messagingState.connectedPeerId == null) {
                                    selectedReport = report
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateEmergencyDialog(
            locationState = locationState,
            mapLocation = mapLocation,
            onChooseOnMap = onChooseOnMap,
            onDismiss = { showCreateDialog = false },
            onSave = { report ->
                repository.save(report)
                reports = repository.list()
                onReportsChanged(reports)
                showCreateDialog = false
            }
        )
    }


    selectedReport?.let { report ->
        ReportDetailsDialog(
            report = report,
            onDismiss = { selectedReport = null }
        )
    }
}

@Composable
private fun LocationStatusCard(
    state: LocationEngineState,
    nowMillis: Long,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Offline location",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = locationStatusLabel(state.status),
                    color = locationStatusColor(state.status),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            val reading = state.reading
            if (reading == null) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    text = "${formatCoordinate(reading.latitude)}, ${formatCoordinate(reading.longitude)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Accuracy: ${reading.accuracyMeters?.let { "%.1f m".format(it) } ?: "not reported"} · Age: ${formatAge(nowMillis - reading.timestampMillis)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Source: ${reading.source.label}${if (!reading.precisePermission) " · approximate permission" else ""}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRefresh,
                    enabled = !state.isRefreshing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmergencyAmber,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(text = if (state.isRefreshing) "Getting fix..." else "Refresh location")
                }
                OutlinedButton(
                    onClick = onClear,
                    enabled = state.reading != null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(text = "Clear")
                }
            }
        }
    }
}

@Composable
private fun ReportSummaryCard(
    report: EmergencyReport,
    transmissionState: EmergencyTransmissionState?,
    canSend: Boolean,
    onClick: () -> Unit,
    onSend: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = report.type.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = report.severity.label,
                    color = EmergencyAmber,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = report.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${report.status.label} · ${formatTimestamp(report.createdAtMillis)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            report.receivedFromPeerId?.let {
                Text(
                    text = "Received from nearby peer",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (report.receivedFromPeerId == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) {
                        Text("Details")
                    }
                    Button(
                        onClick = onSend,
                        enabled = canSend && transmissionState != EmergencyTransmissionState.SENDING,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmergencyAmber,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            when (transmissionState) {
                                EmergencyTransmissionState.SENDING -> "Sending…"
                                EmergencyTransmissionState.TRANSPORT_ACKNOWLEDGED -> "Acknowledged"
                                EmergencyTransmissionState.FAILED,
                                EmergencyTransmissionState.DISCONNECTED -> "Retry"
                                else -> "Send"
                            }
                        )
                    }
                }
                transmissionState?.let { state ->
                    Text(
                        text = when (state) {
                            EmergencyTransmissionState.READY_TO_SEND -> "Ready to send when a peer is connected."
                            EmergencyTransmissionState.SENDING -> "Sending over the active BLE connection…"
                            EmergencyTransmissionState.TRANSPORT_ACKNOWLEDGED -> "Transport acknowledged. This does not mean a responder accepted it."
                            EmergencyTransmissionState.FAILED -> "Transmission failed. The report remains saved locally."
                            EmergencyTransmissionState.DISCONNECTED -> "Connection lost. Retry when a peer reconnects."
                        },
                        color = if (state == EmergencyTransmissionState.TRANSPORT_ACKNOWLEDGED) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

private enum class LocationChoice {
    CURRENT,
    MAP,
    MANUAL,
    NONE
}

@Composable
private fun CreateEmergencyDialog(
    locationState: LocationEngineState,
    mapLocation: EmergencyLocation?,
    onChooseOnMap: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (EmergencyReport) -> Unit
) {
    var type by rememberSaveable { mutableStateOf(EmergencyType.MEDICAL) }
    var severity by rememberSaveable { mutableStateOf(EmergencySeverity.HIGH) }
    var description by rememberSaveable { mutableStateOf("") }
    var locationDescription by rememberSaveable { mutableStateOf("") }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var locationChoice by rememberSaveable {
        mutableStateOf(
            if (locationState.reading != null) LocationChoice.CURRENT
            else LocationChoice.NONE
        )
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(mapLocation) {
        if (mapLocation != null) locationChoice = LocationChoice.MAP
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(text = "Create emergency") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Emergency type", fontWeight = FontWeight.Bold)
                EmergencyType.entries.forEach { candidate ->
                    FilterChip(
                        selected = type == candidate,
                        onClick = { type = candidate },
                        label = { Text(candidate.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Severity", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EmergencySeverity.entries.forEach { candidate ->
                        FilterChip(
                            selected = severity == candidate,
                            onClick = { severity = candidate },
                            label = { Text(candidate.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Short description") },
                    placeholder = { Text("What help is needed?") },
                    minLines = 3,
                    maxLines = 5,
                    isError = validationError != null
                )
                Text(text = "Location (optional)", fontWeight = FontWeight.Bold)
                FilterChip(
                    selected = locationChoice == LocationChoice.CURRENT,
                    onClick = {
                        if (locationState.reading != null) locationChoice = LocationChoice.CURRENT
                    },
                    enabled = locationState.reading != null,
                    label = {
                        Text(
                            if (locationState.reading == null) {
                                "Current device location unavailable"
                            } else {
                                val reading = locationState.reading!!
                                "Use current device location\n" +
                                        "Lat: ${formatCoordinate(reading.latitude)}, " +
                                        "Lon: ${formatCoordinate(reading.longitude)}"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = locationChoice == LocationChoice.MAP,
                    onClick = onChooseOnMap,
                    label = {
                        Text(
                            if (mapLocation == null) "Choose on offline Nagpur map"
                            else "Map pin: ${formatCoordinate(mapLocation.latitude)}, ${formatCoordinate(mapLocation.longitude)}"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = locationChoice == LocationChoice.MANUAL,
                    onClick = { locationChoice = LocationChoice.MANUAL },
                    label = { Text("Advanced: enter coordinates (unverified)") },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = locationChoice == LocationChoice.NONE,
                    onClick = { locationChoice = LocationChoice.NONE },
                    label = { Text("Save without a location") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (locationChoice == LocationChoice.MANUAL) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = latitude,
                            onValueChange = { latitude = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Latitude") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = longitude,
                            onValueChange = { longitude = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Longitude") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                    Text(
                        text = "Manual coordinates are stored as unverified and are not treated as device location.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (locationChoice == LocationChoice.MAP && mapLocation != null) {
                    Text(
                        text = "Map-selected coordinates are stored as manually selected and unverified.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = locationDescription,
                    onValueChange = { locationDescription = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Optional landmark / location description") },
                    placeholder = { Text("Near the main entrance, behind the hostel building.") },
                    minLines = 2,
                    maxLines = 4
                )
                validationError?.let { error ->
                    Text(text = error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val reportLocation = when (locationChoice) {
                        LocationChoice.CURRENT -> locationState.reading?.toEmergencyLocation()
                        LocationChoice.MAP -> mapLocation
                        LocationChoice.MANUAL -> EmergencyReportValidation.parseManualLocation(
                            latitudeText = latitude,
                            longitudeText = longitude,
                            timestampMillis = System.currentTimeMillis()
                        )
                        LocationChoice.NONE -> null
                    }
                    validationError = when {
                        !EmergencyReportValidation.isDescriptionValid(description) -> "Enter a short description."
                        locationChoice == LocationChoice.CURRENT && reportLocation == null -> {
                            "The current device location is no longer available. Refresh or choose another option."
                        }
                        locationChoice == LocationChoice.MAP && reportLocation == null -> {
                            "Choose a point on the offline map before saving."
                        }
                        locationChoice == LocationChoice.MANUAL && reportLocation == null -> {
                            "Enter valid latitude (-90 to 90) and longitude (-180 to 180)."
                        }
                        else -> null
                    }
                    if (validationError == null) {
                        onSave(
                            EmergencyReport(
                                type = type,
                                severity = severity,
                                description = description.trim(),
                                createdAtMillis = System.currentTimeMillis(),
                                status = EmergencyReportStatus.LOCAL_ONLY,
                                location = reportLocation,
                                locationDescription = locationDescription.trim().takeIf { it.isNotBlank() }
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmergencyAmber,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(text = "Save locally", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel") }
        }
    )
}

@Composable
private fun ReportDetailsDialog(
    report: EmergencyReport,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = report.type.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Severity: ${report.severity.label}")
                Text(text = "Status: ${report.status.label}")
                Text(text = "Created: ${formatTimestamp(report.createdAtMillis)}")
                Text(text = report.description)
                report.locationDescription?.let {
                    Text(text = "Landmark / location description: $it")
                }
                val location = report.location
                if (location == null) {
                    Text(text = "Location: not provided")
                } else {
                    Text(
                        text = "Location: ${formatCoordinate(location.latitude)}, ${formatCoordinate(location.longitude)}"
                    )
                    Text(
                        text = "Location source: ${location.source.label}"
                    )
                    Text(
                        text = "Accuracy: ${location.accuracyMeters?.let { "%.1f m".format(it) } ?: "not reported"}"
                    )
                    Text(text = "Location reading: ${formatTimestamp(location.timestampMillis)}")
                    Text(text = "Physical location is not independently verified.")
                }
                Text(
                    text = "Emergency ID: ${report.id}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = "Close") }
        }
    )
}

private fun DeviceLocationReading.toEmergencyLocation(): EmergencyLocation = EmergencyLocation(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    timestampMillis = timestampMillis,
    source = EmergencyLocationSource.GNSS,
    isVerified = false
)

private fun locationStatusLabel(status: LocationStatus): String = when (status) {
    LocationStatus.PERMISSION_REQUIRED -> "Permission needed"
    LocationStatus.ACQUIRING -> "Acquiring"
    LocationStatus.FRESH -> "Fresh"
    LocationStatus.STALE -> "Stale/cached"
    LocationStatus.LOW_ACCURACY -> "Low accuracy"
    LocationStatus.UNAVAILABLE -> "Unavailable"
}

@Composable
private fun locationStatusColor(status: LocationStatus): Color = when (status) {
    LocationStatus.FRESH -> MaterialTheme.colorScheme.primary
    LocationStatus.ACQUIRING -> EmergencyAmber
    LocationStatus.STALE, LocationStatus.LOW_ACCURACY -> EmergencyAmber
    LocationStatus.PERMISSION_REQUIRED, LocationStatus.UNAVAILABLE -> MaterialTheme.colorScheme.error
}

private fun formatAge(ageMillis: Long): String {
    val seconds = ageMillis.coerceAtLeast(0L) / 1_000L
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3_600 -> "${seconds / 60}m"
        else -> "${seconds / 3_600}h"
    }
}

private fun formatCoordinate(value: Double): String = "%.6f".format(Locale.US, value)

private fun formatTimestamp(timestampMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))
