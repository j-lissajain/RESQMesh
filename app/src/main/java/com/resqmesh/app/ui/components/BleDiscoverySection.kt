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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resqmesh.app.bluetooth.BleConnectionStatus
import com.resqmesh.app.bluetooth.BleDiscoveryManager
import com.resqmesh.app.bluetooth.BleMessagingManager
import com.resqmesh.app.bluetooth.BluetoothPermissions
import com.resqmesh.app.bluetooth.BluetoothReadinessChecker
import com.resqmesh.app.bluetooth.formatLastSeenTime
import com.resqmesh.app.ui.theme.EmergencyAmber
import com.resqmesh.app.ui.theme.EmergencySurface
import androidx.compose.ui.res.stringResource
import com.resqmesh.app.R
import com.resqmesh.app.ui.localizedText

@Composable
fun BleDiscoverySection(
    modifier: Modifier = Modifier,
    messagingManager: BleMessagingManager,
    discoveryManager: BleDiscoveryManager? = null
) {
    val context = LocalContext.current
    val checker = remember { BluetoothReadinessChecker(context.applicationContext) }
    val manager = discoveryManager ?: remember { BleDiscoveryManager(context.applicationContext) }
    val ownsManager = discoveryManager == null
    var state by remember { mutableStateOf(manager.state) }
    var messagingState by remember { mutableStateOf(messagingManager.state) }
    var messageText by remember { mutableStateOf("") }

    DisposableEffect(manager) {
        val removeListener = manager.addListener { updatedState ->
            state = updatedState
        }
        onDispose {
            removeListener()
            if (ownsManager) manager.close()
        }
    }

    DisposableEffect(messagingManager) {
        val removeListener = messagingManager.addListener { updatedState ->
            messagingState = updatedState
        }
        onDispose(removeListener)
    }

    LaunchedEffect(state.localAnonymousId) {
        messagingManager.setLocalSessionId(state.localAnonymousId)
        if (state.localAnonymousId != null) {
            messagingManager.startServer()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val readiness = checker.check()
        if (readiness.requiredPermissionsGranted) {
            messagingManager.startServer()
            manager.startDiscovery()
        } else {
            state = state.copy(
                statusMessage = context.getString(R.string.discovery_unavailable),
                error = context.getString(R.string.allow_permission_discovery, BluetoothPermissions.permissionLabel(context))
            )
        }
    }

    fun startDiscoveryOrRequestPermission() {
        val readiness = checker.check()
        if (!readiness.requiredPermissionsGranted) {
            permissionLauncher.launch(BluetoothPermissions.requiredPermissions())
        } else {
            manager.startDiscovery()
            messagingManager.setLocalSessionId(state.localAnonymousId)
            messagingManager.startServer()
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = EmergencySurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                 text = stringResource(R.string.nearby_devices),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                 text = stringResource(R.string.ble_broadcast_privacy),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = ::startDiscoveryOrRequestPermission,
                    enabled = !state.isActive,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmergencyAmber,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                     Text(text = stringResource(R.string.start_discovery), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        manager.stopDiscovery()
                        messagingManager.stopSession()
                    },
                    enabled = state.isActive,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                     Text(text = stringResource(R.string.stop_discovery))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = state.statusMessage,
                color = if (state.isActive) MaterialTheme.colorScheme.primary else EmergencyAmber,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            val error = state.error
            if (error != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            if (state.peers.isEmpty()) {
                Text(
                    text = if (state.isActive) {
                         stringResource(R.string.no_devices_active)
                    } else {
                         stringResource(R.string.no_peers_session)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.peers.forEach { peer ->
                        PeerRow(
                            anonymousId = peer.anonymousId,
                            lastSeen = formatLastSeenTime(peer.lastSeenAtMillis),
                            rssi = peer.rssi,
                            selected = messagingState.selectedPeerId == peer.anonymousId,
                            connected = peer.anonymousId in messagingState.connectedPeerIds,
                            onSelect = {
                                messagingManager.selectPeer(peer.anonymousId)
                            },
                            onConnect = {
                                messagingManager.selectPeer(peer.anonymousId)
                                messagingManager.connectToPeer(peer, state.localAnonymousId)
                            }
                        )
                    }
                }
            }

            MessagingPanel(
                state = messagingState,
                messageText = messageText,
                onMessageTextChange = { messageText = it },
                onSend = {
                    messagingManager.sendMessage(messageText)
                    messageText = ""
                }
            )
        }
    }
}

@Composable
private fun PeerRow(
    anonymousId: String,
    lastSeen: String,
    rssi: Int?,
    selected: Boolean,
    connected: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit
) {
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.anonymous_peer, anonymousId),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                     connected -> stringResource(R.string.connected)
                     selected -> stringResource(R.string.selected)
                     else -> stringResource(R.string.tap_to_select)
                },
                color = if (connected) MaterialTheme.colorScheme.primary else EmergencyAmber,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                 text = stringResource(R.string.last_seen, lastSeen),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = rssi?.let {
                     stringResource(R.string.rssi_value, it)
                 } ?: stringResource(R.string.rssi_unavailable),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onConnect,
                enabled = !connected,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                 Text(text = if (connected) stringResource(R.string.connected) else stringResource(R.string.connect))
            }
        }
    }
}

@Composable
private fun MessagingPanel(
    state: com.resqmesh.app.bluetooth.BleMessagingState,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Spacer(modifier = Modifier.height(14.dp))
    Text(
         text = stringResource(R.string.ble_messages),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = when (state.connectionStatus) {
             BleConnectionStatus.CONNECTED -> stringResource(R.string.connected_to, state.connectedPeerId ?: stringResource(R.string.peer))
             BleConnectionStatus.CONNECTING -> stringResource(R.string.connecting)
             BleConnectionStatus.WAITING_FOR_PEER -> stringResource(R.string.waiting_peer_initiate)
             BleConnectionStatus.DISCONNECTED -> stringResource(R.string.select_peer_connect)
        },
        color = if (state.connectionStatus == BleConnectionStatus.CONNECTED) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        style = MaterialTheme.typography.bodySmall
    )
    val error = state.error
    if (error != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    if (state.messages.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.messages.takeLast(8).forEach { message ->
                Text(
                     text = "${if (message.isIncoming) stringResource(R.string.peer) else stringResource(R.string.you)}: ${message.text} · ${message.state.localizedText()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    androidx.compose.material3.OutlinedTextField(
        value = messageText,
        onValueChange = onMessageTextChange,
        modifier = Modifier.fillMaxWidth(),
         enabled = state.connectionStatus == BleConnectionStatus.CONNECTED &&
             state.connectedPeerId != null && state.selectedPeerId == state.connectedPeerId,
         label = { Text(stringResource(R.string.short_test_message)) },
         placeholder = { Text(stringResource(R.string.utf8_message_hint)) },
        minLines = 1,
        maxLines = 3
    )
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = onSend,
         enabled = state.connectionStatus == BleConnectionStatus.CONNECTED &&
             state.connectedPeerId != null && state.selectedPeerId == state.connectedPeerId &&
             messageText.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = EmergencyAmber,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
         Text(text = stringResource(R.string.send_message), fontWeight = FontWeight.Bold)
    }
}
