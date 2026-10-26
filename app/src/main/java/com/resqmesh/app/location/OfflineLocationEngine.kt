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
import com.resqmesh.app.R

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
    val message: String = "",
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
    private var _state = LocationEngineState(message = appContext.getString(R.string.location_permission_required))

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
                    message = appContext.getString(R.string.location_allow_foreground),
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
                     message = appContext.getString(R.string.location_cached),
                    isRefreshing = true
                )
            )
        } else {
            publish(
                _state.copy(
                    status = LocationStatus.ACQUIRING,
                     message = appContext.getString(R.string.location_acquiring),
                    isRefreshing = true
                )
            )
        }

        if (!isGpsProviderEnabled()) {
            finishWithoutFreshFix(
                appContext.getString(R.string.gps_disabled)
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
                        appContext.getString(R.string.location_no_fresh_fix)
                    )
                }
            }
            timeoutRunnable = timeout
            mainHandler.postDelayed(timeout, ACQUISITION_TIMEOUT_MILLIS)
        } catch (_: SecurityException) {
            finishWithoutFreshFix(appContext.getString(R.string.location_permission_revoked))
        } catch (_: IllegalArgumentException) {
            finishWithoutFreshFix(appContext.getString(R.string.gps_unavailable))
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
                message = appContext.getString(R.string.location_none)
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
                        !reading.precisePermission -> appContext.getString(R.string.location_approximate)
                        reading.accuracyMeters == null -> appContext.getString(R.string.location_accuracy_unavailable)
                        else -> appContext.getString(R.string.location_fresh_unverified)
                    },
                    isRefreshing = false
                )
            )
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                stopUpdates()
                finishWithoutFreshFix(appContext.getString(R.string.gps_disabled_before_fix))
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
