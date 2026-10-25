package com.resqmesh.app.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resqmesh.app.bluetooth.BluetoothPermissions
import com.resqmesh.app.bluetooth.BluetoothReadinessChecker
import com.resqmesh.app.ui.theme.EmergencyAmber
import com.resqmesh.app.ui.theme.EmergencySurface

@Composable
fun BluetoothReadinessSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val checker = remember { BluetoothReadinessChecker(context.applicationContext) }
    var readiness by remember { mutableStateOf(checker.check()) }
    var permissionRequestAttempted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionRequestAttempted = true
        readiness = checker.check()
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        readiness = checker.check()
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = EmergencySurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bluetooth readiness",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Device checks for BLE discovery and advertising",
                        color = Color(0xFF9DB0BB),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { readiness = checker.check() }) {
                    Text(text = "Refresh", color = EmergencyAmber)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            ReadinessRow(
                left = ReadinessItem("Bluetooth", supportStatus(readiness.bluetoothSupported)),
                right = ReadinessItem("BLE", supportStatus(readiness.bleSupported))
            )
            Spacer(modifier = Modifier.height(8.dp))
            ReadinessRow(
                left = ReadinessItem("Bluetooth enabled", enabledStatus(readiness.bluetoothEnabled)),
                right = ReadinessItem(
                    "Permissions",
                    if (readiness.requiredPermissionsGranted) "Granted" else "Needed"
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            ReadinessRow(
                left = ReadinessItem(
                    "BLE advertising",
                    supportStatus(readiness.bleAdvertisingSupported)
                ),
                right = null
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = readiness.guidance,
                color = Color(0xFFD5E2EA),
                style = MaterialTheme.typography.bodySmall
            )

            if (!readiness.requiredPermissionsGranted && readiness.bluetoothSupported && readiness.bleSupported) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        permissionRequestAttempted = true
                        permissionLauncher.launch(BluetoothPermissions.requiredPermissions())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmergencyAmber,
                        contentColor = Color(0xFF1B1300)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = if (permissionRequestAttempted) {
                            "Request ${BluetoothPermissions.permissionLabel()} again"
                        } else {
                            "Grant ${BluetoothPermissions.permissionLabel()} permission"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                if (permissionRequestAttempted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            settingsLauncher.launch(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Open app settings", color = EmergencyAmber)
                    }
                }
            } else if (
                readiness.bluetoothSupported &&
                readiness.bleSupported &&
                readiness.bluetoothEnabled == false
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        settingsLauncher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Open Bluetooth settings")
                }
            }
        }
    }
}

private data class ReadinessItem(
    val label: String,
    val value: String
)

@Composable
private fun ReadinessRow(
    left: ReadinessItem,
    right: ReadinessItem?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ReadinessCell(item = left, modifier = Modifier.weight(1f))
        if (right != null) {
            ReadinessCell(item = right, modifier = Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReadinessCell(
    item: ReadinessItem,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = item.label,
            color = Color(0xFF9DB0BB),
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = item.value,
            color = readinessColor(item.value),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun supportStatus(supported: Boolean?): String = when (supported) {
    true -> "Supported"
    false -> "Not supported"
    null -> "Unknown"
}

private fun enabledStatus(enabled: Boolean?): String = when (enabled) {
    true -> "On"
    false -> "Off"
    null -> "Unknown"
}

private fun readinessColor(value: String): Color = when (value) {
    "Supported", "On", "Granted" -> Color(0xFF78D6D0)
    "Unknown" -> EmergencyAmber
    else -> Color(0xFFFFA08A)
}
