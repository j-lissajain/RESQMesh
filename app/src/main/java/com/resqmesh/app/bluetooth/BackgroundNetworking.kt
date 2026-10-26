package com.resqmesh.app.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.resqmesh.app.R

private const val BACKGROUND_PREFS = "resqmesh_background_networking"
private const val ENABLED_KEY = "enabled"
private const val CHANNEL_ID = "resqmesh_background_networking"
private const val NOTIFICATION_ID = 7401

class BackgroundNetworkingStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(BACKGROUND_PREFS, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean(ENABLED_KEY, false)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(ENABLED_KEY, enabled).apply()
    }
}

object BleRuntimeRegistry {
    @Volatile private var messagingManager: BleMessagingManager? = null
    @Volatile private var discoveryManager: BleDiscoveryManager? = null

    @Synchronized
    fun messaging(context: Context): BleMessagingManager =
        messagingManager ?: BleMessagingManager(context.applicationContext).also { messagingManager = it }

    @Synchronized
    fun discovery(context: Context): BleDiscoveryManager =
        discoveryManager ?: BleDiscoveryManager(context.applicationContext).also { discoveryManager = it }

    fun close() {
        messagingManager?.close()
        discoveryManager?.close()
        messagingManager = null
        discoveryManager = null
    }
}

object BackgroundNetworkingController {
    fun start(context: Context): Boolean {
        val appContext = context.applicationContext
        BackgroundNetworkingStore(appContext).setEnabled(true)
        val intent = Intent(appContext, ResqMeshBleForegroundService::class.java)
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            true
        }.getOrDefault(false)
        if (!started) BackgroundNetworkingStore(appContext).setEnabled(false)
        return started
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        BackgroundNetworkingStore(appContext).setEnabled(false)
        appContext.stopService(Intent(appContext, ResqMeshBleForegroundService::class.java))
    }
}

class ResqMeshBleForegroundService : Service() {
    private var discoveryListenerRemove: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        try {
            startForeground(NOTIFICATION_ID, notification())
        } catch (_: SecurityException) {
            // Android may reject foreground promotion when notification or
            // foreground-service permissions are unavailable.
            stopSelf()
            return
        }

        val discovery = BleRuntimeRegistry.discovery(this)
        val messaging = BleRuntimeRegistry.messaging(this)
        discoveryListenerRemove = discovery.addListener { state ->
            messaging.setLocalSessionId(state.localAnonymousId)
            if (state.localAnonymousId != null) messaging.startServer()
        }
        if (BackgroundNetworkingStore(this).isEnabled()) {
            discovery.startDiscovery()
            messaging.startServer()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BackgroundNetworkingStore(this).isEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        discoveryListenerRemove?.invoke()
        discoveryListenerRemove = null
        BleRuntimeRegistry.discovery(this).stopDiscovery()
        BleRuntimeRegistry.messaging(this).stopSession()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                 getString(R.string.background_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                 description = getString(R.string.background_channel_description)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.relay_active_notification))
        .setContentText(getString(R.string.relay_notification_text))
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()
}
