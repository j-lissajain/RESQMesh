package com.resqmesh.app

import com.resqmesh.app.data.EmergencyContentTrust
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencyReportMerge
import com.resqmesh.app.data.EmergencyReportStatus
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
import com.resqmesh.app.data.bumpContentRevision
import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyReportMergeTest {
    @Test
    fun sameEmergencyIdIsUpdatedInsteadOfDuplicated() {
        val original = EmergencyReport(
            id = "same-id",
            type = EmergencyType.MEDICAL,
            severity = EmergencySeverity.HIGH,
            description = "Initial",
            createdAtMillis = 10L
        )
        val updated = original.copy(description = "Updated locally").bumpContentRevision()

        val merged = EmergencyReportMerge.upsert(listOf(original), updated)

        assertEquals(1, merged.size)
        assertEquals("Updated locally", merged.single().description)
        assertEquals(EmergencyReportStatus.LOCAL_ONLY, merged.single().status)
    }

    @Test
    fun relayedCopyDoesNotOverwriteLocalOriginatorStatus() {
        val original = EmergencyReport(
            id = "same-id",
            type = EmergencyType.MEDICAL,
            severity = EmergencySeverity.HIGH,
            description = "Initial",
            createdAtMillis = 10L,
            status = EmergencyReportStatus.TRANSPORT_ACKNOWLEDGED,
            originId = "origin-a"
        )
        val received = original.copy(
            description = "Received copy",
            status = EmergencyReportStatus.RECEIVED_FROM_PEER,
            receivedFromPeerId = "peer-b",
            hopCount = 1,
            remainingTtl = 2,
            lastHopId = "peer-b"
        )

        val merged = EmergencyReportMerge.upsert(listOf(original), received)

        assertEquals(1, merged.size)
        assertEquals("Initial", merged.single().description)
        assertEquals(EmergencyReportStatus.TRANSPORT_ACKNOWLEDGED, merged.single().status)
        assertEquals(null, merged.single().receivedFromPeerId)
    }

    @Test
    fun receivedCopyCanReceiveLaterContentUpdate() {
        val first = EmergencyReport(
            id = "same-id",
            type = EmergencyType.MEDICAL,
            severity = EmergencySeverity.HIGH,
            description = "First received",
            createdAtMillis = 10L,
            status = EmergencyReportStatus.RECEIVED_FROM_PEER,
            receivedFromPeerId = "peer-b"
        )
        val later = first.copy(
            description = "Updated received copy",
            receivedFromPeerId = "peer-c",
            hopCount = 2,
            remainingTtl = 1,
            lastHopId = "peer-c",
            contentRevision = 2
        )

        val merged = EmergencyReportMerge.upsert(listOf(first), later)

        assertEquals(1, merged.size)
        assertEquals("Updated received copy", merged.single().description)
        assertEquals("peer-c", merged.single().receivedFromPeerId)
        assertEquals(2, merged.single().hopCount)
    }

    @Test
    fun localTransportStatusUpdateIsPreserved() {
        val original = EmergencyReport(
            id = "same-id",
            type = EmergencyType.FIRE,
            severity = EmergencySeverity.CRITICAL,
            description = "Fire near the hall",
            createdAtMillis = 10L
        )
        val acknowledged = original.copy(status = EmergencyReportStatus.TRANSPORT_ACKNOWLEDGED)

        val merged = EmergencyReportMerge.upsert(listOf(original), acknowledged)

        assertEquals(EmergencyReportStatus.TRANSPORT_ACKNOWLEDGED, merged.single().status)
        assertEquals("Fire near the hall", merged.single().description)
    }

    @Test
    fun remoteAuthenticatedDoesNotOverwriteLocalOriginatedReportEvenWithHigherRevision() {
        val local = EmergencyReport(
            id = "same-id",
            type = EmergencyType.MEDICAL,
            severity = EmergencySeverity.CRITICAL,
            description = "Local emergency",
            createdAtMillis = 10L,
            status = EmergencyReportStatus.LOCAL_ONLY,
            originId = "node-a",
            contentRevision = 1,
            contentTrust = EmergencyContentTrust.LOCAL_ORIGINATED
        )
        val remoteAuth = local.copy(
            description = "Remote forged update",
            status = EmergencyReportStatus.RECEIVED_FROM_PEER,
            receivedFromPeerId = "peer-b",
            contentRevision = 3,
            contentTrust = EmergencyContentTrust.REMOTE_AUTHENTICATED
        )

        val merged = EmergencyReportMerge.upsert(listOf(local), remoteAuth)

        assertEquals(1, merged.size)
        assertEquals("Local emergency", merged.single().description)
        assertEquals(EmergencyContentTrust.LOCAL_ORIGINATED, merged.single().contentTrust)
        assertEquals(1, merged.single().contentRevision)
    }

    @Test
    fun remoteUnverifiedDoesNotOverwriteOrDowngradeRemoteAuthenticated() {
        val authenticated = EmergencyReport(
            id = "same-id",
            type = EmergencyType.MEDICAL,
            severity = EmergencySeverity.HIGH,
            description = "Signed emergency",
            createdAtMillis = 10L,
            status = EmergencyReportStatus.RECEIVED_FROM_PEER,
            receivedFromPeerId = "peer-a",
            contentRevision = 1,
            contentTrust = EmergencyContentTrust.REMOTE_AUTHENTICATED
        )
        val unverifiedHigherRevision = authenticated.copy(
            description = "Unsigned spoofed revision",
            receivedFromPeerId = "peer-b",
            contentRevision = 2,
            contentTrust = EmergencyContentTrust.REMOTE_UNVERIFIED
        )

        val merged = EmergencyReportMerge.upsert(listOf(authenticated), unverifiedHigherRevision)

        assertEquals(1, merged.size)
        assertEquals("Signed emergency", merged.single().description)
        assertEquals(EmergencyContentTrust.REMOTE_AUTHENTICATED, merged.single().contentTrust)
        assertEquals(1, merged.single().contentRevision)
    }

    @Test
    fun remoteUnverifiedUpgradesToRemoteAuthenticatedOnSameRevision() {
        val unverified = EmergencyReport(
            id = "same-id",
            type = EmergencyType.MEDICAL,
            severity = EmergencySeverity.HIGH,
            description = "Emergency report",
            createdAtMillis = 10L,
            status = EmergencyReportStatus.RECEIVED_FROM_PEER,
            receivedFromPeerId = "peer-a",
            contentRevision = 1,
            contentTrust = EmergencyContentTrust.REMOTE_UNVERIFIED
        )
        val authenticated = unverified.copy(
            receivedFromPeerId = "peer-b",
            contentTrust = EmergencyContentTrust.REMOTE_AUTHENTICATED
        )

        val merged = EmergencyReportMerge.upsert(listOf(unverified), authenticated)

        assertEquals(1, merged.size)
        assertEquals(EmergencyContentTrust.REMOTE_AUTHENTICATED, merged.single().contentTrust)
    }
}
