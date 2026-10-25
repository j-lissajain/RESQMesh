package com.resqmesh.app.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.location.LocationEngineState
import com.resqmesh.app.maps.OfflineMapDataset
import com.resqmesh.app.ui.components.OfflineMapScreen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

@Composable
fun MapDestinationScreen(
    dataset: OfflineMapDataset?,
    datasetDescription: String,
    locationState: LocationEngineState,
    reports: List<EmergencyReport>,
    contentPadding: PaddingValues
) {
    var selectedReport by remember { mutableStateOf<EmergencyReport?>(null) }
    OfflineMapScreen(
        dataset = dataset,
        datasetDescription = datasetDescription,
        locationState = locationState,
        reports = reports,
        contentPadding = contentPadding,
        onReportClick = { selectedReport = it }
    )
    selectedReport?.let { report ->
        AlertDialog(
            onDismissRequest = { selectedReport = null },
            title = { Text(report.type.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Severity: ${report.severity.label}")
                    Text("Status: ${report.status.label}")
                    Text(report.description)
                    report.locationDescription?.let { Text("Landmark: $it") }
                    report.location?.let {
                        Text("${"%.6f".format(it.latitude)}, ${"%.6f".format(it.longitude)}")
                        Text("Source: ${it.source.label}")
                    } ?: Text("Location: not provided")
                }
            },
            confirmButton = { TextButton(onClick = { selectedReport = null }) { Text("Close") } }
        )
    }
}
