package com.resqmesh.app

import com.resqmesh.app.bluetooth.BlePeer
import com.resqmesh.app.bluetooth.BlePeerRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class BlePeerRegistryTest {
    @Test
    fun sameBluetoothAddressWithNewAdvertisementIdentityDoesNotDuplicatePeer() {
        val first = BlePeer("A", 1L, -40, "AA:AA")
        val second = BlePeer("B", 2L, -41, "AA:AA")

        val merged = BlePeerRegistry.merge(mapOf(first.anonymousId to first), second)

        assertEquals(1, merged.size)
        assertEquals(second, merged.values.single())
    }

    @Test
    fun peerOrderingIsStableAgainstLastSeenChanges() {
        val peers = listOf(
            BlePeer("B", 100L, -30, "BB"),
            BlePeer("A", 1L, -60, "AA")
        )

        assertEquals(listOf("A", "B"), BlePeerRegistry.stableOrder(peers).map { it.anonymousId })
    }
}
