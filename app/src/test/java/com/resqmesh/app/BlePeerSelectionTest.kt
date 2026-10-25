package com.resqmesh.app

import com.resqmesh.app.bluetooth.BlePeerSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlePeerSelectionTest {
    @Test
    fun currentActivePeerIsRetained() {
        assertEquals("peer-b", BlePeerSelection.retainOrChoose("peer-b", listOf("peer-a", "peer-b")))
    }

    @Test
    fun missingSelectionChoosesFirstReadyPeer() {
        assertEquals("peer-a", BlePeerSelection.retainOrChoose(null, listOf("peer-a", "peer-b")))
        assertEquals("peer-a", BlePeerSelection.retainOrChoose("peer-z", listOf("peer-a", "peer-b")))
        assertNull(BlePeerSelection.retainOrChoose("peer-z", emptyList()))
    }
}
