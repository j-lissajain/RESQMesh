package com.resqmesh.app.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class AccessibilityFeedback(context: Context) : AutoCloseable {
    private var ready = false
    private val tts = TextToSpeech(context.applicationContext) { status -> ready = status == TextToSpeech.SUCCESS }

    fun speak(text: String, languageTag: String, enabled: Boolean) {
        if (!enabled || !ready) return
        val locale = Locale.forLanguageTag(languageTag)
        val languageStatus = tts.setLanguage(locale)
        if (languageStatus == TextToSpeech.LANG_MISSING_DATA || languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "resqmesh-status")
    }

    override fun close() {
        tts.stop()
        tts.shutdown()
    }
}
