package com.resqmesh.app

import com.resqmesh.app.data.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferenceTest {
    @Test
    fun storedThemeValues_roundTripAndUnknownValuesUseSystemDefault() {
        ThemePreference.entries.forEach { preference ->
            assertEquals(preference, ThemePreference.fromStoredValue(preference.name))
        }
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStoredValue("unknown"))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStoredValue(null))
    }
}
