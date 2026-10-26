package com.resqmesh.app.data

import kotlin.math.abs

data class MatchSuggestion(
    val volunteer: VolunteerProfile? = null,
    val resource: ResourceRecord? = null,
    val score: Int,
    val reasons: List<String>,
    val approximateDistanceKm: Double? = null,
    val freshnessLabel: String
)

object EmergencyMatchingEngine {
    fun volunteersFor(
        emergency: EmergencyReport,
        volunteers: List<VolunteerProfile>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<MatchSuggestion> = volunteers
        .filter { it.availability == VolunteerAvailability.AVAILABLE }
        .filter { it.expiresAtMillis == null || it.expiresAtMillis > nowMillis }
        .map { volunteer ->
            val reasons = mutableListOf<String>()
            var score = severityScore(emergency.severity)
            val requiredSkill = requiredSkill(emergency.type)
            if (requiredSkill == null || requiredSkill in volunteer.skills) {
                score += 50
                reasons += if (requiredSkill == null) "General support is suitable" else "Skill matches ${requiredSkill.label}"
            } else {
                score -= 20
                reasons += "Skill match is limited"
            }
            if (volunteer.transportAvailable) {
                score += 10
                reasons += "Transport available"
            }
            if (volunteer.operatingArea.isNotBlank()) reasons += "Operating area: ${volunteer.operatingArea}"
            MatchSuggestion(volunteer = volunteer, score = score, reasons = reasons, freshnessLabel = freshness(volunteer.updatedAtMillis, nowMillis))
        }
        .sortedWith(compareByDescending<MatchSuggestion> { it.score }.thenBy { it.volunteer?.alias.orEmpty() })

    fun resourcesFor(
        emergency: EmergencyReport,
        resources: List<ResourceRecord>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<MatchSuggestion> = resources
        .filter { it.availability == ResourceAvailability.AVAILABLE }
        .filter { it.expiresAtMillis == null || it.expiresAtMillis > nowMillis }
        .map { resource ->
            val reasons = mutableListOf<String>()
            var score = severityScore(emergency.severity)
            val category = requiredResource(emergency.type)
            if (category == null || resource.category == category) {
                score += 50
                reasons += if (category == null) "General assistance resource" else "Category matches ${category.label}"
            } else score -= 15
            if (resource.quantity > 0.0) score += 10
            if (resource.approximateArea.isNotBlank()) reasons += "Area: ${resource.approximateArea}"
            val distance = emergency.location?.let { origin ->
                resource.approximateLocation?.let { target ->
                    distanceKm(origin.latitude, origin.longitude, target.latitude, target.longitude)
                }
            }
            if (distance != null) {
                reasons += "Approximately %.1f km away".format(distance)
                score += (20 - distance.toInt()).coerceIn(-10, 20)
            }
            MatchSuggestion(resource = resource, score = score, reasons = reasons, approximateDistanceKm = distance, freshnessLabel = freshness(resource.updatedAtMillis, nowMillis))
        }
        .sortedWith(compareByDescending<MatchSuggestion> { it.score }.thenBy { it.resource?.description.orEmpty() })

    private fun severityScore(severity: EmergencySeverity): Int = when (severity) {
        EmergencySeverity.CRITICAL -> 40
        EmergencySeverity.HIGH -> 30
        EmergencySeverity.MEDIUM -> 20
        EmergencySeverity.LOW -> 10
    }

    private fun requiredSkill(type: EmergencyType): VolunteerSkill? = when (type) {
        EmergencyType.MEDICAL -> VolunteerSkill.MEDICAL_ASSISTANCE
        EmergencyType.FIRE, EmergencyType.FLOOD_RESCUE, EmergencyType.TRAPPED_PERSON -> VolunteerSkill.SEARCH_ASSISTANCE
        EmergencyType.FOOD_WATER -> VolunteerSkill.FOOD_DISTRIBUTION
        EmergencyType.SHELTER -> VolunteerSkill.SHELTER_ASSISTANCE
        else -> VolunteerSkill.GENERAL_SUPPORT
    }

    private fun requiredResource(type: EmergencyType): ResourceCategory? = when (type) {
        EmergencyType.FOOD_WATER -> ResourceCategory.DRINKING_WATER
        EmergencyType.SHELTER -> ResourceCategory.TEMPORARY_SHELTER
        EmergencyType.MEDICAL -> ResourceCategory.MEDICAL_SUPPLIES
        EmergencyType.FIRE, EmergencyType.FLOOD_RESCUE, EmergencyType.TRAPPED_PERSON -> ResourceCategory.RESCUE_EQUIPMENT
        else -> null
    }

    private fun freshness(updatedAtMillis: Long, nowMillis: Long): String {
        val age = (nowMillis - updatedAtMillis).coerceAtLeast(0L)
        return when {
            age < 5 * 60_000L -> "Fresh"
            age < 60 * 60_000L -> "Updated within the hour"
            else -> "May be stale"
        }
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return earthRadius * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }
}
