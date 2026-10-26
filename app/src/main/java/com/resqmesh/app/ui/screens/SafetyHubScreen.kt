package com.resqmesh.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resqmesh.app.data.SafetyPreferences
import com.resqmesh.app.data.SafetyPreferencesStore
import com.resqmesh.app.data.SafetyStateStore
import com.resqmesh.app.data.SafetyTimerStatus
import com.resqmesh.app.data.SafetyTimerState
import com.resqmesh.app.data.SafetyTimerLogic
import com.resqmesh.app.data.SafetyTimerScheduler
import com.resqmesh.app.data.TrustedContact
import com.resqmesh.app.R
import com.resqmesh.app.ui.localizedText
import kotlinx.coroutines.delay

@Composable
fun SafetyHubScreen(
    contentPadding: PaddingValues,
    preferencesStore: SafetyPreferencesStore,
    stateStore: SafetyStateStore,
    onSafetySos: () -> Unit
) {
    var preferences by remember { mutableStateOf(preferencesStore.load()) }
    var timer by remember { mutableStateOf(stateStore.loadTimer()) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var label by remember { mutableStateOf(timer.label) }
    var minutes by remember { mutableStateOf("30") }
    var alias by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var editingContactId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun persistTimer(updated: SafetyTimerState) {
        timer = updated
        stateStore.saveTimer(updated)
        if (updated.status == SafetyTimerStatus.ACTIVE && updated.dueAtMillis != null) {
            SafetyTimerScheduler.schedule(context, updated.dueAtMillis)
        } else {
            SafetyTimerScheduler.cancel(context)
        }
    }

    LaunchedEffect(timer.status, timer.dueAtMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            val refreshed = stateStore.loadTimer(nowMillis)
            if (refreshed != timer) timer = refreshed
            delay(1_000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
         Text(stringResource(R.string.safety_hub), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.safety_hub_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onSafetySos,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Create a personal safety SOS" }
         ) { Text(stringResource(R.string.personal_safety_sos)) }
        Card {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                 Text(stringResource(R.string.discreet_sos_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                 Text(stringResource(R.string.discreet_sos_description))
                FilterChip(
                    selected = preferences.discreetSosEnabled,
                    onClick = {
                        preferences = preferences.copy(discreetSosEnabled = !preferences.discreetSosEnabled)
                        preferencesStore.save(preferences)
                    },
                     label = { Text(if (preferences.discreetSosEnabled) stringResource(R.string.enabled) else stringResource(R.string.enable_discreet_action)) }
                )
            }
        }
        Card {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                 Text(stringResource(R.string.safety_timer_checkin), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                 Text(stringResource(R.string.timer_status, timer.status.localizedText()))
                timer.dueAtMillis?.let {
                    val remaining = (it - nowMillis).coerceAtLeast(0L) / 1_000L
                     Text(if (timer.status == SafetyTimerStatus.ACTIVE) stringResource(R.string.time_remaining, remaining / 60, remaining % 60) else stringResource(R.string.checkin_time_reached))
                }
                 OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringResource(R.string.journey_label)) }, modifier = Modifier.fillMaxWidth())
                 OutlinedTextField(value = minutes, onValueChange = { minutes = it }, label = { Text(stringResource(R.string.checkin_interval)) }, modifier = Modifier.fillMaxWidth())
                FilterChip(
                    selected = timer.autoCreateSafetySosOnMiss,
                    onClick = {
                         persistTimer(timer.copy(autoCreateSafetySosOnMiss = !timer.autoCreateSafetySosOnMiss))
                     },
                     label = { Text(stringResource(R.string.ask_before_sos)) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val duration = minutes.toLongOrNull()?.coerceIn(1L, 24L * 60L) ?: 30L
                            val now = System.currentTimeMillis()
                             persistTimer(
                                 SafetyTimerLogic.start(
                                     label = label,
                                     durationMillis = duration * 60_000L,
                                     nowMillis = now,
                                     autoCreateSafetySosOnMiss = timer.autoCreateSafetySosOnMiss
                                 )
                             )
                        },
                        modifier = Modifier.weight(1f)
                     ) { Text(stringResource(R.string.start)) }
                    OutlinedButton(
                        onClick = {
                             persistTimer(timer.copy(status = SafetyTimerStatus.COMPLETED))
                        },
                        enabled = timer.status == SafetyTimerStatus.ACTIVE || timer.status == SafetyTimerStatus.DUE,
                        modifier = Modifier.weight(1f)
                     ) { Text(stringResource(R.string.i_am_safe)) }
                }
                if (timer.status == SafetyTimerStatus.DUE || timer.status == SafetyTimerStatus.MISSED) {
                     Text(stringResource(R.string.missed_checkin_note), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                     Button(onClick = onSafetySos, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.create_safety_sos)) }
                }
            }
        }
        Card {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                 Text(stringResource(R.string.trusted_contacts), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                 Text(stringResource(R.string.contacts_local_only))
                  preferences.contacts.forEach { savedContact ->
                      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                          Text("${savedContact.alias}: ${savedContact.contactInfo}", modifier = Modifier.weight(1f))
                          TextButton(onClick = {
                              alias = savedContact.alias
                              contact = savedContact.contactInfo
                              editingContactId = savedContact.id
                          }) { Text(stringResource(R.string.edit_contact)) }
                          OutlinedButton(onClick = {
                              preferences = preferences.copy(contacts = preferences.contacts.filterNot { it.id == savedContact.id })
                              preferencesStore.save(preferences)
                              if (editingContactId == savedContact.id) {
                                  editingContactId = null
                                  alias = ""
                                  contact = ""
                              }
                         }) { Text(stringResource(R.string.remove)) }
                     }
                 }
                 OutlinedTextField(value = alias, onValueChange = { alias = it }, label = { Text(stringResource(R.string.contact_alias)) }, modifier = Modifier.fillMaxWidth())
                 OutlinedTextField(value = contact, onValueChange = { contact = it }, label = { Text(stringResource(R.string.optional_phone_detail)) }, modifier = Modifier.fillMaxWidth())
                 Button(onClick = {
                     if (alias.isNotBlank() && contact.isNotBlank()) {
                         val updatedContact = TrustedContact(
                             id = editingContactId ?: java.util.UUID.randomUUID().toString(),
                             alias = alias.trim(),
                             contactInfo = contact.trim()
                         )
                         val updatedContacts = if (editingContactId == null) {
                             preferences.contacts + updatedContact
                         } else {
                             preferences.contacts.map { if (it.id == editingContactId) updatedContact else it }
                         }
                         preferences = preferences.copy(contacts = updatedContacts)
                         preferencesStore.save(preferences)
                         alias = ""
                         contact = ""
                         editingContactId = null
                     }
                 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (editingContactId == null) R.string.add_contact else R.string.save)) }
            }
        }
    }
}
