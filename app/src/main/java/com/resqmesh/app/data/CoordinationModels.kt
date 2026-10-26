package com.resqmesh.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import com.resqmesh.app.security.MeshTrustState

enum class VolunteerSkill(val label: String) {
    FIRST_AID("First aid"),
    MEDICAL_ASSISTANCE("Medical assistance"),
    TRANSPORTATION("Transportation"),
    FOOD_DISTRIBUTION("Food distribution"),
    WATER_DISTRIBUTION("Water distribution"),
    SHELTER_ASSISTANCE("Shelter assistance"),
    SEARCH_ASSISTANCE("Search assistance"),
    GENERAL_SUPPORT("General emergency support")
}

enum class VolunteerAvailability(val label: String) {
    AVAILABLE("Available"),
    BUSY("Busy"),
    UNAVAILABLE("Unavailable")
}

data class VolunteerProfile(
    val id: String = UUID.randomUUID().toString(),
    val nodeId: String,
    val alias: String,
    val skills: Set<VolunteerSkill>,
    val assistanceCapabilities: String = "",
    val availability: VolunteerAvailability = VolunteerAvailability.AVAILABLE,
    val operatingArea: String = "",
    val transportAvailable: Boolean = false,
    val resourceCapabilities: String = "",
    val contactInfo: String? = null,
    val revision: Int = EmergencyRevisionRules.INITIAL_REVISION,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val expiresAtMillis: Long? = null,
    val isRemoteUnverified: Boolean = false,
    val trustState: MeshTrustState = MeshTrustState.LOCAL,
    val originFingerprint: String? = null
)

enum class ResourceCategory(val label: String) {
    DRINKING_WATER("Drinking water"),
    FOOD("Food"),
    MEDICAL_SUPPLIES("Medical supplies"),
    FIRST_AID_SUPPLIES("First-aid supplies"),
    TEMPORARY_SHELTER("Temporary shelter"),
    TRANSPORTATION("Transportation"),
    CLOTHING("Clothing"),
    BLANKETS("Blankets"),
    RESCUE_EQUIPMENT("Rescue equipment"),
    GENERAL_ASSISTANCE("General assistance")
}

enum class ResourceAvailability(val label: String) {
    AVAILABLE("Available"),
    PROVISIONALLY_COMMITTED("Provisionally committed"),
    UNAVAILABLE("Unavailable")
}

data class ApproximateLocation(
    val latitude: Double,
    val longitude: Double
)

data class ResourceRecord(
    val id: String = UUID.randomUUID().toString(),
    val providerNodeId: String,
    val providerAlias: String,
    val category: ResourceCategory,
    val description: String,
    val quantity: Double,
    val unit: String,
    val approximateLocation: ApproximateLocation? = null,
    val approximateArea: String = "",
    val availability: ResourceAvailability = ResourceAvailability.AVAILABLE,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis,
    val revision: Int = EmergencyRevisionRules.INITIAL_REVISION,
    val expiresAtMillis: Long? = null,
    val isRemoteUnverified: Boolean = false,
    val trustState: MeshTrustState = MeshTrustState.LOCAL,
    val originFingerprint: String? = null
)

enum class ResponseStatus(val label: String) {
    OFFERED("Offered"),
    ACCEPTED("Accepted"),
    DECLINED("Declined"),
    WITHDRAWN("Withdrawn"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed")
}

data class AssistanceResponse(
    val id: String = UUID.randomUUID().toString(),
    val emergencyId: String,
    val responderNodeId: String,
    val responderAlias: String,
    val assistanceType: VolunteerSkill,
    val message: String = "",
    val status: ResponseStatus = ResponseStatus.OFFERED,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis,
    val revision: Int = EmergencyRevisionRules.INITIAL_REVISION,
    val isRemoteUnverified: Boolean = false,
    val trustState: MeshTrustState = MeshTrustState.LOCAL,
    val originFingerprint: String? = null
)

data class TrustedContact(
    val id: String = UUID.randomUUID().toString(),
    val alias: String,
    val contactInfo: String,
    val notes: String = ""
)

data class SafetyPreferences(
    val discreetSosEnabled: Boolean = false,
    val contacts: List<TrustedContact> = emptyList(),
    val sharePreciseLocationByDefault: Boolean = false
)

enum class MeshRecordType {
    VOLUNTEER,
    RESOURCE,
    RESPONSE,
    EMERGENCY_UPDATE,
    SYNC_SUMMARY
}

data class MeshRecord(
    val recordId: String,
    val type: MeshRecordType,
    val originId: String,
    val revision: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val body: JSONObject,
    val hopCount: Int = 0,
    val remainingTtl: Int = EmergencyEnvelopeRules.MAX_HOP_LIMIT,
    val lastHopId: String? = null,
    val trustState: MeshTrustState = MeshTrustState.LEGACY_UNSIGNED,
    val originFingerprint: String? = null
) {
    val key: String get() = "$recordId#$revision"
}

object MeshRecordCodec {
    const val DISCRIMINATOR = "RESQMeshRecord"
    const val SCHEMA_VERSION = 1
    const val MAX_BYTES = 2048
    private const val FIELD_COUNT = 12
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(record: MeshRecord): ByteArray {
        val validationError = validate(record)
        require(validationError == null) { validationError ?: "Invalid mesh record" }
        return encodeUnchecked(record).also {
            require(it.size <= MAX_BYTES) { "Mesh record payload is too large" }
        }
    }

