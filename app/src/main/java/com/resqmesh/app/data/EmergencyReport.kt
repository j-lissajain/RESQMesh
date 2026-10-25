package com.resqmesh.app.data

import java.util.UUID

enum class EmergencyType(val label: String) {
    MEDICAL("Medical emergency"),
    FLOOD_RESCUE("Flood rescue"),
    FIRE("Fire"),
    TRAPPED_PERSON("Trapped person"),
    FOOD_WATER("Food/water assistance"),
    SHELTER("Shelter"),
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
    TRANSPORT_ACKNOWLEDGED("Transport acknowledged — not accepted"),
    TRANSMISSION_FAILED("Transmission failed — retry available"),
    RECEIVED_FROM_PEER("Received from another device — local copy");

    companion object {
        fun fromStoredValue(value: String?): EmergencyReportStatus? =
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
    val receivedFromPeerId: String? = null
)
