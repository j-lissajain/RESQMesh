package com.resqmesh.app.data

data class StoredEmergencyLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val timestampMillis: Long,
    val sourceName: String,
    val verified: Boolean
)

data class StoredEmergencyReport(
    val id: String,
    val typeName: String,
    val severityName: String,
    val description: String,
    val createdAtMillis: Long,
    val statusName: String,
    val locationDescription: String?,
    val location: StoredEmergencyLocation?
)

object EmergencyReportStorageCodec {
    fun encode(report: EmergencyReport): StoredEmergencyReport = StoredEmergencyReport(
        id = report.id,
        typeName = report.type.name,
        severityName = report.severity.name,
        description = report.description,
        createdAtMillis = report.createdAtMillis,
        statusName = report.status.name,
        locationDescription = report.locationDescription?.trim()?.takeIf { it.isNotBlank() },
        location = report.location?.let {
            StoredEmergencyLocation(
                latitude = it.latitude,
                longitude = it.longitude,
                accuracyMeters = it.accuracyMeters,
                timestampMillis = it.timestampMillis,
                sourceName = it.source.name,
                verified = it.isVerified
            )
        }
    )

    fun decode(stored: StoredEmergencyReport): EmergencyReport? {
        val type = EmergencyType.fromStoredValue(stored.typeName) ?: return null
        val severity = EmergencySeverity.fromStoredValue(stored.severityName) ?: return null
        val status = EmergencyReportStatus.fromStoredValue(stored.statusName) ?: return null
        if (stored.id.isBlank() || stored.description.isBlank()) return null
        val location = stored.location?.let {
            val source = EmergencyLocationSource.fromStoredValue(it.sourceName) ?: return null
            if (it.latitude !in -90.0..90.0 || it.longitude !in -180.0..180.0 ||
                it.timestampMillis == Long.MIN_VALUE
            ) return null
            EmergencyLocation(
                latitude = it.latitude,
                longitude = it.longitude,
                accuracyMeters = it.accuracyMeters,
                timestampMillis = it.timestampMillis,
                source = source,
                isVerified = it.verified
            )
        }
        return EmergencyReport(
            id = stored.id,
            type = type,
            severity = severity,
            description = stored.description,
            createdAtMillis = stored.createdAtMillis,
            status = status,
            location = location,
            locationDescription = stored.locationDescription
        )
    }
}
