package com.resqmesh.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resqmesh.app.bluetooth.MeshRelayController
import com.resqmesh.app.data.AssistanceResponse
import com.resqmesh.app.data.AssistanceResponseStore
import com.resqmesh.app.data.EmergencyLifecycleStatus
import com.resqmesh.app.data.EmergencyMatchingEngine
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencyRevisionRules
import com.resqmesh.app.data.EmergencyType
import com.resqmesh.app.data.LocalNodeIdentityStore
import com.resqmesh.app.data.ResourceAvailability
import com.resqmesh.app.data.ResourceCategory
import com.resqmesh.app.data.ResourceRecord
import com.resqmesh.app.data.ResourceStore
import com.resqmesh.app.data.ResponseStatus
import com.resqmesh.app.data.SafetyPreferences
import com.resqmesh.app.data.SafetyPreferencesStore
import com.resqmesh.app.data.TrustedContact
import com.resqmesh.app.data.VolunteerAvailability
import com.resqmesh.app.data.VolunteerProfile
import com.resqmesh.app.data.VolunteerProfileStore
import com.resqmesh.app.data.VolunteerSkill
import com.resqmesh.app.data.LanguagePreference
import com.resqmesh.app.ui.languageCopy
import com.resqmesh.app.data.bumpContentRevision
import java.util.UUID

private enum class CoordinationTab { VOLUNTEERS, RESOURCES, RESPONSES, SAFETY }

