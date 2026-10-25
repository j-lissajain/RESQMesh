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

@Composable
fun BleDiscoverySection(
    modifier: Modifier = Modifier,
    messagingManager: BleMessagingManager
) {
    val context = LocalContext.current
    val checker = remember { BluetoothReadinessChecker(context.applicationContext) }
    val manager = remember { BleDiscoveryManager(context.applicationContext) }
    var state by remember { mutableStateOf(manager.state) }
    var messagingState by remember { mutableStateOf(messagingManager.state) }
    var messageText by remember { mutableStateOf("") }

    DisposableEffect(manager) {
        val removeListener = manager.addListener { updatedState ->
            state = updatedState
        }
        onDispose {
            removeListener()
            manager.close()
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
                statusMessage = "Discovery unavailable",
                error = "Allow ${BluetoothPermissions.permissionLabel()} access to discover nearby RESQMesh devices."
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
                text = "Nearby RESQMesh Devices",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Actual BLE advertisements only. No profile information is broadcast.",
                color = Color(0xFF9DB0BB),
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
                        contentColor = Color(0xFF1B1300)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(text = "Start Discovery", fontWeight = FontWeight.Bold)
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
                    Text(text = "Stop Discovery")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = state.statusMessage,
                color = if (state.isActive) Color(0xFF78D6D0) else EmergencyAmber,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            val error = state.error
            if (error != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = error,
                    color = Color(0xFFFFA08A),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            if (state.peers.isEmpty()) {
                Text(
                    text = if (state.isActive) {
                        "No RESQMesh devices discovered yet. Keep both phones nearby and awake."
                    } else {
                        "No peers discovered in this session."
                    },
                    color = Color(0xFFB8C8D2),
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
                            connected = messagingState.connectedPeerId == peer.anonymousId,
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17333B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Anonymous peer $anonymousId",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    connected -> "Connected"
                    selected -> "Selected"
                    else -> "Tap to select"
                },
                color = if (connected) Color(0xFF78D6D0) else EmergencyAmber,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Last seen at $lastSeen",
                color = Color(0xFFB8C8D2),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = rssi?.let {
                    "RSSI: $it dBm (signal strength, not an accurate distance)"
                } ?: "RSSI unavailable",
                color = Color(0xFFB8C8D2),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onConnect,
                enabled = !connected,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = if (connected) "Connected" else "Connect")
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
        text = "BLE messages",
        color = Color.White,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = when (state.connectionStatus) {
            BleConnectionStatus.CONNECTED -> "Connected to ${state.connectedPeerId ?: "anonymous peer"}"
            BleConnectionStatus.CONNECTING -> "Connecting..."
            BleConnectionStatus.WAITING_FOR_PEER -> "Waiting for the peer to initiate the connection"
            BleConnectionStatus.DISCONNECTED -> "Select a peer and connect to exchange messages"
        },
        color = if (state.connectionStatus == BleConnectionStatus.CONNECTED) {
            Color(0xFF78D6D0)
        } else {
            Color(0xFFB8C8D2)
        },
        style = MaterialTheme.typography.bodySmall
    )
    val error = state.error
    if (error != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = error, color = Color(0xFFFFA08A), style = MaterialTheme.typography.bodySmall)
    }

    if (state.messages.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.messages.takeLast(8).forEach { message ->
                Text(
                    text = "${if (message.isIncoming) "Peer" else "You"}: ${message.text} · ${message.state.label}",
                    color = Color(0xFFD5E2EA),
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
        label = { Text("Short test message") },
        placeholder = { Text("Type a UTF-8 message") },
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
            contentColor = Color(0xFF1B1300)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(text = "Send message", fontWeight = FontWeight.Bold)
    }
}

private val com.resqmesh.app.bluetooth.MessageDeliveryState.label: String
    get() = when (this) {
        com.resqmesh.app.bluetooth.MessageDeliveryState.SENDING -> "Sending"
        com.resqmesh.app.bluetooth.MessageDeliveryState.DELIVERED -> "Delivered"
        com.resqmesh.app.bluetooth.MessageDeliveryState.RECEIVED -> "Received"
        com.resqmesh.app.bluetooth.MessageDeliveryState.FAILED -> "Failed"
        com.resqmesh.app.bluetooth.MessageDeliveryState.DISCONNECTED -> "Disconnected"
    }
