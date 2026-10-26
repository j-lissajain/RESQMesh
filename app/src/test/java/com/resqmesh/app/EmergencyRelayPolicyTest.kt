package com.resqmesh.app

import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencyRelayPolicy
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
import com.resqmesh.app.data.RelayPeerDelivery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmergencyRelayPolicyTest {
    private fun report(hopCount: Int, remainingTtl: Int, lastHopId: String? = null) = EmergencyReport(
        id = "mesh-test",
        type = EmergencyType.FIRE,
        severity = EmergencySeverity.CRITICAL,
        description = "Evacuate",
        createdAtMillis = 1L,
        hopCount = hopCount,
        remainingTtl = remainingTtl,
        lastHopId = lastHopId
    )

    @Test
    fun aToBToCAdvancesEnvelopeWithoutChangingContentIdentity() {
        val atA = report(0, 3)
        val atB = EmergencyRelayPolicy.nextEnvelope(atA, "node-b")
        val atC = EmergencyRelayPolicy.nextEnvelope(
            atA.copy(hopCount = atB!!.hopCount, remainingTtl = atB.remainingTtl, lastHopId = atB.lastHopId),
            "node-c"
        )

        assertEquals(1, atB.hopCount)
        assertEquals(2, atB.remainingTtl)
        assertEquals("node-b", atB.lastHopId)
        assertEquals(2, atC?.hopCount)
        assertEquals(1, atC?.remainingTtl)
        assertEquals("node-c", atC?.lastHopId)
        assertEquals(atA.id, report(0, 3).id)
    }

    @Test
    fun ttlExhaustionAndInvalidMetadataStopForwarding() {
        assertNull(EmergencyRelayPolicy.nextEnvelope(report(3, 0, "node-b"), "node-c"))
        assertNull(EmergencyRelayPolicy.nextEnvelope(report(1, 2), "node-c"))
        assertNull(EmergencyRelayPolicy.nextEnvelope(report(0, 3, "spoofed"), "node-c"))
    }

    @Test
    fun immediateSenderAcknowledgedPeerAndRetryLimitAreSuppressed() {
        val eligible = EmergencyRelayPolicy.eligiblePeers(
            activePeerIds = setOf("peer-a", "peer-b", "peer-c"),
            immediateSenderId = "peer-a",
            excludedPeerIds = setOf("peer-c"),
            deliveries = mapOf(
                "peer-b" to RelayPeerDelivery(attempts = EmergencyRelayPolicy.MAX_ATTEMPTS),
                "peer-c" to RelayPeerDelivery(acknowledged = true)
            ),
            nowMillis = 100L
        )

        assertEquals(emptyList<String>(), eligible)
    }

    @Test
    fun retryBackoffIsBoundedAndIncreases() {
        assertEquals(2_000L, EmergencyRelayPolicy.backoffMillis(1))
        assertEquals(4_000L, EmergencyRelayPolicy.backoffMillis(2))
        assertEquals(EmergencyRelayPolicy.MAX_BACKOFF_MILLIS, EmergencyRelayPolicy.backoffMillis(20))
    }
}
