package com.resqmesh.app

import com.resqmesh.app.ui.MainNavigationState
import com.resqmesh.app.ui.MainTab
import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigationStateTest {
    @Test
    fun selectingTabs_changesOnlyTheSelectedDestination() {
        val state = MainNavigationState()

        assertEquals(MainTab.HOME, state.selectedTab)
        assertEquals(MainTab.MAP, state.select(MainTab.MAP).selectedTab)
        assertEquals(MainTab.PROFILE, state.select(MainTab.PROFILE).selectedTab)
    }
}
