package com.resqmesh.app

import com.resqmesh.app.data.EmergencyEnvelope
import com.resqmesh.app.data.EmergencyEnvelopeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EmergencyEnvelopeTest {
    @Test
    fun originEnvelopeUsesZeroHopsAndFullTtl() {
        val origin = EmergencyEnvelopeRules.origin()
        assertEquals(0, origin.hopCount)
        assertEquals(EmergencyEnvelopeRules.MAX_HOP_LIMIT, origin.remainingTtl)
        assertNull(origin.lastHopId)
        assertNull(EmergencyEnvelopeRules.validate(origin))
    }

    @Test
    fun validRelayEnvelopeIsAccepted() {
        assertNull(
            EmergencyEnvelopeRules.validate(
                EmergencyEnvelope(
                    hopCount = 1,
                    remainingTtl = EmergencyEnvelopeRules.MAX_HOP_LIMIT - 1,
                    lastHopId = "peer-a"
                )
            )
        )
    }

    @Test
    fun invalidHopCountAndTtlAreRejected() {
        assertNotNull(EmergencyEnvelopeRules.validate(-1, 3, null))
        assertNotNull(EmergencyEnvelopeRules.validate(4, 0, "peer-a"))
        assertNotNull(EmergencyEnvelopeRules.validate(0, 4, null))
        assertNotNull(EmergencyEnvelopeRules.validate(2, 2, "peer-a"))
        assertNotNull(EmergencyEnvelopeRules.validate(1, 2, null))
        assertNotNull(EmergencyEnvelopeRules.validate(0, 3, "peer-a"))
        assertNotNull(EmergencyEnvelopeRules.validate(1, 2, "peer a"))
        assertNotNull(EmergencyEnvelopeRules.validate(1, 2, "peer\n a"))
    }
}
