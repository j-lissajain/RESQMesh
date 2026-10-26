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
    val location: StoredEmergencyLocation?,
    val originId: String?,
    val receivedFromPeerId: String?,
    val hopCount: Int = 0,
    val remainingTtl: Int = EmergencyEnvelopeRules.MAX_HOP_LIMIT,
    val lastHopId: String? = null,
    val contentRevision: Int = EmergencyRevisionRules.INITIAL_REVISION,
    val contentTrustName: String? = null,
    val lifecycleStatusName: String? = null,
    val updatedAtMillis: Long = createdAtMillis
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
        originId = report.originId?.trim()?.takeIf { it.isNotBlank() },
        receivedFromPeerId = report.receivedFromPeerId?.trim()?.takeIf { it.isNotBlank() },
        hopCount = report.hopCount,
        remainingTtl = report.remainingTtl,
        lastHopId = EmergencyEnvelopeRules.normalize(report.lastHopId),
        contentRevision = report.contentRevision,
        contentTrustName = report.contentTrust.name,
        lifecycleStatusName = report.lifecycleStatus.name,
        updatedAtMillis = report.updatedAtMillis,
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
        if (stored.id.isBlank() || stored.id.length > 100 || stored.id.any { it.isISOControl() } ||
            !EmergencyReportValidation.isDescriptionValid(stored.description) ||
            stored.createdAtMillis <= 0L ||
            !EmergencyReportValidation.isLocationDescriptionValid(stored.locationDescription.orEmpty())
        ) return null
        if (!EmergencyRevisionRules.isValid(stored.contentRevision)) return null
        val contentTrust = EmergencyContentTrust.fromStoredValue(stored.contentTrustName)
            ?: if (status == EmergencyReportStatus.RECEIVED_FROM_PEER) {
                EmergencyContentTrust.REMOTE_UNVERIFIED
            } else {
                EmergencyContentTrust.LOCAL_ORIGINATED
            }
        val lifecycleStatus = EmergencyLifecycleStatus.fromStoredValue(stored.lifecycleStatusName)
            ?: EmergencyLifecycleStatus.ACTIVE
        val location = stored.location?.let {
            val source = EmergencyLocationSource.fromStoredValue(it.sourceName) ?: return null
            if (it.latitude !in -90.0..90.0 || it.longitude !in -180.0..180.0 ||
                it.timestampMillis <= 0L || it.accuracyMeters?.let { accuracy ->
                    !accuracy.isFinite() || accuracy < 0f
                } == true
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
        val lastHopId = EmergencyEnvelopeRules.normalize(stored.lastHopId)
        val envelope = EmergencyEnvelope(stored.hopCount, stored.remainingTtl, lastHopId)
        if (EmergencyEnvelopeRules.validate(envelope) != null) return null
        return EmergencyReport(
            id = stored.id,
            type = type,
            severity = severity,
            description = stored.description,
            createdAtMillis = stored.createdAtMillis,
            status = status,
            location = location,
            locationDescription = stored.locationDescription,
            originId = stored.originId,
            receivedFromPeerId = stored.receivedFromPeerId,
            hopCount = envelope.hopCount,
            remainingTtl = envelope.remainingTtl,
            lastHopId = envelope.lastHopId,
            contentRevision = stored.contentRevision,
            contentTrust = contentTrust,
            lifecycleStatus = lifecycleStatus,
            updatedAtMillis = stored.updatedAtMillis.takeIf { it > 0L } ?: stored.createdAtMillis
        )
    }
}
