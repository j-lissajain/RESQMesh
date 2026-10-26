package com.resqmesh.app.data

import android.content.Context
import com.resqmesh.app.security.MeshTrustState
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private class JsonPreferencesStore(
    private val readRaw: () -> String,
    private val writeRaw: (String) -> Unit
) {
    constructor(
        context: Context,
        name: String,
        key: String
    ) : this(
        readRaw = {
            context.applicationContext
                .getSharedPreferences(name, Context.MODE_PRIVATE)
                .getString(key, "[]") ?: "[]"
        },
        writeRaw = { value ->
            context.applicationContext
                .getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .putString(key, value)
                .commit()
        }
    )

    private val lock = Any()

    fun read(): JSONArray = synchronized(lock) {
        runCatching { JSONArray(readRaw()) }.getOrElse { JSONArray() }
    }

    fun write(array: JSONArray) = synchronized(lock) {
        writeRaw(array.toString())
    }

    companion object {
        fun inMemory(): JsonPreferencesStore {
            var raw = "[]"
            return JsonPreferencesStore(
                readRaw = { raw },
                writeRaw = { raw = it }
            )
        }
    }
}

class VolunteerProfileStore private constructor(private val store: JsonPreferencesStore) {
    constructor(context: Context) : this(JsonPreferencesStore(context, "resqmesh_volunteers", "profiles"))

    fun list(nowMillis: Long = System.currentTimeMillis()): List<VolunteerProfile> = store.read().toList { json ->
        runCatching {
            val remoteUnverified = json.optBoolean("remote_unverified", false)
            val trustState = runCatching {
                MeshTrustState.valueOf(json.optString("trust_state"))
            }.getOrNull() ?: if (remoteUnverified) MeshTrustState.LEGACY_UNSIGNED else MeshTrustState.LOCAL
            val fingerprint = json.optString("origin_fingerprint").takeIf { it.isNotBlank() }
            val record = MeshRecord(
                recordId = json.optString("id"),
                type = MeshRecordType.VOLUNTEER,
                originId = json.optString("node_id"),
                revision = json.optInt("revision", 1),
                createdAtMillis = json.optLong("updated_at", 1L),
                updatedAtMillis = json.optLong("updated_at", 1L),
                body = json,
                trustState = trustState,
                originFingerprint = fingerprint
            )
            json.toVolunteerProfile(record)?.copy(
                isRemoteUnverified = remoteUnverified,
                trustState = trustState,
                originFingerprint = fingerprint
            )
        }.getOrNull()
    }.distinctBy { it.id }.filter { it.expiresAtMillis == null || it.expiresAtMillis > nowMillis }

    fun save(profile: VolunteerProfile): VolunteerProfile {
        val current = list().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index < 0) {
            current += profile
        } else {
            val existing = current[index]
            val canReplace = when {
                !existing.isRemoteUnverified && profile.isRemoteUnverified -> false
                profile.revision > existing.revision -> true
                profile.revision == existing.revision && !profile.isRemoteUnverified -> true
                profile.revision == existing.revision &&
                    existing.trustState == MeshTrustState.LEGACY_UNSIGNED &&
                    profile.trustState != MeshTrustState.LEGACY_UNSIGNED -> true
                else -> false
            }
            if (canReplace) current[index] = profile
        }
        val deduplicated = current.distinctBy { it.id }
        store.write(JSONArray(deduplicated.map(::volunteerToJson)))
        return deduplicated.first { it.id == profile.id }
    }

    private fun volunteerToJson(profile: VolunteerProfile): JSONObject = JSONObject().apply {
        put("id", profile.id)
        put("node_id", profile.nodeId)
        put("alias", profile.alias)
        put("skills", JSONArray(profile.skills.map { it.name }))
        put("assistance_capabilities", profile.assistanceCapabilities)
        put("availability", profile.availability.name)
        put("operating_area", profile.operatingArea)
        put("transport_available", profile.transportAvailable)
        put("resource_capabilities", profile.resourceCapabilities)
        profile.contactInfo?.let { put("contact_info", it) }
        put("revision", profile.revision)
        put("updated_at", profile.updatedAtMillis)
        put("remote_unverified", profile.isRemoteUnverified)
        put("trust_state", profile.trustState.name)
        profile.originFingerprint?.let { put("origin_fingerprint", it) }
        profile.expiresAtMillis?.let { put("expires_at", it) }
    }

    companion object {
        fun inMemory(): VolunteerProfileStore = VolunteerProfileStore(JsonPreferencesStore.inMemory())
    }
}

