package com.resqmesh.app.data

import java.nio.charset.StandardCharsets
import java.util.Base64
import com.resqmesh.app.security.MeshTrustState

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
    val hopCount: Int = 0,
    val remainingTtl: Int = EmergencyEnvelopeRules.MAX_HOP_LIMIT,
    val lastHopId: String? = null,
    val contentRevision: Int = EmergencyRevisionRules.INITIAL_REVISION,
    val lifecycleStatus: EmergencyLifecycleStatus = EmergencyLifecycleStatus.ACTIVE,
    val updatedAtMillis: Long = createdAtMillis
)

object EmergencyWireCodec {
    const val DISCRIMINATOR = "RESQMeshEmergency"
    const val SCHEMA_VERSION = 4
    const val LEGACY_SCHEMA_VERSION = 1
    const val PRELIMINARY_SCHEMA_VERSION = 2
    const val REVISION_SCHEMA_VERSION = 3
    const val MAX_WIRE_BYTES = 2048
    private const val LEGACY_FIELD_COUNT = 15
    private const val PRELIMINARY_FIELD_COUNT = 17
    private const val REVISION_FIELD_COUNT = 18
    private const val FIELD_COUNT = 20
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
             originId = originId?.trim()?.takeIf { it.isNotBlank() },
            hopCount = report.hopCount,
            remainingTtl = report.remainingTtl,
            lastHopId = EmergencyEnvelopeRules.normalize(report.lastHopId),
            contentRevision = report.contentRevision,
            lifecycleStatus = report.lifecycleStatus,
            updatedAtMillis = report.updatedAtMillis
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
            message.hopCount.toString(),
            message.remainingTtl.toString(),
            message.lastHopId?.let(::encodeText) ?: "-",
            message.contentRevision.toString(),
            message.lifecycleStatus.name,
            message.updatedAtMillis.toString()
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
        if (fields.isEmpty() || fields[0] != DISCRIMINATOR) return null
        val schemaVersion = fields.getOrNull(1)?.toIntOrNull() ?: return null
        return when (schemaVersion) {
            LEGACY_SCHEMA_VERSION -> decodeLegacy(fields)
            PRELIMINARY_SCHEMA_VERSION -> decodePreliminary(fields)
            REVISION_SCHEMA_VERSION -> decodeRevision(fields)
            SCHEMA_VERSION -> decodeCurrent(fields)
            else -> null
        }?.takeIf { validate(it) == null }
    }

    fun validate(message: EmergencyWireMessage): String? {
        if (message.schemaVersion != SCHEMA_VERSION &&
            message.schemaVersion != PRELIMINARY_SCHEMA_VERSION &&
            message.schemaVersion != REVISION_SCHEMA_VERSION &&
            message.schemaVersion != LEGACY_SCHEMA_VERSION
        ) {
            return "Unsupported emergency schema version"
        }
        if (message.emergencyId.isBlank() || message.emergencyId.length > MAX_ID_CHARS) return "Invalid emergency ID"
        if (message.description.isBlank() || message.description.length > MAX_DESCRIPTION_CHARS ||
            message.description.any { it.isISOControl() }
        ) {
            return "Invalid emergency description"
        }
        if (message.createdAtMillis <= 0L) return "Invalid creation timestamp"
        if (message.updatedAtMillis <= 0L || message.updatedAtMillis < message.createdAtMillis) {
            return "Invalid update timestamp"
        }
        if (!EmergencyRevisionRules.isValid(message.contentRevision)) return "Invalid content revision"
        if (message.originId != null &&
            (message.originId.isBlank() || message.originId.length > MAX_ID_CHARS ||
                message.originId.any { it.isISOControl() || it.isWhitespace() })
        ) {
            return "Invalid origin identifier"
        }
        if ((message.latitude == null) != (message.longitude == null)) return "Incomplete location"
        if (message.latitude != null && message.longitude != null) {
            if (message.latitude !in -90.0..90.0 || message.longitude !in -180.0..180.0) return "Location out of range"
            if (message.locationSource == null || message.locationTimestampMillis == null) {
                return "Incomplete location metadata"
            }
            if (message.locationTimestampMillis <= 0L ||
                message.accuracyMeters?.let { !it.isFinite() || it < 0f } == true
            ) {
                return "Invalid location metadata"
            }
        } else if (message.locationSource != null || message.accuracyMeters != null || message.locationTimestampMillis != null) {
            return "Location metadata without coordinates"
        }
        if (message.locationDescription != null && message.locationDescription.length > MAX_LOCATION_DESCRIPTION_CHARS) {
            return "Location description is too long"
        }
        if (message.locationDescription?.any { it.isISOControl() } == true) {
            return "Invalid location description"
        }
        return EmergencyEnvelopeRules.validate(
            hopCount = message.hopCount,
            remainingTtl = message.remainingTtl,
            lastHopId = message.lastHopId
        )
    }

    fun toReceivedReport(
        message: EmergencyWireMessage,
        peerId: String?,
        trustState: MeshTrustState = MeshTrustState.LEGACY_UNSIGNED
    ): EmergencyReport = EmergencyReport(
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
        receivedFromPeerId = peerId,
        hopCount = message.hopCount,
        remainingTtl = message.remainingTtl,
        lastHopId = message.lastHopId,
        contentRevision = message.contentRevision,
        contentTrust = if (trustState == MeshTrustState.AUTHENTICATED_KNOWN ||
            trustState == MeshTrustState.AUTHENTICATED_UNKNOWN
        ) EmergencyContentTrust.REMOTE_AUTHENTICATED else EmergencyContentTrust.REMOTE_UNVERIFIED,
        lifecycleStatus = message.lifecycleStatus,
        updatedAtMillis = message.updatedAtMillis
    )

    private fun decodeCurrent(fields: List<String>): EmergencyWireMessage? {
        if (fields.size != FIELD_COUNT) return null
        val shared = decodeSharedFields(fields) ?: return null
        val hopCount = fields[14].toIntOrNull() ?: return null
        val remainingTtl = fields[15].toIntOrNull() ?: return null
        return shared.copy(
            schemaVersion = SCHEMA_VERSION,
            hopCount = hopCount,
            remainingTtl = remainingTtl,
            lastHopId = fields[16].takeIf { it != "-" }?.let { decodeText(it) ?: return null },
            contentRevision = fields[17].toIntOrNull() ?: return null,
            lifecycleStatus = EmergencyLifecycleStatus.fromStoredValue(fields[18]) ?: return null,
            updatedAtMillis = fields[19].toLongOrNull() ?: return null
        )
    }

    private fun decodeRevision(fields: List<String>): EmergencyWireMessage? {
        if (fields.size != REVISION_FIELD_COUNT) return null
        val shared = decodeSharedFields(fields) ?: return null
        return shared.copy(
            schemaVersion = REVISION_SCHEMA_VERSION,
            hopCount = fields[14].toIntOrNull() ?: return null,
            remainingTtl = fields[15].toIntOrNull() ?: return null,
            lastHopId = fields[16].takeIf { it != "-" }?.let { decodeText(it) ?: return null },
            contentRevision = fields[17].toIntOrNull() ?: return null,
            lifecycleStatus = EmergencyLifecycleStatus.ACTIVE,
            updatedAtMillis = shared.createdAtMillis
        )
    }

    private fun decodePreliminary(fields: List<String>): EmergencyWireMessage? {
        if (fields.size != PRELIMINARY_FIELD_COUNT) return null
        val shared = decodeSharedFields(fields) ?: return null
        return shared.copy(
            schemaVersion = PRELIMINARY_SCHEMA_VERSION,
            hopCount = fields[14].toIntOrNull() ?: return null,
            remainingTtl = fields[15].toIntOrNull() ?: return null,
            lastHopId = fields[16].takeIf { it != "-" }?.let { decodeText(it) ?: return null },
            contentRevision = EmergencyRevisionRules.INITIAL_REVISION
            , lifecycleStatus = EmergencyLifecycleStatus.ACTIVE,
            updatedAtMillis = shared.createdAtMillis
        )
    }

    private fun decodeLegacy(fields: List<String>): EmergencyWireMessage? {
        if (fields.size != LEGACY_FIELD_COUNT) return null
        // Phase 4 sent a device-local UI status in the last field. It is not authoritative.
        if (fields[14].isBlank()) return null
        val shared = decodeSharedFields(fields) ?: return null
        return shared.copy(
            schemaVersion = LEGACY_SCHEMA_VERSION,
            hopCount = 0,
            remainingTtl = EmergencyEnvelopeRules.MAX_HOP_LIMIT,
            lastHopId = null,
            contentRevision = EmergencyRevisionRules.INITIAL_REVISION
            , lifecycleStatus = EmergencyLifecycleStatus.ACTIVE,
            updatedAtMillis = shared.createdAtMillis
        )
    }

    private fun decodeSharedFields(fields: List<String>): EmergencyWireMessage? {
        if (fields.size < 14) return null
        val type = EmergencyType.fromStoredValue(fields[3]) ?: return null
        val severity = EmergencySeverity.fromStoredValue(fields[4]) ?: return null
        val locationSource = fields[8].takeIf { it != "-" }?.let(EmergencyLocationSource::fromStoredValue)
            ?: if (fields[8] == "-") null else return null
        return EmergencyWireMessage(
            emergencyId = decodeText(fields[2]) ?: return null,
            schemaVersion = fields[1].toIntOrNull() ?: return null,
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
            originId = fields[13].takeIf { it != "-" }?.let { decodeText(it) ?: return null }
        )
    }

    private fun encodeText(value: String): String = encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String? = runCatching {
        String(decoder.decode(value), StandardCharsets.UTF_8)
    }.getOrNull()

    private fun String.toNullableDouble(): Double? = if (this == "-") null else toDoubleOrNull()
    private fun String.toNullableFloat(): Float? = if (this == "-") null else toFloatOrNull()
    private fun String.toNullableLong(): Long? = if (this == "-") null else toLongOrNull()
}
