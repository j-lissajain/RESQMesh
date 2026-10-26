package com.resqmesh.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.resqmesh.app.R
import com.resqmesh.app.bluetooth.MessageDeliveryState
import com.resqmesh.app.data.EmergencyLifecycleStatus
import com.resqmesh.app.data.EmergencyLocationSource
import com.resqmesh.app.data.EmergencyReportStatus
import com.resqmesh.app.data.EmergencyRole
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
import com.resqmesh.app.data.ResourceAvailability
import com.resqmesh.app.data.ResourceCategory
import com.resqmesh.app.data.ResponseStatus
import com.resqmesh.app.data.SafetyTimerStatus
import com.resqmesh.app.data.ThemePreference
import com.resqmesh.app.data.VolunteerAvailability
import com.resqmesh.app.data.VolunteerSkill

@Composable
fun EmergencyType.localizedText(): String = stringResource(
    when (this) {
        EmergencyType.MEDICAL -> R.string.type_medical
        EmergencyType.FLOOD_RESCUE -> R.string.type_flood_rescue
        EmergencyType.FIRE -> R.string.type_fire
        EmergencyType.TRAPPED_PERSON -> R.string.type_trapped_person
        EmergencyType.FOOD_WATER -> R.string.type_food_water
        EmergencyType.SHELTER -> R.string.type_shelter
        EmergencyType.PERSONAL_SAFETY -> R.string.type_personal_safety
        EmergencyType.HARASSMENT -> R.string.type_harassment
        EmergencyType.UNSAFE_TRAVEL -> R.string.type_unsafe_travel
        EmergencyType.CHILD_SAFETY -> R.string.type_child_safety
        EmergencyType.MISSING_PERSON -> R.string.type_missing_person
        EmergencyType.OTHER -> R.string.type_other
    }
)

@Composable
fun EmergencySeverity.localizedText(): String = stringResource(
    when (this) {
        EmergencySeverity.CRITICAL -> R.string.severity_critical
        EmergencySeverity.HIGH -> R.string.severity_high
        EmergencySeverity.MEDIUM -> R.string.severity_medium
        EmergencySeverity.LOW -> R.string.severity_low
    }
)

@Composable
fun VolunteerSkill.localizedText(): String = stringResource(
    when (this) {
        VolunteerSkill.FIRST_AID -> R.string.skill_first_aid
        VolunteerSkill.MEDICAL_ASSISTANCE -> R.string.skill_medical_assistance
        VolunteerSkill.TRANSPORTATION -> R.string.skill_transportation
        VolunteerSkill.FOOD_DISTRIBUTION -> R.string.skill_food_distribution
        VolunteerSkill.WATER_DISTRIBUTION -> R.string.skill_water_distribution
        VolunteerSkill.SHELTER_ASSISTANCE -> R.string.skill_shelter_assistance
        VolunteerSkill.SEARCH_ASSISTANCE -> R.string.skill_search_assistance
        VolunteerSkill.GENERAL_SUPPORT -> R.string.skill_general_support
    }
)

@Composable
fun VolunteerAvailability.localizedText(): String = stringResource(
    when (this) {
        VolunteerAvailability.AVAILABLE -> R.string.availability_available
        VolunteerAvailability.BUSY -> R.string.availability_busy
        VolunteerAvailability.UNAVAILABLE -> R.string.availability_unavailable
    }
)

@Composable
fun ResourceCategory.localizedText(): String = stringResource(
    when (this) {
        ResourceCategory.DRINKING_WATER -> R.string.resource_drinking_water
        ResourceCategory.FOOD -> R.string.resource_food
        ResourceCategory.MEDICAL_SUPPLIES -> R.string.resource_medical_supplies
        ResourceCategory.FIRST_AID_SUPPLIES -> R.string.resource_first_aid_supplies
        ResourceCategory.TEMPORARY_SHELTER -> R.string.resource_temporary_shelter
        ResourceCategory.TRANSPORTATION -> R.string.resource_transportation
        ResourceCategory.CLOTHING -> R.string.resource_clothing
        ResourceCategory.BLANKETS -> R.string.resource_blankets
        ResourceCategory.RESCUE_EQUIPMENT -> R.string.resource_rescue_equipment
        ResourceCategory.GENERAL_ASSISTANCE -> R.string.resource_general_assistance
    }
)

