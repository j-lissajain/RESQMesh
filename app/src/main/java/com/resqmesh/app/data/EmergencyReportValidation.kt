package com.resqmesh.app.data

object EmergencyReportValidation {
    const val MAX_DESCRIPTION_CHARS = 240
    const val MAX_LOCATION_DESCRIPTION_CHARS = 180

    fun isDescriptionValid(description: String): Boolean =
        isSafeText(description, MAX_DESCRIPTION_CHARS)

    fun isLocationDescriptionValid(description: String): Boolean =
        description.isBlank() || isSafeText(description, MAX_LOCATION_DESCRIPTION_CHARS)

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

    private fun isSafeText(value: String, maxChars: Int): Boolean =
        value.trim().isNotEmpty() &&
            value.length <= maxChars &&
            value.none { it.isISOControl() }
}
