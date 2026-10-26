package com.resqmesh.app.data

import android.content.Context

enum class LanguagePreference(val tag: String, val displayLabel: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिंदी"),
    MARATHI("mr", "मराठी");

    companion object {
        fun fromStoredValue(value: String?): LanguagePreference = entries.firstOrNull { it.tag == value } ?: ENGLISH
    }
}

class LanguagePreferenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("resqmesh_preferences", Context.MODE_PRIVATE)

    fun load(): LanguagePreference = LanguagePreference.fromStoredValue(preferences.getString("language", "en"))

    fun save(language: LanguagePreference) {
        preferences.edit().putString("language", language.tag).commit()
    }
}
