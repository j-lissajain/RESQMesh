package com.resqmesh.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resqmesh.app.bluetooth.BleMessagingManager
import com.resqmesh.app.bluetooth.BleDiscoveryManager
import com.resqmesh.app.bluetooth.EmergencyRelayController
import com.resqmesh.app.ui.components.BleDiscoverySection
import com.resqmesh.app.ui.components.BluetoothReadinessSection

@Composable
fun NetworkScreen(
    messagingManager: BleMessagingManager,
    discoveryManager: BleDiscoveryManager,
    relayController: EmergencyRelayController,
    contentPadding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
    ) {
        BluetoothReadinessSection(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        BleDiscoverySection(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            messagingManager = messagingManager,
            discoveryManager = discoveryManager
        )
        RelayDiagnosticsCard(
            relayController = relayController,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun RelayDiagnosticsCard(
    relayController: EmergencyRelayController,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf(relayController.state) }
    DisposableEffect(relayController) {
        val remove = relayController.addListener { state = it }
        onDispose(remove)
    }
    Card(modifier = modifier) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(16.dp)) {
            Text("Mesh relay diagnostics", style = MaterialTheme.typography.titleMedium)
            Text(
                "Queued: ${state.queuedItems.size} · Waiting for peer: ${state.waitingForPeer} · In flight: ${state.inFlight}",
                style = MaterialTheme.typography.bodySmall
            )
            state.lastError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            state.queuedItems.take(8).forEach { item ->
                Text(
                    "${item.emergencyId.take(12)}… rev ${item.revision} · hop ${item.hopCount} · TTL ${item.remainingTtl} · attempts ${item.attempts} · ACK ${item.acknowledgedPeers}",
                    style = MaterialTheme.typography.labelSmall
                )
                item.lastError?.let { error ->
                    Text("Last relay error: $error", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (state.queuedItems.isEmpty()) {
                Text("No persistent relay items.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
