package com.resqmesh.app.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID

/**
 * Stable application service UUID used by every RESQMesh installation.
 *
 * The advertisement carries only the service UUID and a short random session
 * token. They identify actual RESQMesh advertisements without exposing display
 * names, health details, emergency data, exact locations, or Bluetooth MAC
 * addresses.
 */
object ResqMeshBleProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("7d7f6c2a-4d20-4f7a-9d11-8fb6a7a4c201")
    val SERVICE_PARCEL_UUID: ParcelUuid = ParcelUuid(SERVICE_UUID)
    // Private prototype payload namespace; it is not used as a device identity.
    const val MANUFACTURER_ID: Int = 0xFFFF

    private const val SESSION_TOKEN_SIZE = 6
    private val secureRandom = SecureRandom()

    fun newSessionToken(): ByteArray = ByteArray(SESSION_TOKEN_SIZE).also { token ->
        secureRandom.nextBytes(token)
    }

    fun anonymousIdFrom(result: ScanResult): String? {
        val serviceData = result.scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID)
            ?: return null
        return anonymousIdFor(serviceData)
    }

    fun anonymousIdFor(token: ByteArray): String? {
        if (token.size != SESSION_TOKEN_SIZE) return null
        return token.toHex()
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02X".format(Locale.US, byte.toInt() and 0xFF)
    }
}

data class BlePeer(
    val anonymousId: String,
    val lastSeenAtMillis: Long,
    val rssi: Int?
)

data class BleDiscoveryState(
    val isStarting: Boolean = false,
    val isScanning: Boolean = false,
    val isAdvertising: Boolean = false,
    val peers: List<BlePeer> = emptyList(),
    val statusMessage: String = "Discovery is stopped.",
    val error: String? = null
) {
    val isActive: Boolean
        get() = isStarting || isScanning || isAdvertising
}

/**
 * Owns one foreground scan session and one foreground advertising session.
 * Callers observe state through [addListener] and must call [close] when the
 * owning UI/lifecycle no longer needs discovery.
 */
