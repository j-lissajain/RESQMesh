package com.resqmesh.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.resqmesh.app.data.EmergencyLocationSource

object LocationPermissions {
    fun requiredPermissions(): Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun hasAny(context: Context): Boolean = hasPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) || hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasPrecise(context: Context): Boolean = hasPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

enum class LocationStatus {
    PERMISSION_REQUIRED,
    ACQUIRING,
    FRESH,
    STALE,
    LOW_ACCURACY,
    UNAVAILABLE
}

data class DeviceLocationReading(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val timestampMillis: Long,
    val source: EmergencyLocationSource,
    val precisePermission: Boolean
)

data class LocationEngineState(
    val reading: DeviceLocationReading? = null,
    val status: LocationStatus = LocationStatus.PERMISSION_REQUIRED,
    val message: String = "Location permission is required to read device coordinates.",
    val isRefreshing: Boolean = false
)

class OfflineLocationEngine(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(LocationEngineState) -> Unit>()
    private var timeoutRunnable: Runnable? = null
    private var requestingUpdates = false
    private var closed = false
    private var _state = LocationEngineState()

    val state: LocationEngineState
        get() = _state

    fun addListener(listener: (LocationEngineState) -> Unit): () -> Unit {
        if (closed) {
            listener(_state)
            return {}
        }
        listeners += listener
        listener(_state)
        return { listeners -= listener }
    }

    fun refresh() {
        if (closed || requestingUpdates) return
        if (!LocationPermissions.hasAny(appContext)) {
            publish(
                _state.copy(
                    status = LocationStatus.PERMISSION_REQUIRED,
                    message = "Allow foreground location access to read device coordinates.",
                    isRefreshing = false
                )
            )
            return
        }

        val cached = readCachedGpsLocation()
        if (cached != null) {
            publish(
                LocationEngineState(
                    reading = cached,
                    status = classify(cached, System.currentTimeMillis(), isFreshFix = false),
                    message = "Using a cached device reading while waiting for a fresh GNSS fix.",
                    isRefreshing = true
                )
            )
        } else {
            publish(
                _state.copy(
                    status = LocationStatus.ACQUIRING,
                    message = "Acquiring a fresh GNSS/GPS fix. Internet is not required.",
                    isRefreshing = true
                )
            )
        }

        if (!isGpsProviderEnabled()) {
            finishWithoutFreshFix(
                "GPS/location services are disabled. Enable them or enter coordinates manually."
            )
            return
        }

        val listener = locationListener
        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_INTERVAL_MILLIS,
                0f,
                listener,
                Looper.getMainLooper()
            )
            requestingUpdates = true
            val timeout = Runnable {
                if (requestingUpdates) {
                    stopUpdates()
                    finishWithoutFreshFix(
                        "No fresh GNSS fix arrived before the timeout. Any shown coordinates are marked as cached."
                    )
                }
            }
            timeoutRunnable = timeout
            mainHandler.postDelayed(timeout, ACQUISITION_TIMEOUT_MILLIS)
        } catch (_: SecurityException) {
            finishWithoutFreshFix("Location permission was revoked. Grant it and try again.")
        } catch (_: IllegalArgumentException) {
            finishWithoutFreshFix("The GPS provider is unavailable on this device.")
        }
    }

    fun clear() {
        stopUpdates()
        publish(
            LocationEngineState(
                status = if (LocationPermissions.hasAny(appContext)) {
                    LocationStatus.UNAVAILABLE
                } else {
                    LocationStatus.PERMISSION_REQUIRED
                },
                message = "No device location is currently available."
            )
        )
    }

    override fun close() {
        if (closed) return
        stopUpdates()
        closed = true
        listeners.clear()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!isValid(location)) return
            val reading = location.toReading()
            stopUpdates()
            publish(
                LocationEngineState(
                    reading = reading,
                    status = classify(reading, System.currentTimeMillis(), isFreshFix = true),
                    message = when {
                        !reading.precisePermission -> "Device-reported coordinates use approximate permission; accuracy is not verified."
                        reading.accuracyMeters == null -> "Fresh device-reported GNSS coordinates; provider accuracy was unavailable."
                        else -> "Fresh device-reported GNSS coordinates; accuracy is not verified physically."
                    },
                    isRefreshing = false
                )
            )
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                stopUpdates()
                finishWithoutFreshFix("GPS/location services were disabled before a fresh fix arrived.")
            }
        }
    }

    private fun readCachedGpsLocation(): DeviceLocationReading? {
        val location = try {
            if (!LocationPermissions.hasAny(appContext)) null
            else locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        return location?.takeIf(::isValid)?.toReading()
    }

    private fun isGpsProviderEnabled(): Boolean = try {
        locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    } catch (_: Exception) {
        false
    }

    private fun finishWithoutFreshFix(message: String) {
        val reading = _state.reading
        publish(
            _state.copy(
                status = reading?.let {
                    classify(it, System.currentTimeMillis(), isFreshFix = false)
                } ?: LocationStatus.UNAVAILABLE,
                message = message,
                isRefreshing = false
            )
        )
    }

    private fun stopUpdates() {
        if (requestingUpdates || timeoutRunnable != null) {
            try {
                locationManager?.removeUpdates(locationListener)
            } catch (_: SecurityException) {
                // Permission may have been revoked while stopping the foreground request.
            }
        }
        requestingUpdates = false
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable = null
    }

    private fun Location.toReading(): DeviceLocationReading = DeviceLocationReading(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        timestampMillis = time,
        source = EmergencyLocationSource.GNSS,
        precisePermission = LocationPermissions.hasPrecise(appContext)
    )

    private fun classify(
        reading: DeviceLocationReading,
        nowMillis: Long,
        isFreshFix: Boolean
    ): LocationStatus {
        val age = (nowMillis - reading.timestampMillis).coerceAtLeast(0L)
        return when {
            age > STALE_AFTER_MILLIS -> LocationStatus.STALE
            !isFreshFix -> LocationStatus.STALE
            !reading.precisePermission -> LocationStatus.LOW_ACCURACY
            reading.accuracyMeters != null && reading.accuracyMeters > LOW_ACCURACY_METERS -> {
                LocationStatus.LOW_ACCURACY
            }
            else -> LocationStatus.FRESH
        }
    }

    private fun isValid(location: Location): Boolean =
        location.latitude.isFinite() && location.longitude.isFinite() &&
            location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0

    private fun publish(newState: LocationEngineState) {
        _state = newState
        val snapshot = newState
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listeners.toList().forEach { it(snapshot) }
        } else {
            mainHandler.post { listeners.toList().forEach { it(snapshot) } }
        }
    }

    private companion object {
        const val LOCATION_INTERVAL_MILLIS = 1_000L
        const val ACQUISITION_TIMEOUT_MILLIS = 15_000L
        const val STALE_AFTER_MILLIS = 2 * 60 * 1_000L
        const val LOW_ACCURACY_METERS = 100f
    }
}
