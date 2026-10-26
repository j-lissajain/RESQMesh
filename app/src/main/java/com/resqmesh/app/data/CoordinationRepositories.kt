package com.resqmesh.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private class JsonPreferencesStore(
    context: Context,
    name: String,
    private val key: String
) {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val lock = Any()

    fun read(): JSONArray = synchronized(lock) {
        runCatching { JSONArray(preferences.getString(key, "[]") ?: "[]") }.getOrElse { JSONArray() }
    }

    fun write(array: JSONArray) = synchronized(lock) {
        preferences.edit().putString(key, array.toString()).commit()
    }
}

class VolunteerProfileStore(context: Context) {
    private val store = JsonPreferencesStore(context, "resqmesh_volunteers", "profiles")

    fun list(nowMillis: Long = System.currentTimeMillis()): List<VolunteerProfile> = store.read().toList { json ->
        runCatching {
            val record = MeshRecord(
                recordId = json.optString("id"),
                type = MeshRecordType.VOLUNTEER,
                originId = json.optString("node_id"),
                revision = json.optInt("revision", 1),
                createdAtMillis = json.optLong("updated_at", 1L),
                updatedAtMillis = json.optLong("updated_at", 1L),
                body = json
            )
            json.toVolunteerProfile(record)?.copy(isRemoteUnverified = json.optBoolean("remote_unverified", false))
        }.getOrNull()
    }.filter { it.expiresAtMillis == null || it.expiresAtMillis > nowMillis }

    fun save(profile: VolunteerProfile): VolunteerProfile {
        val current = list().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index < 0 || profile.revision >= current[index].revision) {
            if (index < 0) current += profile else current[index] = profile
        }
        store.write(JSONArray(current.map(::volunteerToJson)))
        return current.first { it.id == profile.id }
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
        profile.expiresAtMillis?.let { put("expires_at", it) }
    }
}

class ResourceStore(context: Context) {
    private val store = JsonPreferencesStore(context, "resqmesh_resources", "records")

    fun list(nowMillis: Long = System.currentTimeMillis()): List<ResourceRecord> = store.read().toList { json ->
        runCatching {
            val record = MeshRecord(
                recordId = json.optString("id"),
                type = MeshRecordType.RESOURCE,
                originId = json.optString("provider_node_id"),
                revision = json.optInt("revision", 1),
                createdAtMillis = json.optLong("created_at", 1L),
                updatedAtMillis = json.optLong("updated_at", 1L),
                body = json
            )
            json.toResourceRecord(record)?.copy(isRemoteUnverified = json.optBoolean("remote_unverified", false))
        }.getOrNull()
    }.filter { it.expiresAtMillis == null || it.expiresAtMillis > nowMillis }

    fun save(resource: ResourceRecord): ResourceRecord {
        val current = list().toMutableList()
        val index = current.indexOfFirst { it.id == resource.id }
        if (index < 0 || resource.revision >= current[index].revision) {
            if (index < 0) current += resource else current[index] = resource
        }
        store.write(JSONArray(current.map(::resourceToJson)))
        return current.first { it.id == resource.id }
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
        resource.expiresAtMillis?.let { put("expires_at", it) }
    }
}

class AssistanceResponseStore(context: Context) {
    private val store = JsonPreferencesStore(context, "resqmesh_responses", "records")

    fun listForEmergency(emergencyId: String): List<AssistanceResponse> = list().filter { it.emergencyId == emergencyId }

    fun list(): List<AssistanceResponse> = store.read().toList { json ->
        runCatching {
            val record = MeshRecord(
                recordId = json.optString("id"),
                type = MeshRecordType.RESPONSE,
                originId = json.optString("responder_node_id"),
                revision = json.optInt("revision", 1),
                createdAtMillis = json.optLong("created_at", 1L),
                updatedAtMillis = json.optLong("updated_at", 1L),
                body = json
            )
            json.toAssistanceResponse(record)?.copy(isRemoteUnverified = json.optBoolean("remote_unverified", false))
        }.getOrNull()
    }

    fun save(response: AssistanceResponse): AssistanceResponse {
        val current = list().toMutableList()
        val index = current.indexOfFirst { it.id == response.id }
        if (index < 0 || response.revision >= current[index].revision) {
            if (index < 0) current += response else current[index] = response
        }
        store.write(JSONArray(current.map(::responseToJson)))
        return current.first { it.id == response.id }
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
