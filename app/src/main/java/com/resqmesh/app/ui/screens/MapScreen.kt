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
import androidx.compose.ui.res.stringResource
import com.resqmesh.app.R
import com.resqmesh.app.ui.localizedText

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
             title = { Text(report.type.localizedText()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                     Text(stringResource(R.string.detail_severity, report.severity.localizedText()))
                     Text(stringResource(R.string.detail_status, report.status.localizedText()))
                    Text(report.description)
                     report.locationDescription?.let { Text(stringResource(R.string.landmark_value, it)) }
                    report.location?.let {
                        Text("${"%.6f".format(it.latitude)}, ${"%.6f".format(it.longitude)}")
                         Text(stringResource(R.string.location_source, it.source.localizedText()))
                     } ?: Text(stringResource(R.string.location_not_provided))
                }
            },
             confirmButton = { TextButton(onClick = { selectedReport = null }) { Text(stringResource(R.string.close)) } }
        )
    }
}
