package com.resqmesh.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.resqmesh.app.data.LocalProfile
import com.resqmesh.app.data.ThemePreference

@Composable
fun ProfileScreen(
    profile: LocalProfile,
    themePreference: ThemePreference,
    contentPadding: PaddingValues,
    onEditProfile: () -> Unit,
    onThemeChange: (ThemePreference) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Role: ${profile.role.label}")
                if (profile.capabilities.isNotBlank()) Text("Capabilities: ${profile.capabilities}")
                TextButton(onClick = onEditProfile) { Text("Edit profile") }
            }
        }
        Text("Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Choose how RESQMesh should appear on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ThemePreference.entries.forEach { option ->
                FilterChip(
                    selected = themePreference == option,
                    onClick = { onThemeChange(option) },
                    label = { Text(option.label) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            "Profiles, emergencies, theme preferences, and map data remain local to this device in this milestone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
