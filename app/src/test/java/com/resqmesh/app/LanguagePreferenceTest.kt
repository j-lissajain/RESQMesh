package com.resqmesh.app

import com.resqmesh.app.data.LanguagePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguagePreferenceTest {
    @Test
    fun unknownLanguageFallsBackToEnglish() {
        assertEquals(LanguagePreference.ENGLISH, LanguagePreference.fromStoredValue("unknown"))
        assertEquals(LanguagePreference.HINDI, LanguagePreference.fromStoredValue("hi"))
        assertEquals(LanguagePreference.MARATHI, LanguagePreference.fromStoredValue("mr"))
    }
}
