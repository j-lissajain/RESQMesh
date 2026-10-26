package com.resqmesh.app.ui.components

import android.Manifest
import android.content.Intent
import android.speech.RecognizerIntent
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
import androidx.compose.ui.res.stringResource
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
import com.resqmesh.app.data.EmergencyLifecycleStatus
import com.resqmesh.app.data.AssistanceResponse
import com.resqmesh.app.data.LanguagePreference
import com.resqmesh.app.data.localizedLabel
import com.resqmesh.app.data.withLifecycleStatus
import com.resqmesh.app.data.EmergencyReportValidation
import com.resqmesh.app.data.LocalEmergencyRepository
import com.resqmesh.app.bluetooth.BleMessagingManager
import com.resqmesh.app.bluetooth.BleConnectionStatus
import com.resqmesh.app.bluetooth.EmergencyRelayController
import com.resqmesh.app.bluetooth.EmergencyTransmissionState
import com.resqmesh.app.location.DeviceLocationReading
import com.resqmesh.app.location.LocationEngineState
import com.resqmesh.app.location.LocationPermissions
import com.resqmesh.app.location.LocationStatus
import com.resqmesh.app.location.OfflineLocationEngine
import com.resqmesh.app.ui.theme.EmergencyAmber
import com.resqmesh.app.ui.theme.EmergencySurface
import com.resqmesh.app.ui.localizedText
import com.resqmesh.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun EmergencyReportingSection(
    repository: LocalEmergencyRepository,
    locationEngine: OfflineLocationEngine,
    messagingManager: BleMessagingManager,
    relayController: EmergencyRelayController? = null,
    localNodeId: String? = null,
    reportsSnapshot: List<EmergencyReport> = repository.list(),
    mapLocation: EmergencyLocation? = null,
    onChooseOnMap: () -> Unit = {},
    onClearMapSelection: () -> Unit = {},
    onReportsChanged: (List<EmergencyReport>) -> Unit = {},
    onLifecycleChange: (EmergencyReport, EmergencyLifecycleStatus) -> Unit = { _, _ -> },
    onContentUpdate: (EmergencyReport, String) -> Unit = { _, _ -> },
    assistanceResponses: List<AssistanceResponse> = emptyList(),
    simplifiedEmergencyMode: Boolean = false,
    languagePreference: LanguagePreference = LanguagePreference.ENGLISH,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var locationState by remember { mutableStateOf(locationEngine.state) }
    var reports by remember(reportsSnapshot) { mutableStateOf(reportsSnapshot) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var selectedReport by remember { mutableStateOf<EmergencyReport?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var messagingState by remember { mutableStateOf(messagingManager.state) }
    var relayState by remember { mutableStateOf(relayController?.state) }

    DisposableEffect(locationEngine) {
        val removeListener = locationEngine.addListener { updatedState -> locationState = updatedState }
        onDispose(removeListener)
    }

    DisposableEffect(messagingManager) {
        val removeListener = messagingManager.addListener { messagingState = it }
        onDispose(removeListener)
    }

    DisposableEffect(relayController) {
        if (relayController == null) return@DisposableEffect onDispose { }
        val removeListener = relayController.addListener { relayState = it }
        onDispose(removeListener)
    }

    LaunchedEffect(messagingState.emergencyTransmissions) {
        if (relayController == null) {
            messagingState.emergencyTransmissions.forEach { (emergencyId, transmissionState) ->
                val status = when (transmissionState) {
                    EmergencyTransmissionState.TRANSPORT_ACKNOWLEDGED -> EmergencyReportStatus.TRANSPORT_ACKNOWLEDGED
                    EmergencyTransmissionState.FAILED,
                    EmergencyTransmissionState.DISCONNECTED -> EmergencyReportStatus.TRANSMISSION_FAILED
                    else -> null
                }
                if (status != null) repository.updateStatus(emergencyId, status)
            }
        }
        reports = repository.list()
        onReportsChanged(reports)
    }

    LaunchedEffect(relayState) {
        if (relayController != null) {
            reports = repository.list()
            onReportsChanged(reports)
        }
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
                 message = context.getString(R.string.location_permission_denied)
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
                 text = stringResource(R.string.emergency_reports),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                 text = stringResource(R.string.emergency_reports_description),
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
                Text(text = stringResource(R.string.create_emergency), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (reports.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_saved_emergencies),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reports.forEach { report ->
                        ReportSummaryCard(
                            report = report,
                            transmissionState = messagingState.emergencyTransmissions[report.id],
                            canSend = if (relayController != null) {
                                true
                            } else {
                                messagingState.connectedPeerId != null &&
                                    messagingState.connectionStatus == BleConnectionStatus.CONNECTED
                            },
                            onClick = { selectedReport = report },
                            onSend = {
                                val sent = relayController?.enqueue(report) ?: messagingManager.sendEmergency(report)
                                if (!sent && messagingState.connectedPeerId == null && relayController == null) {
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
                repository.save(report.copy(originId = localNodeId ?: report.originId))
                reports = repository.list()
                onReportsChanged(reports)
                showCreateDialog = false
            },
            simplifiedEmergencyMode = simplifiedEmergencyMode
            , languagePreference = languagePreference
        )
    }


    selectedReport?.let { report ->
        ReportDetailsDialog(
            report = report,
            onDismiss = { selectedReport = null },
            onLifecycleChange = { status ->
                onLifecycleChange(report, status)
                selectedReport = null
            },
            onContentUpdate = { updatedDescription ->
                onContentUpdate(report, updatedDescription)
                selectedReport = null
            },
            assistanceResponses = assistanceResponses.filter { it.emergencyId == report.id }
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
                 text = stringResource(R.string.offline_location),
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
                     text = stringResource(R.string.accuracy_age, reading.accuracyMeters?.let { "%.1f m".format(it) } ?: stringResource(R.string.not_reported), formatAge(nowMillis - reading.timestampMillis)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                     text = stringResource(R.string.location_source, reading.source.localizedText()) + if (!reading.precisePermission) " · ${stringResource(R.string.approximate_permission)}" else "",
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
                     Text(text = if (state.isRefreshing) stringResource(R.string.getting_fix) else stringResource(R.string.refresh_location))
                }
                OutlinedButton(
                    onClick = onClear,
                    enabled = state.reading != null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                     Text(text = stringResource(R.string.clear))
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
                     text = report.type.localizedText(),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                     text = report.severity.localizedText(),
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
                 text = "${report.status.localizedText()} · ${formatTimestamp(report.createdAtMillis)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            report.receivedFromPeerId?.let {
                Text(
                     text = stringResource(R.string.received_from_peer),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (report.receivedFromPeerId == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) {
                         Text(stringResource(R.string.details))
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
                                 EmergencyTransmissionState.SENDING -> stringResource(R.string.sending)
                                 EmergencyTransmissionState.TRANSPORT_ACKNOWLEDGED -> stringResource(R.string.acknowledged)
                                EmergencyTransmissionState.FAILED,
                                 EmergencyTransmissionState.DISCONNECTED -> stringResource(R.string.retry)
                                 else -> stringResource(R.string.send)
                            }
                        )
                    }
                }
                transmissionState?.let { state ->
                    Text(
                        text = when (state) {
                             EmergencyTransmissionState.READY_TO_SEND -> stringResource(R.string.ready_to_send)
                             EmergencyTransmissionState.SENDING -> stringResource(R.string.sending_ble)
                             EmergencyTransmissionState.TRANSPORT_ACKNOWLEDGED -> stringResource(R.string.transport_acknowledged_explanation)
                             EmergencyTransmissionState.FAILED -> stringResource(R.string.transmission_failed_local)
                             EmergencyTransmissionState.DISCONNECTED -> stringResource(R.string.connection_lost_retry)
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
    onSave: (EmergencyReport) -> Unit,
    simplifiedEmergencyMode: Boolean = false,
    languagePreference: LanguagePreference = LanguagePreference.ENGLISH
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
    val context = LocalContext.current
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.let { description = it }
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            speechLauncher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                     putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.speak_emergency_description))
                }
            )
        }
    }

    LaunchedEffect(mapLocation) {
        if (mapLocation != null) locationChoice = LocationChoice.MAP
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
         title = { Text(text = stringResource(R.string.create_emergency)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (simplifiedEmergencyMode) {
                     Text(stringResource(R.string.simplified_emergency_note), style = MaterialTheme.typography.bodySmall)
                    type = EmergencyType.OTHER
                } else {
                     Text(text = stringResource(R.string.emergency_type), fontWeight = FontWeight.Bold)
                    EmergencyType.entries.forEach { candidate ->
                        FilterChip(
                            selected = type == candidate,
                            onClick = { type = candidate },
                             label = { Text(candidate.localizedText()) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                     Text(text = stringResource(R.string.severity), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EmergencySeverity.entries.forEach { candidate ->
                            FilterChip(
                                selected = severity == candidate,
                                onClick = { severity = candidate },
                                 label = { Text(candidate.localizedText()) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                     label = { Text(stringResource(R.string.short_description)) },
                     placeholder = { Text(stringResource(R.string.help_needed_hint)) },
                    minLines = 3,
                    maxLines = 5,
                    isError = validationError != null
                )
                OutlinedButton(
                    onClick = {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            speechLauncher.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak the emergency description")
                                }
                            )
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.use_voice_input_once)) }
                 Text(stringResource(R.string.voice_input_note), style = MaterialTheme.typography.bodySmall)
                 Text(text = stringResource(R.string.location_optional), fontWeight = FontWeight.Bold)
                FilterChip(
                    selected = locationChoice == LocationChoice.CURRENT,
                    onClick = {
                        if (locationState.reading != null) locationChoice = LocationChoice.CURRENT
                    },
                    enabled = locationState.reading != null,
                    label = {
                        Text(
                            if (locationState.reading == null) {
                                 stringResource(R.string.current_location_unavailable)
                            } else {
                                val reading = locationState.reading!!
                                 stringResource(R.string.use_current_location, formatCoordinate(reading.latitude), formatCoordinate(reading.longitude))
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
                             if (mapLocation == null) stringResource(R.string.choose_offline_map)
                             else stringResource(R.string.map_pin, formatCoordinate(mapLocation.latitude), formatCoordinate(mapLocation.longitude))
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = locationChoice == LocationChoice.MANUAL,
                    onClick = { locationChoice = LocationChoice.MANUAL },
                     label = { Text(stringResource(R.string.manual_coordinates)) },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = locationChoice == LocationChoice.NONE,
                    onClick = { locationChoice = LocationChoice.NONE },
                     label = { Text(stringResource(R.string.save_without_location)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (locationChoice == LocationChoice.MANUAL) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = latitude,
                            onValueChange = { latitude = it },
                            modifier = Modifier.weight(1f),
                             label = { Text(stringResource(R.string.latitude)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = longitude,
                            onValueChange = { longitude = it },
                            modifier = Modifier.weight(1f),
                             label = { Text(stringResource(R.string.longitude)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                    Text(
                         text = stringResource(R.string.manual_coordinates_note),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (locationChoice == LocationChoice.MAP && mapLocation != null) {
                    Text(
                         text = stringResource(R.string.map_coordinates_note),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = locationDescription,
                    onValueChange = { locationDescription = it },
                    modifier = Modifier.fillMaxWidth(),
                     label = { Text(stringResource(R.string.landmark_optional)) },
                     placeholder = { Text(stringResource(R.string.landmark_hint)) },
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
                        !EmergencyReportValidation.isDescriptionValid(description) -> context.getString(R.string.validation_short_description)
                        locationChoice == LocationChoice.CURRENT && reportLocation == null -> {
                            context.getString(R.string.validation_current_location)
                        }
                        locationChoice == LocationChoice.MAP && reportLocation == null -> {
                            context.getString(R.string.validation_map_location)
                        }
                        locationChoice == LocationChoice.MANUAL && reportLocation == null -> {
                            context.getString(R.string.validation_coordinates)
                        }
                        !EmergencyReportValidation.isLocationDescriptionValid(locationDescription) -> {
                            context.getString(R.string.validation_landmark)
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
                Text(text = stringResource(R.string.save_locally), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun ReportDetailsDialog(
    report: EmergencyReport,
    onDismiss: () -> Unit,
    onLifecycleChange: (EmergencyLifecycleStatus) -> Unit,
    onContentUpdate: (String) -> Unit,
    assistanceResponses: List<AssistanceResponse>
) {
    var description by remember(report.id, report.contentRevision) { mutableStateOf(report.description) }
    AlertDialog(
        onDismissRequest = onDismiss,
         title = { Text(text = report.type.localizedText()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                 Text(text = stringResource(R.string.detail_severity, report.severity.localizedText()))
                 Text(text = stringResource(R.string.detail_status, report.status.localizedText()))
                 Text(text = stringResource(R.string.detail_lifecycle, report.lifecycleStatus.localizedText()))
                 Text(text = stringResource(R.string.assistance_offers, assistanceResponses.size), style = MaterialTheme.typography.titleSmall)
                 assistanceResponses.take(5).forEach { response ->
                     Text("${response.responderAlias}: ${response.assistanceType.localizedText()} · ${response.status.localizedText()}", style = MaterialTheme.typography.bodySmall)
                 }
                 Text(text = stringResource(R.string.content_revision, report.contentRevision))
                 Text(text = stringResource(R.string.relay_envelope, report.hopCount, report.remainingTtl))
                 Text(
                     text = if (report.contentTrust == com.resqmesh.app.data.EmergencyContentTrust.REMOTE_UNVERIFIED) {
                         stringResource(R.string.content_origin_unverified)
                     } else {
                         stringResource(R.string.content_origin_local)
                     }
                 )
                 Text(text = stringResource(R.string.created_at, formatTimestamp(report.createdAtMillis)))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                     label = { Text(stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5
                )
                report.locationDescription?.let {
                     Text(text = stringResource(R.string.landmark_value, it))
                }
                val location = report.location
                if (location == null) {
                     Text(text = stringResource(R.string.location_not_provided))
                } else {
                    Text(
                         text = stringResource(R.string.location_value, formatCoordinate(location.latitude), formatCoordinate(location.longitude))
                    )
                    Text(
                         text = stringResource(R.string.location_source, location.source.localizedText())
                    )
                    Text(
                         text = stringResource(R.string.accuracy_value, location.accuracyMeters?.let { "%.1f m".format(it) } ?: stringResource(R.string.not_reported))
                    )
                     Text(text = stringResource(R.string.location_reading, formatTimestamp(location.timestampMillis)))
                     Text(text = stringResource(R.string.location_not_verified))
                }
                Text(
                     text = stringResource(R.string.emergency_id, report.id),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (report.lifecycleStatus == EmergencyLifecycleStatus.ACTIVE) {
                     TextButton(onClick = { if (description.trim() != report.description.trim()) onContentUpdate(description.trim()) }) { Text(stringResource(R.string.save_edit)) }
                     TextButton(onClick = { onLifecycleChange(EmergencyLifecycleStatus.RESOLVED) }) { Text(stringResource(R.string.resolve)) }
                     TextButton(onClick = { onLifecycleChange(EmergencyLifecycleStatus.CANCELLED) }) { Text(stringResource(R.string.cancel_emergency)) }
                }
                 TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.close)) }
            }
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

@Composable
private fun locationStatusLabel(status: LocationStatus): String = when (status) {
    LocationStatus.PERMISSION_REQUIRED -> stringResource(R.string.location_status_permission)
    LocationStatus.ACQUIRING -> stringResource(R.string.location_status_acquiring)
    LocationStatus.FRESH -> stringResource(R.string.location_status_fresh)
    LocationStatus.STALE -> stringResource(R.string.location_status_stale)
    LocationStatus.LOW_ACCURACY -> stringResource(R.string.location_status_low_accuracy)
    LocationStatus.UNAVAILABLE -> stringResource(R.string.location_status_unavailable)
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
