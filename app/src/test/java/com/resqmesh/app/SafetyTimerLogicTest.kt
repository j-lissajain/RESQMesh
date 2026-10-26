package com.resqmesh.app

import com.resqmesh.app.data.SafetyTimerLogic
import com.resqmesh.app.data.SafetyTimerStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyTimerLogicTest {
    @Test
    fun activeTimerBecomesDueAfterDeadline() {
        val active = SafetyTimerLogic.start("Trip", 60_000L, 1_000L, true)

        assertEquals(SafetyTimerStatus.ACTIVE, SafetyTimerLogic.refresh(active, 60_999L).status)
        assertEquals(SafetyTimerStatus.DUE, SafetyTimerLogic.refresh(active, 61_000L).status)
    }

    @Test
    fun timerStartClampsVeryShortDurations() {
        val state = SafetyTimerLogic.start("", 1L, 100L, false)

        assertEquals(60_100L, state.dueAtMillis)
    }
}
