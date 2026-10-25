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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resqmesh.app.bluetooth.BleMessagingManager
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.LocalProfile

@Composable
fun HomeScreen(
    profile: LocalProfile,
    reports: List<EmergencyReport>,
    messagingManager: BleMessagingManager,
    contentPadding: PaddingValues,
    onOpenMap: () -> Unit,
    onReportEmergency: () -> Unit
) {
    var messagingState by remember { mutableStateOf(messagingManager.state) }
    DisposableEffect(messagingManager) {
        val remove = messagingManager.addListener { messagingState = it }
        onDispose(remove)
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
        Text("Offline emergency coordination", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text("Welcome, ${profile.displayName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatusCard(
                title = "Network",
                value = if (messagingState.connectedPeerId == null) "Offline" else "Connected",
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                title = "Nearby peers",
                value = if (messagingState.connectedPeerId == null) "0" else "1",
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                title = "Saved SOS",
                value = reports.size.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Ready when you are", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Create a local emergency report or inspect the bundled Nagpur map. Reports remain local unless you explicitly send one to a connected peer.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onReportEmergency, modifier = Modifier.weight(1f)) { Text("Report emergency") }
                    OutlinedButton(onClick = onOpenMap, modifier = Modifier.weight(1f)) { Text("Open map") }
                }
            }
        }

        Text("Recent emergencies", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (reports.isEmpty()) {
            Text("No emergencies saved on this device yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(report.type.label, fontWeight = FontWeight.Bold)
                Text(report.severity.label, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(report.description, maxLines = 2)
            Text(report.status.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