@Composable
fun CoordinationScreen(
    contentPadding: PaddingValues,
    emergencies: List<EmergencyReport>,
    volunteerStore: VolunteerProfileStore,
    resourceStore: ResourceStore,
    responseStore: AssistanceResponseStore,
    safetyStore: SafetyPreferencesStore,
    meshRelay: MeshRelayController,
    localNodeId: String,
    localAlias: String,
    onSafetySos: () -> Unit = {},
    languagePreference: LanguagePreference = LanguagePreference.ENGLISH
) {
    val copy = languageCopy(languagePreference)
    var tab by remember { mutableStateOf(CoordinationTab.VOLUNTEERS) }
    var volunteers by remember { mutableStateOf(volunteerStore.list()) }
    var resources by remember { mutableStateOf(resourceStore.list()) }
    var responses by remember { mutableStateOf(responseStore.list()) }
    var safety by remember { mutableStateOf(safetyStore.load()) }
    var showVolunteerEditor by remember { mutableStateOf(false) }
    var showResourceEditor by remember { mutableStateOf(false) }

    fun refresh() {
        volunteers = volunteerStore.list()
        resources = resourceStore.list()
        responses = responseStore.list()
        safety = safetyStore.load()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(copy.coordination, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "All records are local-first. Sharing is limited to appropriate coordination data over the active BLE mesh.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            CoordinationTab.values().forEach { item ->
                FilterChip(
                    selected = tab == item,
                    onClick = { tab = item },
                    label = { Text(item.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        when (tab) {
            CoordinationTab.VOLUNTEERS -> VolunteerSection(
                profiles = volunteers,
                emergencies = emergencies,
                onEdit = { showVolunteerEditor = true },
                onAvailability = { value ->
                    volunteers.firstOrNull { it.nodeId == localNodeId }?.let {
                        volunteerStore.save(it.copy(availability = value, revision = it.revision + 1, updatedAtMillis = System.currentTimeMillis()))
                        meshRelay.enqueueVolunteer(it.copy(availability = value, revision = it.revision + 1, updatedAtMillis = System.currentTimeMillis()))
                        refresh()
                    }
                },
                onOffer = { emergency, skill ->
                    val response = AssistanceResponse(
                        emergencyId = emergency.id,
                        responderNodeId = localNodeId,
                        responderAlias = localAlias,
                        assistanceType = skill,
                        message = "I can help with ${skill.label.lowercase()}.",
                        createdAtMillis = System.currentTimeMillis()
                    )
                    responseStore.save(response)
                    meshRelay.enqueueResponse(response)
                    refresh()
                }
            )
            CoordinationTab.RESOURCES -> ResourceSection(
                resources = resources,
                onAdd = { showResourceEditor = true },
                onCommit = { resource ->
                    val updated = resource.copy(availability = ResourceAvailability.PROVISIONALLY_COMMITTED, revision = resource.revision + 1, updatedAtMillis = System.currentTimeMillis())
                    resourceStore.save(updated)
                    meshRelay.enqueueResource(updated)
                    refresh()
                },
                onUnavailable = { resource ->
                    val updated = resource.copy(availability = ResourceAvailability.UNAVAILABLE, revision = resource.revision + 1, updatedAtMillis = System.currentTimeMillis())
                    resourceStore.save(updated)
                    meshRelay.enqueueResource(updated)
                    refresh()
                }
            )
            CoordinationTab.RESPONSES -> ResponseSection(
                emergencies = emergencies,
                responses = responses,
                localNodeId = localNodeId,
                onStatus = { response, status ->
                    val updated = response.copy(status = status, revision = response.revision + 1, updatedAtMillis = System.currentTimeMillis())
                    responseStore.save(updated)
                    meshRelay.enqueueResponse(updated)
                    refresh()
                }
            )
            CoordinationTab.SAFETY -> SafetySection(
                preferences = safety,
                onSave = { value -> safetyStore.save(value); refresh() },
                onSafetySos = onSafetySos
            )
        }
    }

    if (showVolunteerEditor) {
        VolunteerEditorDialog(
            existing = volunteers.firstOrNull { it.nodeId == localNodeId },
            nodeId = localNodeId,
            onDismiss = { showVolunteerEditor = false },
            onSave = { profile ->
                volunteerStore.save(profile)
                meshRelay.enqueueVolunteer(profile)
                showVolunteerEditor = false
                refresh()
            }
        )
    }
    if (showResourceEditor) {
        ResourceEditorDialog(
            nodeId = localNodeId,
            alias = localAlias,
            onDismiss = { showResourceEditor = false },
            onSave = { resource ->
                resourceStore.save(resource)
                meshRelay.enqueueResource(resource)
                showResourceEditor = false
                refresh()
            }
        )
    }
}

@Composable
private fun VolunteerSection(
    profiles: List<VolunteerProfile>,
    emergencies: List<EmergencyReport>,
    onEdit: () -> Unit,
    onAvailability: (VolunteerAvailability) -> Unit,
    onOffer: (EmergencyReport, VolunteerSkill) -> Unit
) {
    val local = profiles.firstOrNull { !it.isRemoteUnverified }
    Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Edit volunteer profile" }) {
        Text(if (local == null) "Register as volunteer" else "Edit volunteer profile")
    }
    local?.let {
        Card {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(it.alias, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Skills: ${it.skills.joinToString { skill -> skill.label }}")
                Text("Operating area: ${it.operatingArea.ifBlank { "Not provided" }}")
                Text("Self-declared information is not professional certification.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VolunteerAvailability.entries.forEach { availability ->
                        FilterChip(selected = it.availability == availability, onClick = { onAvailability(availability) }, label = { Text(availability.label) })
                    }
                }
            }
        }
    }
    Text("Nearby emergency opportunities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    val active = emergencies.filter { it.lifecycleStatus == EmergencyLifecycleStatus.ACTIVE }
    if (active.isEmpty()) Text("No active emergency requests are stored on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    active.forEach { emergency ->
        val suggestions = EmergencyMatchingEngine.volunteersFor(emergency, profiles)
        Card {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("${emergency.type.label} · ${emergency.severity.label}", fontWeight = FontWeight.Bold)
                Text(emergency.description, maxLines = 3)
                Text("Matching is a suggestion, not assignment.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val skill = suggestions.firstOrNull()?.volunteer?.skills?.firstOrNull() ?: VolunteerSkill.GENERAL_SUPPORT
                Button(onClick = { onOffer(emergency, skill) }, enabled = local != null, modifier = Modifier.fillMaxWidth()) {
                    Text(if (local == null) "Register to offer help" else "Offer ${skill.label.lowercase()}")
                }
            }
        }
    }
}

@Composable
private fun ResourceSection(resources: List<ResourceRecord>, onAdd: () -> Unit, onCommit: (ResourceRecord) -> Unit, onUnavailable: (ResourceRecord) -> Unit) {
    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Add a resource" }) { Text("Add resource") }
    if (resources.isEmpty()) Text("No resources are registered locally.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    resources.forEach { resource ->
        Card {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(resource.category.label, fontWeight = FontWeight.Bold)
                Text("${resource.quantity} ${resource.unit} · ${resource.description}")
                Text("${resource.availability.label} · ${resource.approximateArea.ifBlank { "Area not specified" }}")
                if (resource.availability == ResourceAvailability.AVAILABLE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { onCommit(resource) }, modifier = Modifier.weight(1f)) { Text("Commit") }
                        OutlinedButton(onClick = { onUnavailable(resource) }, modifier = Modifier.weight(1f)) { Text("Unavailable") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponseSection(
    emergencies: List<EmergencyReport>,
    responses: List<AssistanceResponse>,
    localNodeId: String,
    onStatus: (AssistanceResponse, ResponseStatus) -> Unit
) {
    val activeIds = emergencies.filter { it.lifecycleStatus == EmergencyLifecycleStatus.ACTIVE }.map { it.id }.toSet()
    val visible = responses.filter { it.emergencyId in activeIds || activeIds.isEmpty() }
    if (visible.isEmpty()) Text("No assistance responses are stored locally.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    visible.forEach { response ->
        Card {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("${response.responderAlias} · ${response.assistanceType.label}", fontWeight = FontWeight.Bold)
                Text(response.message.ifBlank { "No message provided." })
                Text(response.status.label)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (response.status == ResponseStatus.OFFERED) {
                        Button(onClick = { onStatus(response, ResponseStatus.ACCEPTED) }) { Text("Accept") }
                        OutlinedButton(onClick = { onStatus(response, ResponseStatus.DECLINED) }) { Text("Decline") }
                    }
                    if (response.status == ResponseStatus.ACCEPTED) Button(onClick = { onStatus(response, ResponseStatus.IN_PROGRESS) }) { Text("Start") }
                    if (response.status == ResponseStatus.IN_PROGRESS) Button(onClick = { onStatus(response, ResponseStatus.COMPLETED) }) { Text("Complete") }
                    if (response.responderNodeId == localNodeId && response.status in setOf(ResponseStatus.OFFERED, ResponseStatus.ACCEPTED, ResponseStatus.IN_PROGRESS)) {
                        OutlinedButton(onClick = { onStatus(response, ResponseStatus.WITHDRAWN) }) { Text("Withdraw") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SafetySection(preferences: SafetyPreferences, onSave: (SafetyPreferences) -> Unit, onSafetySos: () -> Unit) {
    var alias by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    Card {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Personal safety", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Safety alerts are offline records until a BLE peer acknowledges transport. Trusted contacts are never broadcast automatically.")
            FilterChip(
                selected = preferences.discreetSosEnabled,
                onClick = { onSave(preferences.copy(discreetSosEnabled = !preferences.discreetSosEnabled)) },
                label = { Text(if (preferences.discreetSosEnabled) "Discreet SOS enabled" else "Enable discreet SOS") }
            )
            Button(onClick = onSafetySos, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Create personal safety SOS" }) {
                Text("Personal safety SOS")
            }
            FilterChip(
                selected = preferences.sharePreciseLocationByDefault,
                onClick = { onSave(preferences.copy(sharePreciseLocationByDefault = !preferences.sharePreciseLocationByDefault)) },
                label = { Text(if (preferences.sharePreciseLocationByDefault) "Precise location by default" else "Use privacy-preserving location by default") }
            )
            OutlinedTextField(value = alias, onValueChange = { alias = it }, label = { Text("Trusted contact alias") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = contact, onValueChange = { contact = it }, label = { Text("Contact information") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (alias.isNotBlank() && contact.isNotBlank()) {
                        onSave(preferences.copy(contacts = preferences.contacts + TrustedContact(alias = alias.trim(), contactInfo = contact.trim())))
                        alias = ""
                        contact = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save trusted contact") }
            preferences.contacts.forEach { Text("${it.alias}: ${it.contactInfo}") }
            Text("Child safety and personal safety reports use the same revision-aware emergency transport and should not include unnecessary identifying details.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VolunteerEditorDialog(existing: VolunteerProfile?, nodeId: String, onDismiss: () -> Unit, onSave: (VolunteerProfile) -> Unit) {
    var alias by remember { mutableStateOf(existing?.alias.orEmpty()) }
    var area by remember { mutableStateOf(existing?.operatingArea.orEmpty()) }
    var capabilities by remember { mutableStateOf(existing?.assistanceCapabilities.orEmpty()) }
    var resourceCapabilities by remember { mutableStateOf(existing?.resourceCapabilities.orEmpty()) }
    var contactInfo by remember { mutableStateOf(existing?.contactInfo.orEmpty()) }
    var skills by remember { mutableStateOf(existing?.skills ?: setOf(VolunteerSkill.GENERAL_SUPPORT)) }
    var transport by remember { mutableStateOf(existing?.transportAvailable ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Volunteer profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = alias, onValueChange = { alias = it }, label = { Text("Alias") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("Approximate operating area") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = capabilities, onValueChange = { capabilities = it }, label = { Text("Assistance capabilities") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = resourceCapabilities, onValueChange = { resourceCapabilities = it }, label = { Text("Resource capabilities") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = contactInfo, onValueChange = { contactInfo = it }, label = { Text("Optional contact information") }, modifier = Modifier.fillMaxWidth())
                Text("Skills")
                VolunteerSkill.entries.forEach { skill -> FilterChip(selected = skill in skills, onClick = { skills = if (skill in skills) skills - skill else skills + skill }, label = { Text(skill.label) }, modifier = Modifier.fillMaxWidth()) }
                FilterChip(selected = transport, onClick = { transport = !transport }, label = { Text("Transport available") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { if (alias.isNotBlank() && skills.isNotEmpty()) onSave(VolunteerProfile(id = existing?.id ?: UUID.randomUUID().toString(), nodeId = nodeId, alias = alias.trim(), skills = skills, assistanceCapabilities = capabilities.trim(), operatingArea = area.trim(), transportAvailable = transport, resourceCapabilities = resourceCapabilities.trim(), contactInfo = contactInfo.trim().takeIf { it.isNotBlank() }, revision = (existing?.revision ?: 0) + 1)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ResourceEditorDialog(nodeId: String, alias: String, onDismiss: () -> Unit, onSave: (ResourceRecord) -> Unit) {
    var category by remember { mutableStateOf(ResourceCategory.DRINKING_WATER) }
    var description by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("unit") }
    var area by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register resource") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ResourceCategory.entries.forEach { item -> FilterChip(selected = item == category, onClick = { category = item }, label = { Text(item.label) }, modifier = Modifier.fillMaxWidth()) }
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("Quantity") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("Approximate area") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { quantity.toDoubleOrNull()?.takeIf { it >= 0 }?.let { amount -> if (description.isNotBlank() && unit.isNotBlank()) onSave(ResourceRecord(providerNodeId = nodeId, providerAlias = alias, category = category, description = description.trim(), quantity = amount, unit = unit.trim(), approximateArea = area.trim())) } }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
