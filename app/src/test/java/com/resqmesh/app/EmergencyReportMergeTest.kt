package com.resqmesh.app

import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencyReportMerge
import com.resqmesh.app.data.EmergencyReportStatus
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
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
        val received = original.copy(
            description = "Received copy",
            status = EmergencyReportStatus.RECEIVED_FROM_PEER,
            receivedFromPeerId = "peer-b"
        )

        val merged = EmergencyReportMerge.upsert(listOf(original), received)

        assertEquals(1, merged.size)
        assertEquals("Received copy", merged.single().description)
        assertEquals(EmergencyReportStatus.RECEIVED_FROM_PEER, merged.single().status)
    }
}