class BleDiscoveryManager(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val readinessChecker = BluetoothReadinessChecker(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(BleDiscoveryState) -> Unit>()
    private val peersById = LinkedHashMap<String, BlePeer>()

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var scanCallback: ScanCallback? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var localAnonymousId: String? = null
    private var closed = false

    private var _state = BleDiscoveryState()
    val state: BleDiscoveryState
        get() = _state

    fun addListener(listener: (BleDiscoveryState) -> Unit): () -> Unit {
        if (closed) {
            listener(_state)
            return {}
        }
        listeners += listener
        listener(_state)
        return { listeners -= listener }
    }

    fun startDiscovery() {
        if (closed || _state.isActive) return

        peersById.clear()
        publish(
            _state.copy(
                isStarting = true,
                peers = emptyList(),
                statusMessage = "Starting BLE discovery...",
                error = null
            )
        )

        val readiness = readinessChecker.check()
        when {
            !readiness.bluetoothSupported -> {
                abort("This phone does not support Bluetooth.")
                return
            }

            !readiness.bleSupported -> {
                abort("This phone does not support Bluetooth Low Energy.")
                return
            }

            !readiness.requiredPermissionsGranted -> {
                abort("Allow ${BluetoothPermissions.permissionLabel()} access before starting discovery.")
                return
            }

            readiness.bluetoothEnabled != true -> {
                abort("Turn on Bluetooth before starting discovery.")
                return
            }
        }

        val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
        val adapter = try {
            bluetoothManager?.adapter
        } catch (_: SecurityException) {
            null
        }

        if (adapter == null) {
            abort("The Bluetooth adapter is unavailable on this phone.")
            return
        }

        startScanning(adapter)
        startAdvertising(adapter)
        if (bluetoothLeScanner == null && bluetoothLeAdvertiser == null) {
            publish(_state.copy(isStarting = false))
        }
    }

    fun stopDiscovery() {
        stopScanning()
        stopAdvertising()
        peersById.clear()
        localAnonymousId = null
        publish(
            BleDiscoveryState(
                statusMessage = "Discovery stopped."
            )
        )
    }

    override fun close() {
        if (closed) return
        stopDiscovery()
        closed = true
        listeners.clear()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun startScanning(adapter: BluetoothAdapter) {
        val scanner = try {
            adapter.bluetoothLeScanner
        } catch (_: SecurityException) {
            null
        }

        if (scanner == null) {
            fail("BLE scanning is not supported or is unavailable on this phone.")
            return
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onPeerDiscovered(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::onPeerDiscovered)
            }

            override fun onScanFailed(errorCode: Int) {
                if (!_state.isStarting && !_state.isScanning && !_state.isAdvertising) return
                bluetoothLeScanner = null
                scanCallback = null
                publish(
                    _state.copy(
                        isStarting = bluetoothLeAdvertiser != null,
                        isScanning = false,
                        statusMessage = activeStatus(
                            isScanning = false,
                            isAdvertising = _state.isAdvertising
                        ),
                        error = "BLE scan failed: ${scanErrorMessage(errorCode)}"
                    )
                )
            }
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ResqMeshBleProtocol.SERVICE_PARCEL_UUID)
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, callback)
            bluetoothLeScanner = scanner
            scanCallback = callback
            publish(
                _state.copy(
                    isStarting = false,
                    isScanning = true,
                        statusMessage = activeStatus(
                            isScanning = true,
                            isAdvertising = _state.isAdvertising
                        ),
                        error = _state.error
                )
            )
        } catch (_: SecurityException) {
            fail("BLE scanning permission was revoked. Grant permission and try again.")
        } catch (exception: RuntimeException) {
            fail("BLE scanning could not start: ${exception.message ?: "unknown error"}")
        }
    }

    private fun startAdvertising(adapter: BluetoothAdapter) {
        val advertiser = try {
            adapter.bluetoothLeAdvertiser
        } catch (_: SecurityException) {
            null
        }

        if (advertiser == null) {
            fail("BLE advertising is not supported or is unavailable on this phone.")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val sessionToken = ResqMeshBleProtocol.newSessionToken()
        localAnonymousId = ResqMeshBleProtocol.anonymousIdFor(sessionToken)
        val data = AdvertiseData.Builder()
            // The UUID identifies RESQMesh. The private manufacturer payload
            // carries only the anonymous session token. Together they remain
            // within the legacy BLE advertisement size limit.
            .addServiceUuid(ResqMeshBleProtocol.SERVICE_PARCEL_UUID)
            .addManufacturerData(
                ResqMeshBleProtocol.MANUFACTURER_ID,
                sessionToken
            )
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                if (!_state.isStarting && !_state.isScanning && !_state.isAdvertising) return
                bluetoothLeAdvertiser = advertiser
                advertiseCallback = this
                publish(
                    _state.copy(
                        isStarting = false,
                        isAdvertising = true,
                        statusMessage = activeStatus(
                            isScanning = _state.isScanning,
                            isAdvertising = true
                        ),
                        error = _state.error
                    )
                )
            }

            override fun onStartFailure(errorCode: Int) {
                if (!_state.isStarting && !_state.isScanning && !_state.isAdvertising) return
                bluetoothLeAdvertiser = null
                advertiseCallback = null
                localAnonymousId = null
                publish(
                    _state.copy(
                        isStarting = false,
                        isAdvertising = false,
                        statusMessage = activeStatus(
                            isScanning = _state.isScanning,
                            isAdvertising = false
                        ),
                        error = "BLE advertising failed: ${advertiseErrorMessage(errorCode)}"
                    )
                )
            }
        }

        try {
            advertiser.startAdvertising(settings, data, callback)
            bluetoothLeAdvertiser = advertiser
            advertiseCallback = callback
        } catch (_: SecurityException) {
            localAnonymousId = null
            fail("BLE advertising permission was revoked. Grant permission and try again.")
        } catch (exception: RuntimeException) {
            localAnonymousId = null
            fail("BLE advertising could not start: ${exception.message ?: "unknown error"}")
        }
    }

    private fun stopScanning() {
        val scanner = bluetoothLeScanner
        val callback = scanCallback
        if (scanner != null && callback != null) {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
                // Permissions may have been revoked while the screen was open.
            }
        }
        bluetoothLeScanner = null
        scanCallback = null
    }

    private fun stopAdvertising() {
        val advertiser = bluetoothLeAdvertiser
        val callback = advertiseCallback
        if (advertiser != null && callback != null) {
            try {
                advertiser.stopAdvertising(callback)
            } catch (_: SecurityException) {
                // Permissions may have been revoked while the screen was open.
            }
        }
        bluetoothLeAdvertiser = null
        advertiseCallback = null
        localAnonymousId = null
    }

    private fun onPeerDiscovered(result: ScanResult) {
        if (!_state.isScanning) return
        val anonymousId = ResqMeshBleProtocol.anonymousIdFrom(result) ?: return
        if (anonymousId == localAnonymousId) return
        val peer = BlePeer(
            anonymousId = anonymousId,
            lastSeenAtMillis = System.currentTimeMillis(),
            rssi = result.rssi
        )
        peersById[anonymousId] = peer
        publish(_state.copy(peers = peersById.values.sortedByDescending { it.lastSeenAtMillis }))
    }

    private fun abort(message: String) {
        publish(
            _state.copy(
                isStarting = false,
                statusMessage = activeStatus(
                    isScanning = _state.isScanning,
                    isAdvertising = _state.isAdvertising
                ),
                error = message
            )
        )
    }

    private fun fail(message: String) {
        publish(
            _state.copy(
                statusMessage = activeStatus(
                    isScanning = _state.isScanning,
                    isAdvertising = _state.isAdvertising
                ),
                error = message
            )
        )
    }

    private fun publish(newState: BleDiscoveryState) {
        _state = newState
        val snapshot = newState
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listeners.toList().forEach { listener -> listener(snapshot) }
        } else {
            mainHandler.post {
                listeners.toList().forEach { listener -> listener(snapshot) }
            }
        }
    }

    private fun activeStatus(isScanning: Boolean, isAdvertising: Boolean): String = when {
        isScanning && isAdvertising -> "Scanning and advertising"
        isScanning -> "Scanning (advertising unavailable)"
        isAdvertising -> "Advertising (scanning unavailable)"
        else -> "Discovery unavailable"
    }

    private fun scanErrorMessage(errorCode: Int): String = when (errorCode) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "scanner already started"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "application registration failed"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "internal scanner error"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "scanner feature unsupported"
        ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "scanner hardware resources unavailable"
        ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "scanning requested too frequently"
        else -> "error code $errorCode"
    }

    private fun advertiseErrorMessage(errorCode: Int): String = when (errorCode) {
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "advertiser already started"
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "advertisement data is too large"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "advertising unsupported"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "internal advertiser error"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "too many active advertisers"
        else -> "error code $errorCode"
    }
}

fun formatLastSeenTime(timestampMillis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))
