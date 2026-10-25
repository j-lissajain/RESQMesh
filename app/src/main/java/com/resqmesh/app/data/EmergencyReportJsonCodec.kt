package com.resqmesh.app.data

import org.json.JSONException
import org.json.JSONObject

object EmergencyReportJsonCodec {
    fun encode(report: EmergencyReport): JSONObject = EmergencyReportStorageCodec.encode(report).toJson()

    fun decode(json: JSONObject?): EmergencyReport? {
        if (json == null) return null
        return try {
            EmergencyReportStorageCodec.decode(
                StoredEmergencyReport(
                    id = json.optString(KEY_ID),
                    typeName = json.optString(KEY_TYPE),
                    severityName = json.optString(KEY_SEVERITY),
                    description = json.optString(KEY_DESCRIPTION).trim(),
                    createdAtMillis = json.optLong(KEY_CREATED_AT, Long.MIN_VALUE),
                    statusName = json.optString(KEY_STATUS),
                    locationDescription = json.optString(KEY_LOCATION_DESCRIPTION)
                        .trim()
                        .takeIf { it.isNotBlank() },
                    location = json.optJSONObject(KEY_LOCATION)?.let { location ->
                        StoredEmergencyLocation(
                            latitude = location.optDouble(KEY_LATITUDE, Double.NaN),
                            longitude = location.optDouble(KEY_LONGITUDE, Double.NaN),
                            accuracyMeters = if (location.has(KEY_ACCURACY)) {
                                location.optDouble(KEY_ACCURACY, Double.NaN).takeIf { it.isFinite() }?.toFloat()
                            } else null,
                            timestampMillis = location.optLong(KEY_LOCATION_TIMESTAMP, Long.MIN_VALUE),
                            sourceName = location.optString(KEY_LOCATION_SOURCE),
                            verified = location.optBoolean(KEY_VERIFIED, false)
                        )
                    }
                )
            )
        } catch (_: JSONException) {
            null
        }
    }

    private fun StoredEmergencyReport.toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_TYPE, typeName)
        put(KEY_SEVERITY, severityName)
        put(KEY_DESCRIPTION, description)
        put(KEY_CREATED_AT, createdAtMillis)
        put(KEY_STATUS, statusName)
        locationDescription?.let { put(KEY_LOCATION_DESCRIPTION, it) }
        location?.let { value ->
            put(KEY_LOCATION, JSONObject().apply {
                put(KEY_LATITUDE, value.latitude)
                put(KEY_LONGITUDE, value.longitude)
                value.accuracyMeters?.let { put(KEY_ACCURACY, it) }
                put(KEY_LOCATION_TIMESTAMP, value.timestampMillis)
                put(KEY_LOCATION_SOURCE, value.sourceName)
                put(KEY_VERIFIED, value.verified)
            })
        }
    }

    private const val KEY_ID = "id"
    private const val KEY_TYPE = "type"
    private const val KEY_SEVERITY = "severity"
    private const val KEY_DESCRIPTION = "description"
    private const val KEY_CREATED_AT = "created_at"
    private const val KEY_STATUS = "status"
    private const val KEY_LOCATION_DESCRIPTION = "location_description"
    private const val KEY_LOCATION = "location"
    private const val KEY_LATITUDE = "latitude"
    private const val KEY_LONGITUDE = "longitude"
    private const val KEY_ACCURACY = "accuracy_meters"
    private const val KEY_LOCATION_TIMESTAMP = "location_timestamp"
    private const val KEY_LOCATION_SOURCE = "location_source"
    private const val KEY_VERIFIED = "verified"
}
