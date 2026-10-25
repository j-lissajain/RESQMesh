package com.resqmesh.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resqmesh.app.data.LocalProfile
import com.resqmesh.app.data.LocalEmergencyRepository
import com.resqmesh.app.bluetooth.BleMessagingManager
import com.resqmesh.app.ui.components.BleDiscoverySection
import com.resqmesh.app.ui.components.BluetoothReadinessSection
import com.resqmesh.app.ui.components.EmergencyReportingSection
import com.resqmesh.app.ui.theme.EmergencyAmber
import com.resqmesh.app.ui.theme.EmergencyBackground
import com.resqmesh.app.ui.theme.EmergencySurface

private data class DashboardAction(
    val title: String,
    val description: String,
    val implemented: Boolean
)

@Composable
fun DashboardScreen(
    profile: LocalProfile,
    contentPadding: PaddingValues,
    onEditProfile: () -> Unit,
    onActionClick: (String) -> Unit
) {
    val context = LocalContext.current
    val messagingManager = remember {
        BleMessagingManager(context.applicationContext)
    }
    DisposableEffect(messagingManager) {
        onDispose { messagingManager.close() }
    }

    val actions = listOf(
        DashboardAction("Emergency Reports", "Create and view local cases", implemented = true),
        DashboardAction("Responder Network", "Coordinate nearby help", implemented = false),
        DashboardAction("Hazard Map", "See reported hazards", implemented = false),
        DashboardAction("Mesh Connectivity", "Explore device-to-device links", implemented = false)
    )
    val emergencyRepository = remember { LocalEmergencyRepository(context.applicationContext) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EmergencyBackground)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RESQMesh",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Offline-first prototype",
                    color = EmergencyAmber,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(onClick = onEditProfile) {
                Text(text = "Edit profile")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = EmergencySurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Welcome, ${profile.displayName}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Role: ${profile.role.label}",
                    color = Color(0xFFB8C8D2),
                    style = MaterialTheme.typography.bodyLarge
                )
                if (profile.capabilities.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Capabilities: ${profile.capabilities}",
                        color = Color(0xFFB8C8D2),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        BluetoothReadinessSection(modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))
        BleDiscoverySection(
            modifier = Modifier.fillMaxWidth(),
            messagingManager = messagingManager
        )

        Spacer(modifier = Modifier.height(24.dp))
        EmergencyReportingSection(
            repository = emergencyRepository,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Explore RESQMesh",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            actions.chunked(2).forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowActions.forEach { action ->
                        ActionCard(
                            action = action,
                            modifier = Modifier.weight(1f),
                            onClick = { onActionClick(action.title) }
                        )
                    }
                    if (rowActions.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    action: DashboardAction,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = EmergencySurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                color = Color(0xFF17333B),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (action.implemented) "AVAILABLE" else "NOT IMPLEMENTED",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    color = EmergencyAmber,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = action.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = action.description,
                color = Color(0xFF9DB0BB),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
