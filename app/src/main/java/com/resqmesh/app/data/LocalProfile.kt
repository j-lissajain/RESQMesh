package com.resqmesh.app.data

import android.content.Context

enum class EmergencyRole(val label: String) {
    VICTIM("Victim"),
    RESPONDER("Responder"),
    VOLUNTEER("Volunteer");

    companion object {
        fun fromStoredValue(value: String?): EmergencyRole? =
            entries.firstOrNull { it.name == value }
    }
}

data class LocalProfile(
    val displayName: String,
    val role: EmergencyRole,
    val capabilities: String
)

class LocalProfileStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): LocalProfile? {
        val displayName = preferences.getString(KEY_DISPLAY_NAME, null)?.trim().orEmpty()
        val role = EmergencyRole.fromStoredValue(preferences.getString(KEY_ROLE, null))

        return if (displayName.isNotEmpty() && role != null) {
            LocalProfile(
                displayName = displayName,
                role = role,
                capabilities = preferences.getString(KEY_CAPABILITIES, "").orEmpty()
            )
        } else {
            null
        }
    }

    fun save(profile: LocalProfile) {
        preferences.edit()
            .putString(KEY_DISPLAY_NAME, profile.displayName.trim())
            .putString(KEY_ROLE, profile.role.name)
            .putString(KEY_CAPABILITIES, profile.capabilities.trim())
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "resqmesh_local_profile"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_ROLE = "role"
        const val KEY_CAPABILITIES = "capabilities"
    }
}
