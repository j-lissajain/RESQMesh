package com.resqmesh.app.data

import android.content.Context

data class AccessibilityPreferences(
    val highContrast: Boolean = false,
    val largeText: Boolean = false,
    val simplifiedEmergencyMode: Boolean = false,
    val spokenFeedback: Boolean = false,
    val reducedMotion: Boolean = false
)

class AccessibilityPreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("resqmesh_accessibility", Context.MODE_PRIVATE)

    fun load(): AccessibilityPreferences = AccessibilityPreferences(
        highContrast = preferences.getBoolean("high_contrast", false),
        largeText = preferences.getBoolean("large_text", false),
        simplifiedEmergencyMode = preferences.getBoolean("simplified_emergency", false),
        spokenFeedback = preferences.getBoolean("spoken_feedback", false),
        reducedMotion = preferences.getBoolean("reduced_motion", false)
    )

    fun save(value: AccessibilityPreferences) {
        preferences.edit()
            .putBoolean("high_contrast", value.highContrast)
            .putBoolean("large_text", value.largeText)
            .putBoolean("simplified_emergency", value.simplifiedEmergencyMode)
            .putBoolean("spoken_feedback", value.spokenFeedback)
            .putBoolean("reduced_motion", value.reducedMotion)
            .apply()
    }
}
