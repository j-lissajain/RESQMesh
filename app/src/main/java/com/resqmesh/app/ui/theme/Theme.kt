package com.resqmesh.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = EmergencyAmber,
    onPrimary = Color(0xFF1B1300),
    secondary = EmergencyTeal,
    onSecondary = Color(0xFF00201F),
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

@Composable
fun RESQMeshTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
