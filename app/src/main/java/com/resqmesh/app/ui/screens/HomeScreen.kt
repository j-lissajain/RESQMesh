package com.resqmesh.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.resqmesh.app.R
import com.resqmesh.app.bluetooth.BleMessagingManager
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.LocalProfile
import com.resqmesh.app.data.LanguagePreference
import com.resqmesh.app.ui.localizedText
import com.resqmesh.app.ui.languageCopy
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    profile: LocalProfile,
    reports: List<EmergencyReport>,
    messagingManager: BleMessagingManager,
    contentPadding: PaddingValues,
    onOpenMap: () -> Unit,
    onReportEmergency: () -> Unit,
    onInstantSos: () -> String?,
    onCancelInstantSos: (String) -> Unit,
    onOpenCoordination: () -> Unit,
    onOpenSafety: () -> Unit,
    languagePreference: LanguagePreference = LanguagePreference.ENGLISH
) {
    val copy = languageCopy(languagePreference)
    val instantSosDescription = stringResource(R.string.instant_sos_accessibility)
    var messagingState by remember { mutableStateOf(messagingManager.state) }
    var undoId by remember { mutableStateOf<String?>(null) }
    DisposableEffect(messagingManager) {
        val remove = messagingManager.addListener { messagingState = it }
        onDispose(remove)
    }
    LaunchedEffect(undoId) {
        if (undoId != null) {
            delay(5_000L)
            undoId = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("RESQMesh", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(copy.appTagline, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
         Text(stringResource(R.string.welcome_user, profile.displayName), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Button(
            onClick = { undoId = onInstantSos() },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .semantics { contentDescription = instantSosDescription }
        ) {
            Text(copy.instantSos, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
        undoId?.let { id ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                     Text(stringResource(R.string.sos_saved_undo), modifier = Modifier.weight(1f))
                     TextButton(onClick = { onCancelInstantSos(id); undoId = null }) { Text(stringResource(R.string.undo)) }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatusCard(
                title = stringResource(R.string.network),
                value = if (messagingState.connectedPeerId == null) stringResource(R.string.offline) else stringResource(R.string.connected),
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                title = stringResource(R.string.nearby_peers),
                value = messagingState.connectedPeerIds.size.toString(),
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                title = stringResource(R.string.saved_sos),
                value = reports.size.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.ready_when_you_are), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.home_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                     Button(onClick = onReportEmergency, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.report_emergency)) }
                     OutlinedButton(onClick = onOpenMap, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.open_map)) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenCoordination, modifier = Modifier.fillMaxWidth()) {
                    Text("${copy.volunteer}, ${copy.resources}, ${copy.safety}")
                }
                OutlinedButton(onClick = onOpenSafety, modifier = Modifier.fillMaxWidth()) {
                     Text(stringResource(R.string.safety_hub))
                }
            }
        }

         Text(stringResource(R.string.recent_emergencies), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
         if (reports.isEmpty()) {
             Text(stringResource(R.string.no_emergencies_saved), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            reports.take(3).forEach { report -> RecentReportCard(report) }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RecentReportCard(report: EmergencyReport) {
    val type = report.type.localizedText()
    val severity = report.severity.localizedText()
    val status = report.status.localizedText()
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                 Text(type, fontWeight = FontWeight.Bold)
                 Text(severity, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(report.description, maxLines = 2)
             Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
