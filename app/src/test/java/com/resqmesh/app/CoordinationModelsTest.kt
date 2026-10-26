package com.resqmesh.app

import com.resqmesh.app.bluetooth.EmergencyRelayTransport
import com.resqmesh.app.bluetooth.EmergencyTransportResult
import com.resqmesh.app.bluetooth.MeshRecordQueueStore
import com.resqmesh.app.bluetooth.MeshRelayController
import com.resqmesh.app.bluetooth.MeshSeenStore
import com.resqmesh.app.bluetooth.RelayScheduler
import com.resqmesh.app.data.AssistanceResponse
import com.resqmesh.app.data.AssistanceResponseStore
import com.resqmesh.app.data.EmergencyContentTrust
import com.resqmesh.app.data.EmergencyEnvelopeRules
import com.resqmesh.app.data.EmergencyLifecycleStatus
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencyReportMerge
import com.resqmesh.app.data.EmergencyReportRepository
import com.resqmesh.app.data.EmergencyReportStatus
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
import com.resqmesh.app.data.MeshRecordCodec
import com.resqmesh.app.data.ResourceCategory
import com.resqmesh.app.data.ResourceRecord
import com.resqmesh.app.data.ResourceStore
import com.resqmesh.app.data.ResponseStatus
import com.resqmesh.app.data.VolunteerAvailability
import com.resqmesh.app.data.VolunteerProfile
import com.resqmesh.app.data.VolunteerProfileStore
import com.resqmesh.app.data.VolunteerSkill
import com.resqmesh.app.data.toAssistanceResponse
import com.resqmesh.app.data.toMeshRecord
import com.resqmesh.app.data.toResourceRecord
import com.resqmesh.app.data.toVolunteerProfile
import com.resqmesh.app.security.MeshTrustState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val oversized = AssistanceResponse(
            emergencyId = "emergency-1",
            responderNodeId = "node-a",
            responderAlias = "Volunteer",
            assistanceType = VolunteerSkill.GENERAL_SUPPORT,
            status = ResponseStatus.OFFERED,
            message = "m".repeat(MeshRecordCodec.MAX_BYTES + 100)
        ).toMeshRecord()
        assertNotNull(MeshRecordCodec.validate(oversized))
    }

    @Test
    fun incomingCoordinationRecordsPersistDeduplicateProtectLocalRecordsAndNotifyListenersEvenOnFinalHop() {
        val volunteerStore = VolunteerProfileStore.inMemory()
        val resourceStore = ResourceStore.inMemory()
        val responseStore = AssistanceResponseStore.inMemory()
        val emergencyRepo = InMemoryEmergencyRepo()
        val transport = FakeCoordinationTransport(mutableSetOf("peer-c"))
        val controller = MeshRelayController(
            queue = MeshRecordQueueStore.inMemory(),
            seen = MeshSeenStore.inMemory(),
            transport = transport,
            localNodeId = "node-b",
            volunteerStore = volunteerStore,
            resourceStore = resourceStore,
            responseStore = responseStore,
            emergencyRepository = emergencyRepo,
            nowMillis = { 1_000L },
            scheduler = RelayScheduler { _, action -> action() }
        )

        var observedRevision = 0L
        controller.addListener { state -> observedRevision = state.dataRevision }

        // Save a local volunteer first; incoming remote record with same ID must not overwrite local
        val localVolunteer = VolunteerProfile(
            id = "vol-local",
            nodeId = "node-b",
            alias = "Local Volunteer",
            skills = setOf(VolunteerSkill.FIRST_AID)
        )
        volunteerStore.save(localVolunteer)
        val spoofedRemote = localVolunteer.copy(alias = "Spoofed Remote", revision = 5).toMeshRecord()
        controller.onIncoming(spoofedRemote, "peer-a")
        assertEquals("Local Volunteer", volunteerStore.list().single { it.id == "vol-local" }.alias)
        assertFalse(volunteerStore.list().single { it.id == "vol-local" }.isRemoteUnverified)

        // Incoming remote volunteer on final hop (hopCount = 2, remainingTtl = 1) still updates store & notifies UI
        val remoteVolunteerRecord = VolunteerProfile(
            id = "vol-remote-1",
            nodeId = "node-a",
            alias = "Remote Medic",
            skills = setOf(VolunteerSkill.MEDICAL_ASSISTANCE),
            operatingArea = "Sitabuldi"
        ).toMeshRecord().copy(
            hopCount = 2,
            remainingTtl = 1,
            lastHopId = "peer-a",
            trustState = MeshTrustState.AUTHENTICATED_KNOWN,
            originFingerprint = "a".repeat(64)
        )
        val revBeforeVolunteer = observedRevision
        controller.onIncoming(remoteVolunteerRecord, "peer-a")
        assertTrue(observedRevision > revBeforeVolunteer)
        val savedRemoteVolunteer = volunteerStore.list().single { it.id == "vol-remote-1" }
        assertEquals("Remote Medic", savedRemoteVolunteer.alias)
        assertTrue(savedRemoteVolunteer.isRemoteUnverified)
        assertEquals(MeshTrustState.AUTHENTICATED_KNOWN, savedRemoteVolunteer.trustState)
        assertEquals("a".repeat(64), savedRemoteVolunteer.originFingerprint)

        // Incoming remote resource and response records notify listener and deduplicate by ID
        val remoteResource = ResourceRecord(
            id = "res-remote-1",
            providerNodeId = "node-a",
            providerAlias = "Relief Camp",
            category = ResourceCategory.DRINKING_WATER,
            description = "Clean water cans",
            quantity = 50.0,
            unit = "liters",
            approximateArea = "Dharampeth"
        ).toMeshRecord().copy(trustState = MeshTrustState.AUTHENTICATED_UNKNOWN, originFingerprint = "b".repeat(64))
        val revBeforeResource = observedRevision
        controller.onIncoming(remoteResource, "peer-a")
        assertTrue(observedRevision > revBeforeResource)
        assertEquals(1, resourceStore.list().size)
        assertEquals(MeshTrustState.AUTHENTICATED_UNKNOWN, resourceStore.list().single().trustState)

        val remoteResponse = AssistanceResponse(
            id = "resp-remote-1",
            emergencyId = "em-1",
            responderNodeId = "node-a",
            responderAlias = "Remote Medic",
            assistanceType = VolunteerSkill.MEDICAL_ASSISTANCE,
            status = ResponseStatus.OFFERED,
            message = "On my way"
        ).toMeshRecord().copy(trustState = MeshTrustState.AUTHENTICATED_KNOWN, originFingerprint = "a".repeat(64))
        val revBeforeResponse = observedRevision
        controller.onIncoming(remoteResponse, "peer-a")
        assertTrue(observedRevision > revBeforeResponse)
        assertEquals(1, responseStore.list().size)
        assertEquals("On my way", responseStore.list().single().message)

        // Emergency update preserves REMOTE_AUTHENTICATED trust and does not overwrite local emergencies
        val remoteEmergency = EmergencyReport(
            id = "em-remote-1",
            type = EmergencyType.MEDICAL,
            severity = EmergencySeverity.HIGH,
            description = "Initial description",
            createdAtMillis = 500L,
            status = EmergencyReportStatus.RECEIVED_FROM_PEER,
            originId = "node-a",
            receivedFromPeerId = "peer-a",
            contentRevision = 1,
            contentTrust = EmergencyContentTrust.REMOTE_AUTHENTICATED
        )
        emergencyRepo.save(remoteEmergency)
        controller.enqueueEmergencyLifecycleUpdate(
            remoteEmergency.copy(
                description = "Updated description",
                lifecycleStatus = EmergencyLifecycleStatus.RESOLVED,
                contentRevision = 2
            )
        )
        val updateRecord = transport.sentRecords.last().first
            .copy(trustState = MeshTrustState.AUTHENTICATED_KNOWN, originFingerprint = "a".repeat(64))
        controller.onIncoming(updateRecord, "peer-a")
        val updatedEmergency = emergencyRepo.list().single { it.id == "em-remote-1" }
        assertEquals(EmergencyLifecycleStatus.RESOLVED, updatedEmergency.lifecycleStatus)
        assertEquals("Updated description", updatedEmergency.description)
        assertEquals(EmergencyContentTrust.REMOTE_AUTHENTICATED, updatedEmergency.contentTrust)
        assertEquals(EmergencyReportStatus.RECEIVED_FROM_PEER, updatedEmergency.status)

        controller.close()
    }

    private class InMemoryEmergencyRepo : EmergencyReportRepository {
        private var items = emptyList<EmergencyReport>()
        override fun list(): List<EmergencyReport> = items
        override fun save(report: EmergencyReport): EmergencyReport? {
            items = EmergencyReportMerge.upsert(items, report)
            return items.firstOrNull { it.id == report.id }
        }
        override fun updateStatus(reportId: String, status: EmergencyReportStatus): EmergencyReport? {
            val existing = items.firstOrNull { it.id == reportId } ?: return null
            return save(existing.copy(status = status))
        }
    }

    private class FakeCoordinationTransport(
        private val peers: MutableSet<String>
    ) : EmergencyRelayTransport {
        val sentRecords = mutableListOf<Pair<com.resqmesh.app.data.MeshRecord, String>>()
        override fun connectedPeerIds(): Set<String> = peers
        override fun sendEmergencyToPeer(
            report: EmergencyReport,
            peerId: String,
            callback: (EmergencyTransportResult, String?) -> Unit
        ): Boolean = true
        override fun sendMeshRecordToPeer(
            record: com.resqmesh.app.data.MeshRecord,
            peerId: String,
            callback: (EmergencyTransportResult, String?) -> Unit
        ): Boolean {
            sentRecords += record to peerId
            callback(EmergencyTransportResult.ACKNOWLEDGED, null)
            return true
        }
        override fun addConnectionListener(listener: () -> Unit): () -> Unit = {}
    }
}