class ResourceStore private constructor(private val store: JsonPreferencesStore) {
    constructor(context: Context) : this(JsonPreferencesStore(context, "resqmesh_resources", "records"))

    fun list(nowMillis: Long = System.currentTimeMillis()): List<ResourceRecord> = store.read().toList { json ->
        runCatching {
            val remoteUnverified = json.optBoolean("remote_unverified", false)
            val trustState = runCatching {
                MeshTrustState.valueOf(json.optString("trust_state"))
            }.getOrNull() ?: if (remoteUnverified) MeshTrustState.LEGACY_UNSIGNED else MeshTrustState.LOCAL
            val fingerprint = json.optString("origin_fingerprint").takeIf { it.isNotBlank() }
            val record = MeshRecord(
                recordId = json.optString("id"),
                type = MeshRecordType.RESOURCE,
                originId = json.optString("provider_node_id"),
                revision = json.optInt("revision", 1),
                createdAtMillis = json.optLong("created_at", 1L),
                updatedAtMillis = json.optLong("updated_at", 1L),
                body = json,
                trustState = trustState,
                originFingerprint = fingerprint
            )
            json.toResourceRecord(record)?.copy(
                isRemoteUnverified = remoteUnverified,
                trustState = trustState,
                originFingerprint = fingerprint
            )
        }.getOrNull()
    }.distinctBy { it.id }.filter { it.expiresAtMillis == null || it.expiresAtMillis > nowMillis }

    fun save(resource: ResourceRecord): ResourceRecord {
        val current = list().toMutableList()
        val index = current.indexOfFirst { it.id == resource.id }
        if (index < 0) {
            current += resource
        } else {
            val existing = current[index]
            val canReplace = when {
                !existing.isRemoteUnverified && resource.isRemoteUnverified -> false
                resource.revision > existing.revision -> true
                resource.revision == existing.revision && !resource.isRemoteUnverified -> true
                resource.revision == existing.revision &&
                    existing.trustState == MeshTrustState.LEGACY_UNSIGNED &&
                    resource.trustState != MeshTrustState.LEGACY_UNSIGNED -> true
                else -> false
            }
            if (canReplace) current[index] = resource
        }
        val deduplicated = current.distinctBy { it.id }
        store.write(JSONArray(deduplicated.map(::resourceToJson)))
        return deduplicated.first { it.id == resource.id }
    }

    private fun resourceToJson(resource: ResourceRecord): JSONObject = JSONObject().apply {
        put("id", resource.id)
        put("provider_node_id", resource.providerNodeId)
        put("provider_alias", resource.providerAlias)
        put("category", resource.category.name)
        put("description", resource.description)
        put("quantity", resource.quantity)
        put("unit", resource.unit)
        put("approximate_area", resource.approximateArea)
        resource.approximateLocation?.let {
            put("latitude", it.latitude)
            put("longitude", it.longitude)
        }
        put("availability", resource.availability.name)
        put("created_at", resource.createdAtMillis)
        put("updated_at", resource.updatedAtMillis)
        put("revision", resource.revision)
        put("remote_unverified", resource.isRemoteUnverified)
        put("trust_state", resource.trustState.name)
        resource.originFingerprint?.let { put("origin_fingerprint", it) }
        resource.expiresAtMillis?.let { put("expires_at", it) }
    }

    companion object {
        fun inMemory(): ResourceStore = ResourceStore(JsonPreferencesStore.inMemory())
    }
}

class AssistanceResponseStore private constructor(private val store: JsonPreferencesStore) {
    constructor(context: Context) : this(JsonPreferencesStore(context, "resqmesh_responses", "records"))

    fun listForEmergency(emergencyId: String): List<AssistanceResponse> = list().filter { it.emergencyId == emergencyId }