    private fun encodeUnchecked(record: MeshRecord): ByteArray {
        val fields = listOf(
            DISCRIMINATOR,
            SCHEMA_VERSION.toString(),
            record.type.name,
            encodeText(record.recordId),
            encodeText(record.originId),
            record.revision.toString(),
            record.createdAtMillis.toString(),
            record.updatedAtMillis.toString(),
            record.hopCount.toString(),
            record.remainingTtl.toString(),
            record.lastHopId?.let(::encodeText) ?: "-",
            encodeText(record.body.toString())
        )
        return fields.joinToString("|").toByteArray(StandardCharsets.UTF_8)
    }

    fun decode(payload: ByteArray): MeshRecord? {
        if (payload.size > MAX_BYTES) return null
        val fields = runCatching { String(payload, StandardCharsets.UTF_8).split('|') }.getOrNull()
            ?: return null
        if (fields.size != FIELD_COUNT || fields[0] != DISCRIMINATOR || fields[1] != SCHEMA_VERSION.toString()) return null
        val type = runCatching { MeshRecordType.valueOf(fields[2]) }.getOrNull() ?: return null
        val body = runCatching { JSONObject(decodeText(fields[11]) ?: return null) }.getOrNull() ?: return null
        val record = MeshRecord(
            recordId = decodeText(fields[3]) ?: return null,
            type = type,
            originId = decodeText(fields[4]) ?: return null,
            revision = fields[5].toIntOrNull() ?: return null,
            createdAtMillis = fields[6].toLongOrNull() ?: return null,
            updatedAtMillis = fields[7].toLongOrNull() ?: return null,
            hopCount = fields[8].toIntOrNull() ?: return null,
            remainingTtl = fields[9].toIntOrNull() ?: return null,
            lastHopId = fields[10].takeIf { it != "-" }?.let { decodeText(it) ?: return null },
            body = body
        )
        return record.takeIf { validate(it) == null }
    }

    fun validate(record: MeshRecord): String? {
        if (record.recordId.isBlank() || record.recordId.length > 100) return "Invalid record ID"
        if (record.originId.isBlank() || record.originId.length > 100) return "Invalid origin ID"
        if (!EmergencyRevisionRules.isValid(record.revision)) return "Invalid record revision"
        if (record.createdAtMillis <= 0L || record.updatedAtMillis < record.createdAtMillis) return "Invalid timestamps"
        val envelopeError = EmergencyEnvelopeRules.validate(record.hopCount, record.remainingTtl, record.lastHopId)
        if (envelopeError != null) return envelopeError
        if (encodeUnchecked(record).size > MAX_BYTES) return "Mesh record payload is too large"
        return null
    }

    fun nextHop(record: MeshRecord, localNodeId: String): MeshRecord? {
        val envelope = EmergencyEnvelope(record.hopCount, record.remainingTtl, record.lastHopId)
        val next = EmergencyEnvelopeRules.nextHop(envelope, localNodeId) ?: return null
        return record.copy(
            hopCount = next.hopCount,
            remainingTtl = next.remainingTtl,
            lastHopId = next.lastHopId
        )
    }

