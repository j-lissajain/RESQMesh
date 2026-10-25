package com.resqmesh.app.ui

enum class MainTab(val route: String, val label: String) {
    HOME("home", "Home"),
    MAP("map", "Map"),
    SOS("sos", "SOS"),
    NETWORK("network", "Network"),
    PROFILE("profile", "Profile")
}

data class MainNavigationState(
    val selectedTab: MainTab = MainTab.HOME
) {
    fun select(tab: MainTab): MainNavigationState = copy(selectedTab = tab)
}