    fun list(): List<AssistanceResponse> = store.read().toList { json ->
        runCatching {
            val remoteUnverified = json.optBoolean("remote_unverified", false)
            val trustState = runCatching {
                MeshTrustState.valueOf(json.optString("trust_state"))
            }.getOrNull() ?: if (remoteUnverified) MeshTrustState.LEGACY_UNSIGNED else MeshTrustState.LOCAL
            val fingerprint = json.optString("origin_fingerprint").takeIf { it.isNotBlank() }
            val record = MeshRecord(
                recordId = json.optString("id"),
                type = MeshRecordType.RESPONSE,
                originId = json.optString("responder_node_id"),
                revision = json.optInt("revision", 1),
                createdAtMillis = json.optLong("created_at", 1L),
                updatedAtMillis = json.optLong("updated_at", 1L),
                body = json,
                trustState = trustState,
                originFingerprint = fingerprint
            )
            json.toAssistanceResponse(record)?.copy(
                isRemoteUnverified = remoteUnverified,
                trustState = trustState,
                originFingerprint = fingerprint
            )
        }.getOrNull()
    }.distinctBy { it.id }

    fun save(response: AssistanceResponse): AssistanceResponse {
        val current = list().toMutableList()
        val index = current.indexOfFirst { it.id == response.id }
        if (index < 0) {
            current += response
        } else {
            val existing = current[index]
            val canReplace = when {
                !existing.isRemoteUnverified && response.isRemoteUnverified -> false
                response.revision > existing.revision -> true
                response.revision == existing.revision && !response.isRemoteUnverified -> true
                response.revision == existing.revision &&
                    existing.trustState == MeshTrustState.LEGACY_UNSIGNED &&
                    response.trustState != MeshTrustState.LEGACY_UNSIGNED -> true
                else -> false
            }
            if (canReplace) current[index] = response
        }
        val deduplicated = current.distinctBy { it.id }
        store.write(JSONArray(deduplicated.map(::responseToJson)))
        return deduplicated.first { it.id == response.id }
    }

    private fun responseToJson(response: AssistanceResponse): JSONObject = JSONObject().apply {
        put("id", response.id)
        put("emergency_id", response.emergencyId)
        put("responder_node_id", response.responderNodeId)
        put("responder_alias", response.responderAlias)
        put("assistance_type", response.assistanceType.name)
        put("message", response.message)
        put("status", response.status.name)
        put("created_at", response.createdAtMillis)
        put("updated_at", response.updatedAtMillis)
        put("revision", response.revision)
        put("remote_unverified", response.isRemoteUnverified)
        put("trust_state", response.trustState.name)
        response.originFingerprint?.let { put("origin_fingerprint", it) }
    }

    companion object {
        fun inMemory(): AssistanceResponseStore = AssistanceResponseStore(JsonPreferencesStore.inMemory())
    }
}

class SafetyPreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("resqmesh_safety", Context.MODE_PRIVATE)

    fun load(): SafetyPreferences {
        val contacts = runCatching { JSONArray(preferences.getString("contacts", "[]") ?: "[]") }.getOrElse { JSONArray() }
            .toList { json ->
                TrustedContact(
                    id = json.optString("id"),
                    alias = json.optString("alias"),
                    contactInfo = json.optString("contact_info"),
                    notes = json.optString("notes")
                )
            }
        return SafetyPreferences(
            discreetSosEnabled = preferences.getBoolean("discreet_sos", false),
            contacts = contacts,
            sharePreciseLocationByDefault = preferences.getBoolean("precise_location", false)
        )
    }

    fun save(value: SafetyPreferences) {
        preferences.edit()
            .putBoolean("discreet_sos", value.discreetSosEnabled)
            .putBoolean("precise_location", value.sharePreciseLocationByDefault)
            .putString("contacts", JSONArray(value.contacts.map {
                JSONObject().apply {
                    put("id", it.id)
                    put("alias", it.alias)
                    put("contact_info", it.contactInfo)
                    put("notes", it.notes)
                }
            }).toString())
            .commit()
    }
}

private inline fun <T> JSONArray.toList(transform: (JSONObject) -> T?): List<T> = buildList {
    for (index in 0 until length()) {
        optJSONObject(index)?.let(transform)?.let(::add)
    }
}