    private fun encodeText(value: String): String = encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun decodeText(value: String): String? = runCatching {
        String(decoder.decode(value), StandardCharsets.UTF_8)
    }.getOrNull()
}

fun VolunteerProfile.toMeshRecord(): MeshRecord = MeshRecord(
    recordId = id,
    type = MeshRecordType.VOLUNTEER,
    originId = nodeId,
    revision = revision,
    createdAtMillis = updatedAtMillis,
    updatedAtMillis = updatedAtMillis,
    body = JSONObject().apply {
        put("node_id", nodeId)
        put("alias", alias)
        put("skills", JSONArray(skills.map { it.name }))
        put("assistance_capabilities", assistanceCapabilities)
        put("availability", availability.name)
        put("operating_area", operatingArea)
        put("transport_available", transportAvailable)
        put("resource_capabilities", resourceCapabilities)
        // Contact information is intentionally local-only and is never shared automatically.
        expiresAtMillis?.let { put("expires_at", it) }
    },
    trustState = if (isRemoteUnverified) MeshTrustState.LEGACY_UNSIGNED else MeshTrustState.LOCAL
)

fun ResourceRecord.toMeshRecord(): MeshRecord = MeshRecord(
    recordId = id,
    type = MeshRecordType.RESOURCE,
    originId = providerNodeId,
    revision = revision,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    body = JSONObject().apply {
        put("provider_node_id", providerNodeId)
        put("provider_alias", providerAlias)
        put("category", category.name)
        put("description", description)
        put("quantity", quantity)
        put("unit", unit)
        put("approximate_area", approximateArea)
        approximateLocation?.let {
            put("latitude", it.latitude)
            put("longitude", it.longitude)
        }
        put("availability", availability.name)
        expiresAtMillis?.let { put("expires_at", it) }
    },
    trustState = if (isRemoteUnverified) MeshTrustState.LEGACY_UNSIGNED else MeshTrustState.LOCAL
)

fun AssistanceResponse.toMeshRecord(): MeshRecord = MeshRecord(
    recordId = id,
    type = MeshRecordType.RESPONSE,
    originId = responderNodeId,
    revision = revision,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    body = JSONObject().apply {
        put("emergency_id", emergencyId)
        put("responder_node_id", responderNodeId)
        put("responder_alias", responderAlias)
        put("assistance_type", assistanceType.name)
        put("message", message)
        put("status", status.name)
    },
    trustState = if (isRemoteUnverified) MeshTrustState.LEGACY_UNSIGNED else MeshTrustState.LOCAL
)

fun JSONObject.toVolunteerProfile(record: MeshRecord): VolunteerProfile? = runCatching {
    val skills = buildSet {
        val values = optJSONArray("skills") ?: JSONArray()
        for (index in 0 until values.length()) {
            runCatching { VolunteerSkill.valueOf(values.optString(index)) }.getOrNull()?.let(::add)
        }
    }
    val remoteUnverified = optBoolean("remote_unverified", true)
    VolunteerProfile(
        id = record.recordId,
        nodeId = optString("node_id", record.originId),
        alias = optString("alias").trim(),
        skills = skills,
        assistanceCapabilities = optString("assistance_capabilities"),
        availability = VolunteerAvailability.valueOf(optString("availability")),
        operatingArea = optString("operating_area"),
        transportAvailable = optBoolean("transport_available", false),
        resourceCapabilities = optString("resource_capabilities"),
        contactInfo = optString("contact_info").takeIf { it.isNotBlank() },
        revision = record.revision,
        updatedAtMillis = record.updatedAtMillis,
        expiresAtMillis = if (has("expires_at")) optLong("expires_at") else null,
        isRemoteUnverified = remoteUnverified,
        trustState = if (!remoteUnverified) MeshTrustState.LOCAL else record.trustState,
        originFingerprint = record.originFingerprint
    ).takeIf { it.alias.isNotBlank() && it.skills.isNotEmpty() }
}.getOrNull()

fun JSONObject.toResourceRecord(record: MeshRecord): ResourceRecord? = runCatching {
    val remoteUnverified = optBoolean("remote_unverified", true)
    ResourceRecord(
        id = record.recordId,
        providerNodeId = optString("provider_node_id", record.originId),
        providerAlias = optString("provider_alias"),
        category = ResourceCategory.valueOf(optString("category")),
        description = optString("description").trim(),
        quantity = optDouble("quantity"),
        unit = optString("unit").trim(),
        approximateLocation = if (has("latitude") && has("longitude")) ApproximateLocation(optDouble("latitude"), optDouble("longitude")) else null,
        approximateArea = optString("approximate_area"),
        availability = ResourceAvailability.valueOf(optString("availability")),
        createdAtMillis = record.createdAtMillis,
        updatedAtMillis = record.updatedAtMillis,
        revision = record.revision,
        expiresAtMillis = if (has("expires_at")) optLong("expires_at") else null,
        isRemoteUnverified = remoteUnverified,
        trustState = if (!remoteUnverified) MeshTrustState.LOCAL else record.trustState,
        originFingerprint = record.originFingerprint
    ).takeIf { it.description.isNotBlank() && it.quantity >= 0.0 && it.unit.isNotBlank() }
}.getOrNull()

fun JSONObject.toAssistanceResponse(record: MeshRecord): AssistanceResponse? = runCatching {
    val remoteUnverified = optBoolean("remote_unverified", true)
    AssistanceResponse(
        id = record.recordId,
        emergencyId = optString("emergency_id").trim(),
        responderNodeId = optString("responder_node_id", record.originId),
        responderAlias = optString("responder_alias").trim(),
        assistanceType = VolunteerSkill.valueOf(optString("assistance_type")),
        message = optString("message"),
        status = ResponseStatus.valueOf(optString("status")),
        createdAtMillis = record.createdAtMillis,
        updatedAtMillis = record.updatedAtMillis,
        revision = record.revision,
        isRemoteUnverified = remoteUnverified,
        trustState = if (!remoteUnverified) MeshTrustState.LOCAL else record.trustState,
        originFingerprint = record.originFingerprint
    ).takeIf { it.emergencyId.isNotBlank() && it.responderNodeId.isNotBlank() }
}.getOrNull()
