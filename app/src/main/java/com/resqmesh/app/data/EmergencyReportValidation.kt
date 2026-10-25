package com.resqmesh.app.data

object EmergencyReportValidation {
    fun isDescriptionValid(description: String): Boolean = description.trim().isNotEmpty()

    fun parseManualLocation(
        latitudeText: String,
        longitudeText: String,
        timestampMillis: Long
    ): EmergencyLocation? {
        val latitude = latitudeText.trim().toDoubleOrNull() ?: return null
        val longitude = longitudeText.trim().toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return EmergencyLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = null,
            timestampMillis = timestampMillis,
            source = EmergencyLocationSource.MANUAL,
            isVerified = false
        )
    }
}
