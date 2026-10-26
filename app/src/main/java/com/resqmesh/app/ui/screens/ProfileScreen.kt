package com.resqmesh.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.resqmesh.app.R
import com.resqmesh.app.data.LocalProfile
import com.resqmesh.app.data.ThemePreference
import com.resqmesh.app.data.LanguagePreference
import com.resqmesh.app.data.AccessibilityPreferences
import com.resqmesh.app.ui.localizedText

@Composable
fun ProfileScreen(
    profile: LocalProfile,
    themePreference: ThemePreference,
    contentPadding: PaddingValues,
    onEditProfile: () -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    languagePreference: LanguagePreference,
    onLanguageChange: (LanguagePreference) -> Unit,
    accessibilityPreferences: AccessibilityPreferences,
    onAccessibilityChange: (AccessibilityPreferences) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.profile), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.role_value, profile.role.localizedText()))
                if (profile.capabilities.isNotBlank()) Text(stringResource(R.string.capabilities_value, profile.capabilities))
                TextButton(onClick = onEditProfile) { Text(stringResource(R.string.edit_profile)) }
            }
        }
        Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.theme_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ThemePreference.entries.forEach { option ->
                FilterChip(
                    selected = themePreference == option,
                    onClick = { onThemeChange(option) },
                    label = { Text(option.localizedText()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(stringResource(R.string.accessibility_assistance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.accessibility_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(
            accessibilityPreferences.highContrast to R.string.high_contrast,
            accessibilityPreferences.largeText to R.string.large_text,
            accessibilityPreferences.simplifiedEmergencyMode to R.string.simplified_mode,
            accessibilityPreferences.spokenFeedback to R.string.spoken_feedback,
            accessibilityPreferences.reducedMotion to R.string.reduced_motion
        ).forEach { (selected, label) ->
            FilterChip(
                selected = selected,
                onClick = {
                    onAccessibilityChange(
                        when (label) {
                            R.string.high_contrast -> accessibilityPreferences.copy(highContrast = !selected)
                            R.string.large_text -> accessibilityPreferences.copy(largeText = !selected)
                            R.string.simplified_mode -> accessibilityPreferences.copy(simplifiedEmergencyMode = !selected)
                            R.string.spoken_feedback -> accessibilityPreferences.copy(spokenFeedback = !selected)
                            else -> accessibilityPreferences.copy(reducedMotion = !selected)
                        }
                    )
                },
                label = { Text(stringResource(label)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.language_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            LanguagePreference.entries.forEach { option ->
                FilterChip(
                    selected = languagePreference == option,
                    onClick = { onLanguageChange(option) },
                    label = { Text(option.displayLabel) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            stringResource(R.string.local_data_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
