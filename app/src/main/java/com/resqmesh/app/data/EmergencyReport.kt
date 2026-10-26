package com.resqmesh.app.data

import java.util.UUID

enum class EmergencyType(val label: String) {
    MEDICAL("Medical emergency"),
    FLOOD_RESCUE("Flood rescue"),
    FIRE("Fire"),
    TRAPPED_PERSON("Trapped person"),
    FOOD_WATER("Food/water assistance"),
    SHELTER("Shelter"),
    PERSONAL_SAFETY("Personal safety threat"),
    HARASSMENT("Harassment"),
    UNSAFE_TRAVEL("Unsafe travel situation"),
    CHILD_SAFETY("Child safety concern"),
    MISSING_PERSON("Missing person concern"),
    OTHER("Other");

    companion object {
        fun fromStoredValue(value: String?): EmergencyType? =
            entries.firstOrNull { it.name == value }
    }
}

enum class EmergencySeverity(val label: String) {
    CRITICAL("Critical"),
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low");

    companion object {
        fun fromStoredValue(value: String?): EmergencySeverity? =
            entries.firstOrNull { it.name == value }
    }
}

enum class EmergencyReportStatus(val label: String) {
    LOCAL_ONLY("Saved locally — not transmitted"),
    QUEUED_FOR_FORWARDING("Queued for nearby forwarding"),
    TRANSMISSION_IN_PROGRESS("Transmission in progress"),
    WAITING_FOR_PEER("Waiting for a nearby peer"),
    TRANSPORT_ACKNOWLEDGED("Transport acknowledged — not accepted"),
    TRANSMISSION_FAILED("Transmission failed — retry available"),
    RECEIVED_FROM_PEER("Received from another device — local copy");

    companion object {
        fun fromStoredValue(value: String?): EmergencyReportStatus? =
            entries.firstOrNull { it.name == value }
    }
}

enum class EmergencyContentTrust {
    LOCAL_ORIGINATED,
    REMOTE_AUTHENTICATED,
    REMOTE_UNVERIFIED;

    companion object {
        fun fromStoredValue(value: String?): EmergencyContentTrust? =
            entries.firstOrNull { it.name == value }
    }
}

enum class EmergencyLifecycleStatus(val label: String) {
    ACTIVE("Active"),
    CANCELLED("Cancelled"),
    RESOLVED("Resolved");

    companion object {
        fun fromStoredValue(value: String?): EmergencyLifecycleStatus? =
            entries.firstOrNull { it.name == value }
    }
}

enum class EmergencyLocationSource(val label: String) {
    GNSS("Device-reported GNSS/GPS"),
    MAP("Offline map pin — unverified"),
    MANUAL("Manual entry — unverified");

    companion object {
        fun fromStoredValue(value: String?): EmergencyLocationSource? =
            entries.firstOrNull { it.name == value }
    }
}

data class EmergencyLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val timestampMillis: Long,
    val source: EmergencyLocationSource,
    val isVerified: Boolean = false
)

data class EmergencyReport(
    val id: String = UUID.randomUUID().toString(),
    val type: EmergencyType,
    val severity: EmergencySeverity,
    val description: String,
    val createdAtMillis: Long,
    val status: EmergencyReportStatus = EmergencyReportStatus.LOCAL_ONLY,
    val location: EmergencyLocation? = null,
    val locationDescription: String? = null,
    val originId: String? = null,
    val receivedFromPeerId: String? = null,
    val hopCount: Int = 0,
    val remainingTtl: Int = EmergencyEnvelopeRules.MAX_HOP_LIMIT,
    val lastHopId: String? = null,
    val contentRevision: Int = EmergencyRevisionRules.INITIAL_REVISION,
    val contentTrust: EmergencyContentTrust = EmergencyContentTrust.LOCAL_ORIGINATED,
    val lifecycleStatus: EmergencyLifecycleStatus = EmergencyLifecycleStatus.ACTIVE,
    val updatedAtMillis: Long = createdAtMillis
)
