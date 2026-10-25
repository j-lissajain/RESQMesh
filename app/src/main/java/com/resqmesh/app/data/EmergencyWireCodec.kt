package com.resqmesh.app.data

import java.nio.charset.StandardCharsets
import java.util.Base64

data class EmergencyWireMessage(
    val emergencyId: String,
    val schemaVersion: Int,
    val type: EmergencyType,
    val severity: EmergencySeverity,
    val description: String,
    val latitude: Double?,
    val longitude: Double?,
    val locationSource: EmergencyLocationSource?,
    val accuracyMeters: Float?,
    val locationTimestampMillis: Long?,
    val locationDescription: String?,
    val createdAtMillis: Long,
    val originId: String?,
    val status: EmergencyReportStatus
)

object EmergencyWireCodec {
    const val DISCRIMINATOR = "RESQMeshEmergency"
    const val SCHEMA_VERSION = 1
    const val MAX_WIRE_BYTES = 1024
    private const val FIELD_COUNT = 15
    private const val MAX_DESCRIPTION_CHARS = 240
    private const val MAX_LOCATION_DESCRIPTION_CHARS = 180
    private const val MAX_ID_CHARS = 100
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun fromReport(report: EmergencyReport, originId: String? = report.originId): EmergencyWireMessage =
        EmergencyWireMessage(
            emergencyId = report.id,
            schemaVersion = SCHEMA_VERSION,
            type = report.type,
            severity = report.severity,
            description = report.description,
            latitude = report.location?.latitude,
            longitude = report.location?.longitude,
            locationSource = report.location?.source,
            accuracyMeters = report.location?.accuracyMeters,
            locationTimestampMillis = report.location?.timestampMillis,
            locationDescription = report.locationDescription,
            createdAtMillis = report.createdAtMillis,
            originId = originId,
            status = report.status
        )

    fun encode(report: EmergencyReport, originId: String? = report.originId): ByteArray {
        val message = fromReport(report, originId)
        require(validate(message) == null) { validate(message) ?: "Invalid emergency" }
        val fields = listOf(
            DISCRIMINATOR,
            message.schemaVersion.toString(),
            encodeText(message.emergencyId),
            message.type.name,
            message.severity.name,
            encodeText(message.description),
            message.latitude?.toString() ?: "-",
            message.longitude?.toString() ?: "-",
            message.locationSource?.name ?: "-",
            message.accuracyMeters?.toString() ?: "-",
            message.locationTimestampMillis?.toString() ?: "-",
            message.locationDescription?.let(::encodeText) ?: "-",
            message.createdAtMillis.toString(),
            message.originId?.let(::encodeText) ?: "-",
            message.status.name
        )
        return fields.joinToString("|").toByteArray(StandardCharsets.UTF_8).also {
            require(it.size <= MAX_WIRE_BYTES) { "Emergency payload is too large" }
        }
    }

    fun decode(payload: ByteArray): EmergencyWireMessage? {
        if (payload.size > MAX_WIRE_BYTES) return null
        val fields = runCatching {
            String(payload, StandardCharsets.UTF_8).split('|')
        }.getOrNull() ?: return null
        if (fields.size != FIELD_COUNT || fields[0] != DISCRIMINATOR) return null
        val schemaVersion = fields[1].toIntOrNull() ?: return null
        val type = EmergencyType.fromStoredValue(fields[3]) ?: return null
        val severity = EmergencySeverity.fromStoredValue(fields[4]) ?: return null
        val locationSource = fields[8].takeIf { it != "-" }?.let(EmergencyLocationSource::fromStoredValue)
            ?: if (fields[8] == "-") null else return null
        val message = EmergencyWireMessage(
            emergencyId = decodeText(fields[2]) ?: return null,
            schemaVersion = schemaVersion,
            type = type,
            severity = severity,
            description = decodeText(fields[5]) ?: return null,
            latitude = fields[6].toNullableDouble() ?: if (fields[6] == "-") null else return null,
            longitude = fields[7].toNullableDouble() ?: if (fields[7] == "-") null else return null,
            locationSource = locationSource,
            accuracyMeters = fields[9].toNullableFloat() ?: if (fields[9] == "-") null else return null,
            locationTimestampMillis = fields[10].toNullableLong() ?: if (fields[10] == "-") null else return null,
            locationDescription = fields[11].takeIf { it != "-" }?.let { decodeText(it) ?: return null },
            createdAtMillis = fields[12].toLongOrNull() ?: return null,
            originId = fields[13].takeIf { it != "-" }?.let { decodeText(it) ?: return null },
            status = EmergencyReportStatus.fromStoredValue(fields[14]) ?: return null
        )
        return message.takeIf { validate(it) == null }
    }

    fun validate(message: EmergencyWireMessage): String? {
        if (message.schemaVersion != SCHEMA_VERSION) return "Unsupported emergency schema version"
        if (message.emergencyId.isBlank() || message.emergencyId.length > MAX_ID_CHARS) return "Invalid emergency ID"
        if (message.description.isBlank() || message.description.length > MAX_DESCRIPTION_CHARS) return "Invalid emergency description"
        if (message.createdAtMillis <= 0L) return "Invalid creation timestamp"
        if ((message.latitude == null) != (message.longitude == null)) return "Incomplete location"
        if (message.latitude != null && message.longitude != null) {
            if (message.latitude !in -90.0..90.0 || message.longitude !in -180.0..180.0) return "Location out of range"
            if (message.locationSource == null || message.locationTimestampMillis == null) return "Incomplete location metadata"
        } else if (message.locationSource != null || message.accuracyMeters != null || message.locationTimestampMillis != null) {
            return "Location metadata without coordinates"
        }
        if (message.locationDescription != null && message.locationDescription.length > MAX_LOCATION_DESCRIPTION_CHARS) {
            return "Location description is too long"
        }
        return null
    }

    fun toReceivedReport(message: EmergencyWireMessage, peerId: String?): EmergencyReport = EmergencyReport(
        id = message.emergencyId,
        type = message.type,
        severity = message.severity,
        description = message.description,
        createdAtMillis = message.createdAtMillis,
        status = EmergencyReportStatus.RECEIVED_FROM_PEER,
        location = if (message.latitude != null && message.longitude != null) {
            EmergencyLocation(
                latitude = message.latitude,
                longitude = message.longitude,
                accuracyMeters = message.accuracyMeters,
                timestampMillis = message.locationTimestampMillis ?: message.createdAtMillis,
                source = message.locationSource ?: EmergencyLocationSource.MANUAL,
                isVerified = false
            )
        } else null,
        locationDescription = message.locationDescription,
        originId = message.originId,
        receivedFromPeerId = peerId
    )

    private fun encodeText(value: String): String = encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String? = runCatching {
        String(decoder.decode(value), StandardCharsets.UTF_8)
    }.getOrNull()

    private fun String.toNullableDouble(): Double? = if (this == "-") null else toDoubleOrNull()
    private fun String.toNullableFloat(): Float? = if (this == "-") null else toFloatOrNull()
    private fun String.toNullableLong(): Long? = if (this == "-") null else toLongOrNull()
}
