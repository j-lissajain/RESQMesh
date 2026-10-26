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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resqmesh.app.bluetooth.BleMessagingManager
import com.resqmesh.app.bluetooth.BleDiscoveryManager
import com.resqmesh.app.bluetooth.EmergencyRelayController
import com.resqmesh.app.bluetooth.BackgroundNetworkingController
import com.resqmesh.app.bluetooth.BackgroundNetworkingStore
import com.resqmesh.app.security.MeshTrustState
import com.resqmesh.app.security.PeerTrustStore
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import com.resqmesh.app.ui.components.BleDiscoverySection
import com.resqmesh.app.ui.components.BluetoothReadinessSection
import androidx.compose.ui.res.stringResource
import com.resqmesh.app.R

@Composable
fun NetworkScreen(
    messagingManager: BleMessagingManager,
    discoveryManager: BleDiscoveryManager,
    relayController: EmergencyRelayController,
    backgroundNetworkingStore: BackgroundNetworkingStore,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    var backgroundEnabled by remember { mutableStateOf(backgroundNetworkingStore.isEnabled()) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            backgroundEnabled = BackgroundNetworkingController.start(context)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
    ) {
        BluetoothReadinessSection(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                 Text(stringResource(R.string.background_relay), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (backgroundEnabled) {
                         stringResource(R.string.background_relay_active)
                    } else {
                         stringResource(R.string.background_relay_inactive)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (backgroundEnabled) {
                    OutlinedButton(onClick = { BackgroundNetworkingController.stop(context); backgroundEnabled = false }) {
                         Text(stringResource(R.string.stop_background_relay))
                    }
                } else {
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            backgroundEnabled = BackgroundNetworkingController.start(context)
                        }
                    }) {
                         Text(stringResource(R.string.enable_background_relay))
                    }
                }
            }
        }
        BleDiscoverySection(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            messagingManager = messagingManager,
            discoveryManager = discoveryManager
        )
        ConnectedPeersCard(
            messagingManager = messagingManager,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
        RelayDiagnosticsCard(
            relayController = relayController,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun ConnectedPeersCard(
    messagingManager: BleMessagingManager,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf(messagingManager.state) }
    DisposableEffect(messagingManager) {
        val remove = messagingManager.addListener { state = it }
        onDispose(remove)
    }
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.connected_peers), style = MaterialTheme.typography.titleMedium)
            if (!state.localIdentityFingerprint.isNullOrBlank()) {
                Text(
                    "Local device key: ${PeerTrustStore.shortFingerprint(state.localIdentityFingerprint)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.connectedPeerIds.isEmpty()) {
                Text(stringResource(R.string.no_active_peer_connections), style = MaterialTheme.typography.bodySmall)
            } else {
                state.connectedPeerIds.sorted().forEach { peerId ->
                    val peerTrust = state.peerTrustStates[peerId]
                    val peerFingerprint = state.peerFingerprints[peerId]
                    val trustLabel = when (peerTrust) {
                        MeshTrustState.AUTHENTICATED_KNOWN -> "Known device key"
                        MeshTrustState.AUTHENTICATED_UNKNOWN -> "Signed by unknown device key"
                        MeshTrustState.LEGACY_UNSIGNED -> "Unsigned legacy peer"
                        MeshTrustState.INVALID -> "Invalid/suspicious signature"
                        MeshTrustState.LOCAL, null -> "Unknown peer (no signed message yet)"
                    }
                    Text(stringResource(R.string.peer_connected, peerId), style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (peerFingerprint != null) {
                                "$trustLabel · key ${PeerTrustStore.shortFingerprint(peerFingerprint)}"
                            } else {
                                trustLabel
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (peerTrust == MeshTrustState.INVALID) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (peerFingerprint != null && peerTrust != MeshTrustState.INVALID) {
                            val isTrusted = state.trustedFingerprints.contains(peerFingerprint)
                            TextButton(
                                onClick = {
                                    if (isTrusted) {
                                        messagingManager.forgetPeerFingerprint(peerFingerprint)
                                    } else {
                                        messagingManager.trustPeerFingerprint(peerFingerprint)
                                    }
                                }
                            ) {
                                Text(if (isTrusted) "Forget key" else "Mark key known")
                            }
                        }
                    }
                }
            }
            if (state.trustedFingerprints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Known device keys (${state.trustedFingerprints.size}) — verifies device key continuity only, not real-world identity:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.trustedFingerprints.sorted().forEach { fingerprint ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            PeerTrustStore.shortFingerprint(fingerprint),
                            style = MaterialTheme.typography.labelSmall
                        )
                        TextButton(onClick = { messagingManager.forgetPeerFingerprint(fingerprint) }) {
                            Text("Forget")
                        }
                    }
                }
            }
            state.error?.let { Text(stringResource(R.string.last_transport_issue, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
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
             Text(stringResource(R.string.mesh_relay_diagnostics), style = MaterialTheme.typography.titleMedium)
            Text(
                 stringResource(R.string.relay_summary, state.queuedItems.size, state.waitingForPeer, state.inFlight),
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
                     Text(stringResource(R.string.last_relay_error, error), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (state.queuedItems.isEmpty()) {
                 Text(stringResource(R.string.no_persistent_relay_items), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
