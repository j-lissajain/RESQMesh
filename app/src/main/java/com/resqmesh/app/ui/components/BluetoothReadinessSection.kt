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
import androidx.compose.ui.res.stringResource
import com.resqmesh.app.R

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
                        text = stringResource(R.string.bluetooth_readiness),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.ble_readiness_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { readiness = checker.check() }) {
                    Text(text = stringResource(R.string.refresh), color = EmergencyAmber)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            ReadinessRow(
                left = ReadinessItem(stringResource(R.string.bluetooth), supportStatus(readiness.bluetoothSupported)),
                right = ReadinessItem(stringResource(R.string.ble), supportStatus(readiness.bleSupported))
            )
            Spacer(modifier = Modifier.height(8.dp))
            ReadinessRow(
                left = ReadinessItem(stringResource(R.string.bluetooth_enabled), enabledStatus(readiness.bluetoothEnabled)),
                right = ReadinessItem(
                    stringResource(R.string.permissions),
                    if (readiness.requiredPermissionsGranted) stringResource(R.string.granted) else stringResource(R.string.needed)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            ReadinessRow(
                left = ReadinessItem(
                    stringResource(R.string.ble_advertising),
                    supportStatus(readiness.bleAdvertisingSupported)
                ),
                right = null
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = readiness.guidance,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = if (permissionRequestAttempted) {
                            stringResource(R.string.request_permission_again, BluetoothPermissions.permissionLabel(context))
                        } else {
                            stringResource(R.string.grant_permission, BluetoothPermissions.permissionLabel(context))
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
                        Text(text = stringResource(R.string.open_app_settings), color = EmergencyAmber)
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
                    Text(text = stringResource(R.string.open_bluetooth_settings))
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun supportStatus(supported: Boolean?): String = when (supported) {
    true -> stringResource(R.string.supported)
    false -> stringResource(R.string.not_supported)
    null -> stringResource(R.string.unknown)
}

@Composable
private fun enabledStatus(enabled: Boolean?): String = when (enabled) {
    true -> stringResource(R.string.on)
    false -> stringResource(R.string.off)
    null -> stringResource(R.string.unknown)
}

@Composable
private fun readinessColor(value: String): Color {
    val positive = setOf(
        stringResource(R.string.supported),
        stringResource(R.string.on),
        stringResource(R.string.granted)
    )
    return when {
        value in positive -> MaterialTheme.colorScheme.primary
        value == stringResource(R.string.unknown) -> EmergencyAmber
        else -> MaterialTheme.colorScheme.error
    }
}
