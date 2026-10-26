package com.resqmesh.app

import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
import com.resqmesh.app.data.EmergencyMatchingEngine
import com.resqmesh.app.data.ResourceAvailability
import com.resqmesh.app.data.ResourceCategory
import com.resqmesh.app.data.ResourceRecord
import com.resqmesh.app.data.VolunteerAvailability
import com.resqmesh.app.data.VolunteerProfile
import com.resqmesh.app.data.VolunteerSkill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchingEngineTest {
    private val emergency = EmergencyReport(
        id = "emergency",
        type = EmergencyType.MEDICAL,
        severity = EmergencySeverity.CRITICAL,
        description = "Need medical help",
        createdAtMillis = 1L
    )

    @Test
    fun matchingPrefersAvailableSkillAndRanksDeterministically() {
        val volunteers = listOf(
            VolunteerProfile(nodeId = "b", alias = "General", skills = setOf(VolunteerSkill.GENERAL_SUPPORT)),
            VolunteerProfile(nodeId = "a", alias = "Medic", skills = setOf(VolunteerSkill.MEDICAL_ASSISTANCE)),
            VolunteerProfile(nodeId = "c", alias = "Busy", skills = setOf(VolunteerSkill.MEDICAL_ASSISTANCE), availability = VolunteerAvailability.BUSY)
        )

        val matches = EmergencyMatchingEngine.volunteersFor(emergency, volunteers, nowMillis = 10_000L)

        assertEquals("Medic", matches.first().volunteer?.alias)
        assertTrue(matches.first().reasons.any { it.contains("Skill matches") })
        assertTrue(matches.none { it.volunteer?.alias == "Busy" })
    }

    @Test
    fun resourceMatchingFiltersUnavailableItemsAndExplainsCategory() {
        val resources = listOf(
            ResourceRecord(providerNodeId = "a", providerAlias = "Clinic", category = ResourceCategory.MEDICAL_SUPPLIES, description = "Bandages", quantity = 10.0, unit = "packs"),
            ResourceRecord(providerNodeId = "b", providerAlias = "Used", category = ResourceCategory.MEDICAL_SUPPLIES, description = "Empty", quantity = 0.0, unit = "packs", availability = ResourceAvailability.UNAVAILABLE)
        )

        val matches = EmergencyMatchingEngine.resourcesFor(emergency, resources, nowMillis = 10_000L)

        assertEquals(1, matches.size)
        assertTrue(matches.single().reasons.any { it.contains("Category matches") })
    }
}
