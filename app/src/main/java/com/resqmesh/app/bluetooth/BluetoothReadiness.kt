package com.resqmesh.app.bluetooth

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object BluetoothPermissions {
    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun areGranted(context: Context): Boolean = requiredPermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun permissionLabel(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        "Nearby devices"
    } else {
        "Location"
    }
}

data class BluetoothReadiness(
    val bluetoothSupported: Boolean,
    val bleSupported: Boolean,
    val bluetoothEnabled: Boolean?,
    val requiredPermissionsGranted: Boolean,
    val bleAdvertisingSupported: Boolean?,
    val guidance: String
)

class BluetoothReadinessChecker(private val context: Context) {
    fun check(): BluetoothReadiness {
        val packageManager = context.packageManager
        val bluetoothSupported = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        val bleSupported = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val permissionsGranted = BluetoothPermissions.areGranted(context)
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val adapter = try {
            bluetoothManager?.adapter
        } catch (_: SecurityException) {
            null
        }

        val canInspectAdapter = adapter != null &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || permissionsGranted)
        val bluetoothEnabled = when {
            !bluetoothSupported || adapter == null -> false
            !canInspectAdapter -> null
            else -> try {
                adapter.isEnabled
            } catch (_: SecurityException) {
                null
            }
        }
        val bleAdvertisingSupported = when {
            !bleSupported || adapter == null -> false
            !canInspectAdapter -> null
            else -> try {
                adapter.bluetoothLeAdvertiser != null
            } catch (_: SecurityException) {
                null
            }
        }

        return BluetoothReadiness(
            bluetoothSupported = bluetoothSupported,
            bleSupported = bleSupported,
            bluetoothEnabled = bluetoothEnabled,
            requiredPermissionsGranted = permissionsGranted,
            bleAdvertisingSupported = bleAdvertisingSupported,
            guidance = guidanceFor(
                bluetoothSupported = bluetoothSupported,
                bleSupported = bleSupported,
                bluetoothEnabled = bluetoothEnabled,
                permissionsGranted = permissionsGranted,
                bleAdvertisingSupported = bleAdvertisingSupported
            )
        )
    }

    private fun guidanceFor(
        bluetoothSupported: Boolean,
        bleSupported: Boolean,
        bluetoothEnabled: Boolean?,
        permissionsGranted: Boolean,
        bleAdvertisingSupported: Boolean?
    ): String = when {
        !bluetoothSupported -> "This phone does not support Bluetooth."
        !bleSupported -> "Bluetooth is available, but Bluetooth Low Energy is not supported on this phone."
        !permissionsGranted -> "Allow ${BluetoothPermissions.permissionLabel()} access to check BLE readiness."
        bluetoothEnabled == false -> "Turn on Bluetooth in system settings to prepare this phone."
        bluetoothEnabled == null -> "Bluetooth status could not be checked yet. Refresh after granting permissions."
        bleAdvertisingSupported == false -> "BLE is available, but this phone cannot advertise as a BLE peripheral."
        bleAdvertisingSupported == null -> "BLE advertising support could not be checked yet."
        else -> "Ready for BLE discovery. Scanning and advertising can be started from the dashboard."
    }
}
