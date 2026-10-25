package com.resqmesh.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.resqmesh.app.bluetooth.BleMessagingManager
import com.resqmesh.app.data.EmergencyLocation
import com.resqmesh.app.data.LocalEmergencyRepository
import com.resqmesh.app.data.LocalProfile
import com.resqmesh.app.data.LocalProfileStore
import com.resqmesh.app.data.ThemePreference
import com.resqmesh.app.data.ThemePreferenceStore
import com.resqmesh.app.location.LocationEngineState
import com.resqmesh.app.location.OfflineLocationEngine
import com.resqmesh.app.maps.OfflineMapDataset
import com.resqmesh.app.maps.OfflineMapRepository
import com.resqmesh.app.ui.screens.HomeScreen
import com.resqmesh.app.ui.screens.MapDestinationScreen
import com.resqmesh.app.ui.screens.NetworkScreen
import com.resqmesh.app.ui.screens.ProfileScreen
import com.resqmesh.app.ui.screens.ProfileSetupScreen
import com.resqmesh.app.ui.screens.WelcomeScreen
import com.resqmesh.app.ui.components.EmergencyReportingSection
import com.resqmesh.app.ui.components.OfflineLocationPickerScreen
import com.resqmesh.app.ui.theme.RESQMeshTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object Routes {
    const val WELCOME = "welcome"
    const val PROFILE_SETUP = "profile_setup"
    const val HOME = "home"
    const val MAP = "map"
    const val SOS = "sos"
    const val NETWORK = "network"
    const val PROFILE = "profile"
    const val LOCATION_PICKER = "location_picker"
}

private data class MainDestination(
    val route: String,
    val label: String,
    val icon: String
)

private val mainDestinations = listOf(
    MainDestination(Routes.HOME, "Home", "⌂"),
    MainDestination(Routes.MAP, "Map", "⌖"),
    MainDestination(Routes.SOS, "SOS", "!"),
    MainDestination(Routes.NETWORK, "Network", "◌"),
    MainDestination(Routes.PROFILE, "Profile", "☰")
)

@Composable
fun ResqMeshApp() {
    val context = LocalContext.current
    val profileStore = remember { LocalProfileStore(context.applicationContext) }
    val themeStore = remember { ThemePreferenceStore(context.applicationContext) }
    val emergencyRepository = remember { LocalEmergencyRepository(context.applicationContext) }
    val mapRepository = remember { OfflineMapRepository(context.applicationContext) }
    val messagingManager = remember { BleMessagingManager(context.applicationContext) }
    val locationEngine = remember { OfflineLocationEngine(context.applicationContext) }
    val navController = rememberNavController()
    val savedProfile = remember { profileStore.load() }
    var profile by remember { mutableStateOf(savedProfile) }
    var themePreference by remember { mutableStateOf(themeStore.load()) }
    var reports by remember { mutableStateOf(emergencyRepository.list()) }
    var mapDataset by remember { mutableStateOf<OfflineMapDataset?>(null) }
    var locationState by remember { mutableStateOf<LocationEngineState>(locationEngine.state) }
    var selectedMapLocation by remember { mutableStateOf<EmergencyLocation?>(null) }

    LaunchedEffect(profile != null) {
        if (profile != null && mapDataset == null) {
            mapDataset = withContext(Dispatchers.Default) { mapRepository.loadDataset() }
        }
    }
    DisposableEffect(messagingManager) { onDispose { messagingManager.close() } }
    DisposableEffect(messagingManager, emergencyRepository) {
        messagingManager.setEmergencyReceiver { report, _ ->
            emergencyRepository.save(report)
            reports = emergencyRepository.list()
        }
        onDispose { messagingManager.setEmergencyReceiver(null) }
    }
    DisposableEffect(locationEngine) {
        val remove = locationEngine.addListener { locationState = it }
        onDispose {
            remove()
            locationEngine.close()
        }
    }

    RESQMeshTheme(themePreference = themePreference, dynamicColor = false) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination
        val currentRoute = currentDestination?.route
        val showBottomBar = profile != null && mainDestinations.any { it.route == currentRoute }
        val startDestination = if (profile == null) Routes.WELCOME else Routes.HOME

        BackHandler(enabled = currentRoute == Routes.LOCATION_PICKER) {
            navController.popBackStack()
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        mainDestinations.forEach { destination ->
                            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Text(destination.icon) },
                                label = { Text(destination.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Routes.WELCOME) {
                    WelcomeScreen(
                        contentPadding = innerPadding,
                        onGetStarted = { navController.navigate(Routes.PROFILE_SETUP) }
                    )
                }
                composable(Routes.PROFILE_SETUP) {
                    ProfileSetupScreen(
                        existingProfile = profile,
                        contentPadding = innerPadding,
                        onBack = { navController.popBackStack() },
                        onSave = { updatedProfile ->
                            profileStore.save(updatedProfile)
                            profile = updatedProfile
                            navController.navigate(Routes.HOME) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(Routes.HOME) {
                    profile?.let { currentProfile ->
                        HomeScreen(
                            profile = currentProfile,
                            reports = reports,
                            messagingManager = messagingManager,
                            contentPadding = innerPadding,
                            onOpenMap = { navController.navigate(Routes.MAP) },
                            onReportEmergency = { navController.navigate(Routes.SOS) }
                        )
                    }
                }
                composable(Routes.MAP) {
                    MapDestinationScreen(
                        dataset = mapDataset,
                        datasetDescription = mapRepository.datasetDescription(),
                        locationState = locationState,
                        reports = reports,
                        contentPadding = innerPadding
                    )
                }
                composable(Routes.SOS) {
                    EmergencyReportingSection(
                        repository = emergencyRepository,
                        locationEngine = locationEngine,
                        messagingManager = messagingManager,
                        reportsSnapshot = reports,
                        mapLocation = selectedMapLocation,
                        onChooseOnMap = { navController.navigate(Routes.LOCATION_PICKER) },
                        onClearMapSelection = { selectedMapLocation = null },
                        onReportsChanged = { reports = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
                composable(Routes.NETWORK) {
                    NetworkScreen(messagingManager = messagingManager, contentPadding = innerPadding)
                }
                composable(Routes.PROFILE) {
                    profile?.let { currentProfile ->
                        ProfileScreen(
                            profile = currentProfile,
                            themePreference = themePreference,
                            contentPadding = innerPadding,
                            onEditProfile = { navController.navigate(Routes.PROFILE_SETUP) },
                            onThemeChange = {
                                themeStore.save(it)
                                themePreference = it
                            }
                        )
                    }
                }
                composable(Routes.LOCATION_PICKER) {
                    OfflineLocationPickerScreen(
                        dataset = mapDataset,
                        locationState = locationState,
                        initialLocation = selectedMapLocation,
                        contentPadding = innerPadding,
                        onBack = { navController.popBackStack() },
                        onConfirm = {
                            selectedMapLocation = it
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