@Composable
fun ResourceAvailability.localizedText(): String = stringResource(
    when (this) {
        ResourceAvailability.AVAILABLE -> R.string.availability_available
        ResourceAvailability.PROVISIONALLY_COMMITTED -> R.string.resource_provisionally_committed
        ResourceAvailability.UNAVAILABLE -> R.string.availability_unavailable
    }
)

@Composable
fun ResponseStatus.localizedText(): String = stringResource(
    when (this) {
        ResponseStatus.OFFERED -> R.string.response_offered
        ResponseStatus.ACCEPTED -> R.string.response_accepted
        ResponseStatus.DECLINED -> R.string.response_declined
        ResponseStatus.WITHDRAWN -> R.string.response_withdrawn
        ResponseStatus.IN_PROGRESS -> R.string.response_in_progress
        ResponseStatus.COMPLETED -> R.string.response_completed
    }
)

@Composable
fun EmergencyReportStatus.localizedText(): String = stringResource(
    when (this) {
        EmergencyReportStatus.LOCAL_ONLY -> R.string.status_local_only
        EmergencyReportStatus.QUEUED_FOR_FORWARDING -> R.string.status_queued
        EmergencyReportStatus.TRANSMISSION_IN_PROGRESS -> R.string.status_transmitting
        EmergencyReportStatus.WAITING_FOR_PEER -> R.string.status_waiting_peer
        EmergencyReportStatus.TRANSPORT_ACKNOWLEDGED -> R.string.status_transport_ack
        EmergencyReportStatus.TRANSMISSION_FAILED -> R.string.status_transmission_failed
        EmergencyReportStatus.RECEIVED_FROM_PEER -> R.string.status_received_peer
    }
)

@Composable
fun EmergencyLifecycleStatus.localizedText(): String = stringResource(
    when (this) {
        EmergencyLifecycleStatus.ACTIVE -> R.string.lifecycle_active
        EmergencyLifecycleStatus.CANCELLED -> R.string.lifecycle_cancelled
        EmergencyLifecycleStatus.RESOLVED -> R.string.lifecycle_resolved
    }
)

@Composable
fun EmergencyLocationSource.localizedText(): String = stringResource(
    when (this) {
        EmergencyLocationSource.GNSS -> R.string.location_source_gnss
        EmergencyLocationSource.MAP -> R.string.location_source_map
        EmergencyLocationSource.MANUAL -> R.string.location_source_manual
    }
)

@Composable
fun EmergencyRole.localizedText(): String = stringResource(
    when (this) {
        EmergencyRole.VICTIM -> R.string.role_victim
        EmergencyRole.RESPONDER -> R.string.role_responder
        EmergencyRole.VOLUNTEER -> R.string.role_volunteer
    }
)

@Composable
fun ThemePreference.localizedText(): String = stringResource(
    when (this) {
        ThemePreference.SYSTEM -> R.string.theme_system
        ThemePreference.LIGHT -> R.string.theme_light
        ThemePreference.DARK -> R.string.theme_dark
    }
)

@Composable
fun SafetyTimerStatus.localizedText(): String = stringResource(
    when (this) {
        SafetyTimerStatus.INACTIVE -> R.string.timer_inactive
        SafetyTimerStatus.ACTIVE -> R.string.timer_active
        SafetyTimerStatus.DUE -> R.string.timer_due
        SafetyTimerStatus.MISSED -> R.string.timer_missed
        SafetyTimerStatus.COMPLETED -> R.string.timer_completed
    }
)

@Composable
fun MessageDeliveryState.localizedText(): String = stringResource(
    when (this) {
        MessageDeliveryState.SENDING -> R.string.message_sending
        MessageDeliveryState.DELIVERED -> R.string.message_delivered
        MessageDeliveryState.RECEIVED -> R.string.message_received
        MessageDeliveryState.FAILED -> R.string.message_failed
        MessageDeliveryState.DISCONNECTED -> R.string.message_disconnected
    }
)
