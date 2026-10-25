package com.resqmesh.app.data

import android.content.Context

enum class ThemePreference(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromStoredValue(value: String?): ThemePreference =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

class ThemePreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): ThemePreference = ThemePreference.fromStoredValue(
        preferences.getString(KEY_THEME, ThemePreference.SYSTEM.name)
    )

    fun save(preference: ThemePreference) {
        preferences.edit().putString(KEY_THEME, preference.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "resqmesh_preferences"
        const val KEY_THEME = "theme_preference"
    }
}
