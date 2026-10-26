package com.resqmesh.app

import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencyReportMerge
import com.resqmesh.app.data.EmergencyReportStatus
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
import com.resqmesh.app.data.bumpContentRevision
import com.resqmesh.app.data.withLifecycleStatus
import com.resqmesh.app.data.EmergencyLifecycleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EmergencyRevisionTest {
    private val original = EmergencyReport(
        id = "revision-test",
        type = EmergencyType.MEDICAL,
        severity = EmergencySeverity.HIGH,
        description = "Need assistance",
        createdAtMillis = 10L,
        originId = "origin-a"
    )

    @Test
    fun newReportsStartAtRevisionOneAndContentUpdateBumpsRevision() {
        val updated = original.copy(description = "Need urgent assistance").bumpContentRevision()

        assertEquals(1, original.contentRevision)
        assertEquals(2, updated.contentRevision)
        assertEquals(original.id, updated.id)
    }

    @Test
    fun transportStatusDoesNotChangeContentRevision() {
        val acknowledged = original.copy(status = EmergencyReportStatus.TRANSPORT_ACKNOWLEDGED)

        assertEquals(original.contentRevision, acknowledged.contentRevision)
        assertEquals(original.description, acknowledged.description)
    }

    @Test
    fun staleRevisionCannotReplaceNewerContent() {
        val current = original.copy(description = "Newer").bumpContentRevision()
        val stale = original.copy(description = "Older")

        val merged = EmergencyReportMerge.upsert(listOf(current), stale).single()

        assertEquals(2, merged.contentRevision)
        assertEquals("Newer", merged.description)
    }

    @Test
    fun equalRevisionDoesNotReplaceContentButLocalStatusCanChange() {
        val statusUpdate = original.copy(
            description = "Must not replace content",
            status = EmergencyReportStatus.QUEUED_FOR_FORWARDING
        )

        val merged = EmergencyReportMerge.upsert(listOf(original), statusUpdate).single()

        assertEquals(original.description, merged.description)
        assertEquals(EmergencyReportStatus.QUEUED_FOR_FORWARDING, merged.status)
        assertEquals(1, merged.contentRevision)
    }

    @Test
    fun unverifiedRemoteRevisionCannotReplaceTrustedOriginatorContent() {
        val remote = original.copy(
            description = "Unverified remote rewrite",
            status = EmergencyReportStatus.RECEIVED_FROM_PEER,
            receivedFromPeerId = "peer-b",
            contentRevision = 2,
            contentTrust = com.resqmesh.app.data.EmergencyContentTrust.REMOTE_UNVERIFIED
        )

        val merged = EmergencyReportMerge.upsert(listOf(original), remote).single()

        assertSame(original, merged)
    }

    @Test
    fun lifecycleUpdateIsVersionedAndKeepsTheEmergencyId() {
        val cancelled = original.withLifecycleStatus(EmergencyLifecycleStatus.CANCELLED)

        assertEquals(original.id, cancelled.id)
        assertEquals(2, cancelled.contentRevision)
        assertEquals(EmergencyLifecycleStatus.CANCELLED, cancelled.lifecycleStatus)
    }
}
