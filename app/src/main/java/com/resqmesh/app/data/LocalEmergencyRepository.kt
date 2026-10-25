package com.resqmesh.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class LocalEmergencyRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun list(): List<EmergencyReport> = loadStoredReports()

    fun save(report: EmergencyReport) {
        val reports = loadStoredReports()
            .filterNot { it.id == report.id }
            .toMutableList()
        reports += report
        val encoded = JSONArray().apply {
            reports.forEach { put(it.toJson()) }
        }
        preferences.edit().putString(KEY_REPORTS, encoded.toString()).apply()
    }

    private fun loadStoredReports(): List<EmergencyReport> {
        val raw = preferences.getString(KEY_REPORTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    parseReport(array.optJSONObject(index))?.let(::add)
                }
            }.sortedByDescending(EmergencyReport::createdAtMillis)
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun parseReport(json: JSONObject?): EmergencyReport? {
        if (json == null) return null
        val id = json.optString(KEY_ID).takeIf { it.isNotBlank() } ?: return null
        val type = EmergencyType.fromStoredValue(json.optString(KEY_TYPE)) ?: return null
        val severity = EmergencySeverity.fromStoredValue(json.optString(KEY_SEVERITY)) ?: return null
        val description = json.optString(KEY_DESCRIPTION).trim()
        val createdAtMillis = json.optLong(KEY_CREATED_AT, Long.MIN_VALUE)
        val status = EmergencyReportStatus.fromStoredValue(json.optString(KEY_STATUS)) ?: return null
        if (description.isBlank() || createdAtMillis == Long.MIN_VALUE) return null

        return EmergencyReport(
            id = id,
            type = type,
            severity = severity,
            description = description,
            createdAtMillis = createdAtMillis,
            status = status,
            location = parseLocation(json.optJSONObject(KEY_LOCATION))
        )
    }

    private fun parseLocation(json: JSONObject?): EmergencyLocation? {
        if (json == null) return null
        val latitude = json.optDouble(KEY_LATITUDE, Double.NaN)
        val longitude = json.optDouble(KEY_LONGITUDE, Double.NaN)
        val timestampMillis = json.optLong(KEY_LOCATION_TIMESTAMP, Long.MIN_VALUE)
        val source = EmergencyLocationSource.fromStoredValue(json.optString(KEY_LOCATION_SOURCE))
            ?: return null
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0 ||
            timestampMillis == Long.MIN_VALUE
        ) {
            return null
        }
        val accuracy = if (json.has(KEY_ACCURACY)) {
            json.optDouble(KEY_ACCURACY, Double.NaN).takeIf { it.isFinite() }?.toFloat()
        } else {
            null
        }
        return EmergencyLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracy,
            timestampMillis = timestampMillis,
            source = source,
            isVerified = json.optBoolean(KEY_VERIFIED, false)
        )
    }

    private fun EmergencyReport.toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_TYPE, type.name)
        put(KEY_SEVERITY, severity.name)
        put(KEY_DESCRIPTION, description)
        put(KEY_CREATED_AT, createdAtMillis)
        put(KEY_STATUS, status.name)
        location?.let { put(KEY_LOCATION, it.toJson()) }
    }

    private fun EmergencyLocation.toJson(): JSONObject = JSONObject().apply {
        put(KEY_LATITUDE, latitude)
        put(KEY_LONGITUDE, longitude)
        accuracyMeters?.let { put(KEY_ACCURACY, it) }
        put(KEY_LOCATION_TIMESTAMP, timestampMillis)
        put(KEY_LOCATION_SOURCE, source.name)
        put(KEY_VERIFIED, isVerified)
    }

    private companion object {
        const val PREFERENCES_NAME = "resqmesh_local_emergency_reports"
        const val KEY_REPORTS = "reports"
        const val KEY_ID = "id"
        const val KEY_TYPE = "type"
        const val KEY_SEVERITY = "severity"
        const val KEY_DESCRIPTION = "description"
        const val KEY_CREATED_AT = "created_at"
        const val KEY_STATUS = "status"
        const val KEY_LOCATION = "location"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_ACCURACY = "accuracy_meters"
        const val KEY_LOCATION_TIMESTAMP = "location_timestamp"
        const val KEY_LOCATION_SOURCE = "location_source"
        const val KEY_VERIFIED = "verified"
    }
}
