package com.resqmesh.app

import com.resqmesh.app.data.EmergencyRelayQueueCodec
import com.resqmesh.app.data.EmergencyRelayQueueItem
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
import com.resqmesh.app.data.RelayPeerDelivery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyRelayQueueCodecTest {
    @Test
    fun persistentQueueEntryRoundTripsDeliveryAttemptsAndExclusions() {
        val item = EmergencyRelayQueueItem(
            report = EmergencyReport(
                id = "queued-1",
                type = EmergencyType.TRAPPED_PERSON,
                severity = EmergencySeverity.CRITICAL,
                description = "Queued SOS",
                createdAtMillis = 10L,
                contentRevision = 3
            ),
            queuedAtMillis = 20L,
            expiresAtMillis = 100_000L,
            excludedPeerIds = setOf("sender-a"),
            deliveries = mapOf(
                "peer-b" to RelayPeerDelivery(
                    attempts = 2,
                    nextAttemptAtMillis = 500L,
                    acknowledged = false,
                    lastError = "temporary BLE failure"
                )
            )
        )

        val restored = EmergencyRelayQueueCodec.decode(
            EmergencyRelayQueueCodec.encode(listOf(item)).toString()
        )

        assertEquals(listOf(item), restored)
    }

    @Test
    fun corruptedQueuePayloadIsDiscardedSafely() {
        assertTrue(EmergencyRelayQueueCodec.decode("not-json").isEmpty())
    }
}
