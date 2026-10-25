package com.resqmesh.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.resqmesh.app.data.ThemePreference

private val DarkColorScheme = darkColorScheme(
    primary = EmergencyTeal,
    onPrimary = Color(0xFF00201F),
    secondary = EmergencyAmber,
    onSecondary = Color(0xFF1B1300),
    tertiary = Color(0xFFFF8A65),
    background = EmergencyBackground,
    onBackground = EmergencyText,
    surface = EmergencySurface,
    onSurface = EmergencyText,
    surfaceVariant = EmergencySurfaceVariant,
    onSurfaceVariant = EmergencyTextMuted,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006A67),
    onPrimary = Color.White,
    secondary = Color(0xFF006A67),
    onSecondary = Color.White,
    tertiary = Color(0xFF8A5000),
    background = Color(0xFFF7F9F8),
    onBackground = Color(0xFF172126),
    surface = Color.White,
    onSurface = Color(0xFF172126),
    surfaceVariant = Color(0xFFDCE5E6),
    onSurfaceVariant = Color(0xFF3F4A4D),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun RESQMeshTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
