package com.resqmesh.app

import com.resqmesh.app.data.AssistanceResponse
import com.resqmesh.app.data.EmergencyEnvelopeRules
import com.resqmesh.app.data.MeshRecordCodec
import com.resqmesh.app.data.ResourceCategory
import com.resqmesh.app.data.ResourceRecord
import com.resqmesh.app.data.ResponseStatus
import com.resqmesh.app.data.VolunteerAvailability
import com.resqmesh.app.data.VolunteerProfile
import com.resqmesh.app.data.VolunteerSkill
import com.resqmesh.app.data.toMeshRecord
import com.resqmesh.app.data.toResourceRecord
import com.resqmesh.app.data.toVolunteerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinationModelsTest {
    @Test
    fun volunteerRecordRoundTripsWithoutPrivateContactByDefault() {
        val profile = VolunteerProfile(
            nodeId = "node-a",
            alias = "Asha",
            skills = setOf(VolunteerSkill.FIRST_AID, VolunteerSkill.FOOD_DISTRIBUTION),
            availability = VolunteerAvailability.AVAILABLE,
            operatingArea = "Central Nagpur"
        )

        val decodedRecord = MeshRecordCodec.decode(MeshRecordCodec.encode(profile.toMeshRecord()))
        val restored = decodedRecord?.body?.toVolunteerProfile(decodedRecord)

        assertNotNull(decodedRecord)
        assertEquals(profile.id, restored?.id)
        assertEquals(profile.skills, restored?.skills)
        assertNull(restored?.contactInfo)
    }

    @Test
    fun resourceRecordRoundTripsAndUsesApproximateArea() {
        val resource = ResourceRecord(
            providerNodeId = "node-a",
            providerAlias = "Community hall",
            category = ResourceCategory.DRINKING_WATER,
            description = "Sealed bottles",
            quantity = 24.0,
            unit = "bottles",
            approximateArea = "Ward 12"
        )

        val decodedRecord = MeshRecordCodec.decode(MeshRecordCodec.encode(resource.toMeshRecord()))
        val restored = decodedRecord?.body?.toResourceRecord(decodedRecord)

        assertEquals(resource.category, restored?.category)
        assertEquals(resource.quantity, restored?.quantity ?: 0.0, 0.0)
        assertEquals("Ward 12", restored?.approximateArea)
    }

    @Test
    fun meshCodecRejectsInvalidTtlAndOversizedPayload() {
        val response = AssistanceResponse(
            emergencyId = "emergency-1",
            responderNodeId = "node-a",
            responderAlias = "Volunteer",
            assistanceType = VolunteerSkill.GENERAL_SUPPORT,
            status = ResponseStatus.OFFERED
        )
        val invalid = response.toMeshRecord().copy(
            hopCount = 1,
            remainingTtl = EmergencyEnvelopeRules.MAX_HOP_LIMIT,
            lastHopId = "node-a"
        )
        assertTrue(MeshRecordCodec.validate(invalid) != null)
        assertTrue(runCatching { MeshRecordCodec.encode(invalid) }.isFailure)
    }
}
