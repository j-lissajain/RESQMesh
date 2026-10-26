package com.resqmesh.app.bluetooth

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.resqmesh.app.R

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

    fun permissionLabel(context: Context): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getString(R.string.nearby_devices_permission)
    } else {
        context.getString(R.string.location_permission)
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
                context = context,
                bluetoothSupported = bluetoothSupported,
                bleSupported = bleSupported,
                bluetoothEnabled = bluetoothEnabled,
                permissionsGranted = permissionsGranted,
                bleAdvertisingSupported = bleAdvertisingSupported
            )
        )
    }

    private fun guidanceFor(
        context: Context,
        bluetoothSupported: Boolean,
        bleSupported: Boolean,
        bluetoothEnabled: Boolean?,
        permissionsGranted: Boolean,
        bleAdvertisingSupported: Boolean?
    ): String = when {
        !bluetoothSupported -> context.getString(R.string.bluetooth_not_supported)
        !bleSupported -> context.getString(R.string.ble_not_supported)
        !permissionsGranted -> context.getString(R.string.allow_ble_permission, BluetoothPermissions.permissionLabel(context))
        bluetoothEnabled == false -> context.getString(R.string.turn_on_bluetooth)
        bluetoothEnabled == null -> context.getString(R.string.bluetooth_status_unknown)
        bleAdvertisingSupported == false -> context.getString(R.string.ble_advertising_not_supported)
        bleAdvertisingSupported == null -> context.getString(R.string.ble_advertising_unknown)
        else -> context.getString(R.string.ble_ready)
    }
}
