package com.resqmesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.resqmesh.app.data.EmergencyRole
import com.resqmesh.app.data.LocalProfile
import com.resqmesh.app.ui.theme.EmergencyAmber
import com.resqmesh.app.ui.theme.EmergencyBackground

@Composable
fun ProfileSetupScreen(
    existingProfile: LocalProfile?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSave: (LocalProfile) -> Unit
) {
    var displayName by remember(existingProfile) {
        mutableStateOf(existingProfile?.displayName.orEmpty())
    }
    var selectedRole by remember(existingProfile) {
        mutableStateOf(existingProfile?.role)
    }
    var capabilities by remember(existingProfile) {
        mutableStateOf(existingProfile?.capabilities.orEmpty())
    }
    var submitted by remember { mutableStateOf(false) }

    val displayNameError = submitted && displayName.trim().isEmpty()
    val roleError = submitted && selectedRole == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EmergencyBackground)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(text = "Back", color = EmergencyAmber)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (existingProfile == null) "Create your local profile" else "Edit your local profile",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This helps nearby people understand how you want to participate in the prototype.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(20.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Important: self-declared information",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Your name, role, and capabilities are not verified authentication or proof of professional qualifications. Do not treat a selection here as a credential or a trust score.",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Display name") },
            placeholder = { Text("How should others see you?") },
            singleLine = true,
            isError = displayNameError,
            supportingText = if (displayNameError) {
                { Text("Enter a display name to continue.") }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words
            )
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Choose a role",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "This is a local, self-described role and is not verified.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EmergencyRole.entries.forEach { role ->
                FilterChip(
                    selected = selectedRole == role,
                    onClick = { selectedRole = role },
                    label = { Text(role.label) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (roleError) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Choose a role to continue.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = capabilities,
            onValueChange = { capabilities = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Capabilities (optional)") },
            placeholder = { Text("e.g. first aid, transport, translation") },
            minLines = 2,
            maxLines = 3,
            supportingText = {
                Text("Optional and self-declared. Separate multiple capabilities with commas.")
            }
        )

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = {
                submitted = true
                val role = selectedRole
                if (displayName.trim().isNotEmpty() && role != null) {
                    onSave(
                        LocalProfile(
                            displayName = displayName.trim(),
                            role = role,
                            capabilities = capabilities.trim()
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = EmergencyAmber,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            Text(
                text = if (existingProfile == null) "Continue" else "Save Profile",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Only this device's local profile is saved in this milestone. No account or cloud sync is used.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
