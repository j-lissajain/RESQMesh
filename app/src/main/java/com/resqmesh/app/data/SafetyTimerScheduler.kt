package com.resqmesh.app.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.resqmesh.app.MainActivity
import com.resqmesh.app.R

private const val SAFETY_TIMER_REQUEST_CODE = 8412
private const val SAFETY_TIMER_CHANNEL = "resqmesh_safety_timer"

object SafetyTimerScheduler {
    fun schedule(context: Context, dueAtMillis: Long) {
        if (dueAtMillis <= System.currentTimeMillis()) return
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                dueAtMillis,
                pendingIntent(context)
            )
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context.applicationContext,
        SAFETY_TIMER_REQUEST_CODE,
        Intent(context, SafetyTimerAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

class SafetyTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val store = SafetyStateStore(context)
        val state = store.loadTimer()
        if (state.status != SafetyTimerStatus.ACTIVE && state.status != SafetyTimerStatus.DUE) return

        store.saveTimer(state.copy(status = SafetyTimerStatus.MISSED))
        ensureChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            SAFETY_TIMER_REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationManagerCompatBridge.notify(
            context,
            NotificationCompat.Builder(context, SAFETY_TIMER_CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.safety_timer_notification_title))
                .setContentText(context.getString(R.string.safety_timer_notification_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.safety_timer_notification_text)))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(
                    SAFETY_TIMER_CHANNEL,
                    context.getString(R.string.safety_timer_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.safety_timer_channel_description)
                }
            )
        }
    }
}

private object NotificationManagerCompatBridge {
    fun notify(context: Context, notification: android.app.Notification) {
        context.getSystemService(NotificationManager::class.java)?.notify(SAFETY_TIMER_REQUEST_CODE, notification)
    }
}
