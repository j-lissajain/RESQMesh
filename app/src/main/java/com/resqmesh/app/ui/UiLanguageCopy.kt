package com.resqmesh.app.ui

import com.resqmesh.app.data.LanguagePreference

data class UiLanguageCopy(
    val appTagline: String,
    val instantSos: String,
    val coordination: String,
    val volunteer: String,
    val resources: String,
    val safety: String
)

fun languageCopy(language: LanguagePreference): UiLanguageCopy = when (language) {
    LanguagePreference.ENGLISH -> UiLanguageCopy(
        "Offline emergency coordination", "INSTANT SOS", "Volunteer and resource coordination", "Volunteers", "Resources", "Safety"
    )
    LanguagePreference.HINDI -> UiLanguageCopy(
        "ऑफ़लाइन आपातकालीन समन्वय", "तुरंत SOS", "स्वयंसेवक और संसाधन समन्वय", "स्वयंसेवक", "संसाधन", "सुरक्षा"
    )
    LanguagePreference.MARATHI -> UiLanguageCopy(
        "ऑफलाइन आपत्कालीन समन्वय", "त्वरित SOS", "स्वयंसेवक आणि संसाधन समन्वय", "स्वयंसेवक", "संसाधने", "सुरक्षा"
    )
}
