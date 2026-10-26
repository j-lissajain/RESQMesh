package com.resqmesh.app.data

import android.content.Context

enum class SafetyTimerStatus { INACTIVE, ACTIVE, DUE, MISSED, COMPLETED }

data class SafetyTimerState(
    val status: SafetyTimerStatus = SafetyTimerStatus.INACTIVE,
    val label: String = "",
    val startedAtMillis: Long? = null,
    val dueAtMillis: Long? = null,
    val autoCreateSafetySosOnMiss: Boolean = false
)

object SafetyTimerLogic {
    fun refresh(state: SafetyTimerState, nowMillis: Long): SafetyTimerState =
        if (state.status == SafetyTimerStatus.ACTIVE && state.dueAtMillis != null && state.dueAtMillis <= nowMillis) {
            state.copy(status = SafetyTimerStatus.DUE)
        } else state

    fun start(label: String, durationMillis: Long, nowMillis: Long, autoCreateSafetySosOnMiss: Boolean): SafetyTimerState =
        SafetyTimerState(
            status = SafetyTimerStatus.ACTIVE,
            label = label.trim(),
            startedAtMillis = nowMillis,
            dueAtMillis = nowMillis + durationMillis.coerceAtLeast(60_000L),
            autoCreateSafetySosOnMiss = autoCreateSafetySosOnMiss
        )
}

class SafetyStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("resqmesh_safety_state", Context.MODE_PRIVATE)

    fun loadTimer(nowMillis: Long = System.currentTimeMillis()): SafetyTimerState {
        val status = runCatching {
            SafetyTimerStatus.valueOf(preferences.getString("status", SafetyTimerStatus.INACTIVE.name).orEmpty())
        }.getOrDefault(SafetyTimerStatus.INACTIVE)
        val due = preferences.getLong("due_at", 0L).takeIf { it > 0L }
        val effectiveStatus = SafetyTimerLogic.refresh(
            SafetyTimerState(status = status, dueAtMillis = due),
            nowMillis
        ).status
        if (effectiveStatus != status) saveTimer(
            SafetyTimerState(
                status = effectiveStatus,
                label = preferences.getString("label", "").orEmpty(),
                startedAtMillis = preferences.getLong("started_at", 0L).takeIf { it > 0L },
                dueAtMillis = due,
                autoCreateSafetySosOnMiss = preferences.getBoolean("auto_sos", false)
            )
        )
        return SafetyTimerState(
            status = effectiveStatus,
            label = preferences.getString("label", "").orEmpty(),
            startedAtMillis = preferences.getLong("started_at", 0L).takeIf { it > 0L },
            dueAtMillis = due,
            autoCreateSafetySosOnMiss = preferences.getBoolean("auto_sos", false)
        )
    }

    fun saveTimer(state: SafetyTimerState) {
        preferences.edit()
            .putString("status", state.status.name)
            .putString("label", state.label)
            .putLong("started_at", state.startedAtMillis ?: 0L)
            .putLong("due_at", state.dueAtMillis ?: 0L)
            .putBoolean("auto_sos", state.autoCreateSafetySosOnMiss)
            .apply()
    }
}
