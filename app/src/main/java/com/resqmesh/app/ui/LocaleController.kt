package com.resqmesh.app.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.resqmesh.app.data.LanguagePreference
import java.util.Locale

object LocaleController {
    fun localizedContext(context: Context, preference: LanguagePreference): Context {
        val locale = Locale.forLanguageTag(preference.tag)
        val configuration = Configuration(context.resources.configuration).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLocale(locale)
            } else {
                @Suppress("DEPRECATION")
                this.locale = locale
            }
        }
        return context.createConfigurationContext(configuration)
    }

    fun apply(context: Context, preference: LanguagePreference) {
        val locale = Locale.forLanguageTag(preference.tag)
        Locale.setDefault(locale)
        val resources = context.resources
        val configuration = Configuration(resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }
}
