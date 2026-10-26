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
import com.resqmesh.app.bluetooth.BleDiscoveryManager
import com.resqmesh.app.bluetooth.EmergencyRelayController
import com.resqmesh.app.bluetooth.MeshRelayController
import com.resqmesh.app.data.EmergencySeenStore
import com.resqmesh.app.data.EmergencyRelayQueueStore
import com.resqmesh.app.data.LocalEmergencyRepository
import com.resqmesh.app.data.LocalNodeIdentityStore
import com.resqmesh.app.data.AssistanceResponseStore
import com.resqmesh.app.data.LanguagePreference
import com.resqmesh.app.data.LanguagePreferenceStore
import com.resqmesh.app.data.ResourceStore
import com.resqmesh.app.data.SafetyPreferencesStore
import com.resqmesh.app.data.VolunteerProfileStore
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
import com.resqmesh.app.data.EmergencyLocation
import com.resqmesh.app.data.EmergencyLocationSource
import com.resqmesh.app.data.EmergencyLifecycleStatus
import com.resqmesh.app.data.withLifecycleStatus
import com.resqmesh.app.data.EmergencyReportValidation
import com.resqmesh.app.data.bumpContentRevision
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
import com.resqmesh.app.ui.screens.CoordinationScreen
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
    const val COORDINATION = "coordination"
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
    val emergencySeenStore = remember { EmergencySeenStore(context.applicationContext) }
    val relayQueueStore = remember { EmergencyRelayQueueStore(context.applicationContext) }
    val localNodeId = remember { LocalNodeIdentityStore(context.applicationContext).getOrCreate() }
    val mapRepository = remember { OfflineMapRepository(context.applicationContext) }
    val messagingManager = remember { BleMessagingManager(context.applicationContext) }
    val discoveryManager = remember { BleDiscoveryManager(context.applicationContext) }
    val relayController = remember {
        EmergencyRelayController(
            repository = emergencyRepository,
            seenStore = emergencySeenStore,
            queueStore = relayQueueStore,
            transport = messagingManager,
            localNodeId = localNodeId
        )
    }
    val volunteerStore = remember { VolunteerProfileStore(context.applicationContext) }
    val resourceStore = remember { ResourceStore(context.applicationContext) }
    val responseStore = remember { AssistanceResponseStore(context.applicationContext) }
    val safetyStore = remember { SafetyPreferencesStore(context.applicationContext) }
    val meshQueueStore = remember { com.resqmesh.app.bluetooth.MeshRecordQueueStore(context.applicationContext) }
    val meshSeenStore = remember { com.resqmesh.app.bluetooth.MeshSeenStore(context.applicationContext) }
    val meshRelay = remember {
        MeshRelayController(
            queue = meshQueueStore,
            seen = meshSeenStore,
            transport = messagingManager,
            localNodeId = localNodeId,
            volunteerStore = volunteerStore,
            resourceStore = resourceStore,
            responseStore = responseStore,
            emergencyRepository = emergencyRepository
        )
    }
    val languageStore = remember { LanguagePreferenceStore(context.applicationContext) }
    val locationEngine = remember { OfflineLocationEngine(context.applicationContext) }
    val navController = rememberNavController()
    val savedProfile = remember { profileStore.load() }
    var profile by remember { mutableStateOf(savedProfile) }
    var themePreference by remember { mutableStateOf(themeStore.load()) }
    var languagePreference by remember { mutableStateOf(languageStore.load()) }
    var reports by remember { mutableStateOf(emergencyRepository.list()) }
    var relayState by remember { mutableStateOf(relayController.state) }
    var mapDataset by remember { mutableStateOf<OfflineMapDataset?>(null) }
    var locationState by remember { mutableStateOf<LocationEngineState>(locationEngine.state) }
    var selectedMapLocation by remember { mutableStateOf<EmergencyLocation?>(null) }

    LaunchedEffect(profile != null) {
        if (profile != null && mapDataset == null) {
            mapDataset = withContext(Dispatchers.Default) { mapRepository.loadDataset() }
        }
    }
    DisposableEffect(relayController) {
        val remove = relayController.addListener { relayState = it }
        onDispose { remove() }
    }
    DisposableEffect(discoveryManager) {
        onDispose {
            discoveryManager.close()
        }
    }
    DisposableEffect(messagingManager, relayController) {
        messagingManager.setEmergencyReceiver { report, peerId ->
            relayController.onIncoming(report, peerId)
            reports = emergencyRepository.list()
        }
        onDispose { messagingManager.setEmergencyReceiver(null) }
    }
    DisposableEffect(meshRelay) {
        meshRelay.setReceiver()
        onDispose {
            meshRelay.close()
            messagingManager.setMeshRecordReceiver(null)
        }
    }
    DisposableEffect(messagingManager, relayController) {
        onDispose {
            relayController.close()
            messagingManager.close()
        }
    }
    LaunchedEffect(relayState) {
        reports = emergencyRepository.list()
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
                            onReportEmergency = { navController.navigate(Routes.SOS) },
                            onInstantSos = {
                                val reading = locationEngine.state.reading
                                val report = EmergencyReport(
                                    type = EmergencyType.OTHER,
                                    severity = EmergencySeverity.HIGH,
                                    description = "Emergency assistance requested",
                                    createdAtMillis = System.currentTimeMillis(),
                                    originId = localNodeId,
                                    location = reading?.let {
                                        EmergencyLocation(
                                            latitude = it.latitude,
                                            longitude = it.longitude,
                                            accuracyMeters = it.accuracyMeters,
                                            timestampMillis = it.timestampMillis,
                                            source = EmergencyLocationSource.GNSS
                                        )
                                    }
                                )
                                emergencyRepository.save(report)
                                relayController.enqueue(report)
                                reports = emergencyRepository.list()
                                report.id
                            },
                            onCancelInstantSos = { id ->
                                emergencyRepository.list().firstOrNull { it.id == id }?.let { report ->
                                    val cancelled = report.withLifecycleStatus(com.resqmesh.app.data.EmergencyLifecycleStatus.CANCELLED)
                                    emergencyRepository.save(cancelled)
                                    meshRelay.enqueueEmergencyLifecycleUpdate(cancelled)
                                    reports = emergencyRepository.list()
                                }
                            },
                            onOpenCoordination = { navController.navigate(Routes.COORDINATION) },
                            languagePreference = languagePreference
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
                        relayController = relayController,
                        localNodeId = localNodeId,
                        reportsSnapshot = reports,
                        mapLocation = selectedMapLocation,
                        onChooseOnMap = { navController.navigate(Routes.LOCATION_PICKER) },
                        onClearMapSelection = { selectedMapLocation = null },
                        onReportsChanged = { reports = it },
                        onLifecycleChange = { report, status ->
                            val updated = report.withLifecycleStatus(status)
                            emergencyRepository.save(updated)
                            meshRelay.enqueueEmergencyLifecycleUpdate(updated)
                            reports = emergencyRepository.list()
                        },
                        onContentUpdate = { report, description ->
                            if (EmergencyReportValidation.isDescriptionValid(description)) {
                                val updated = report.copy(description = description).bumpContentRevision()
                                emergencyRepository.save(updated)
                                meshRelay.enqueueEmergencyLifecycleUpdate(updated)
                                reports = emergencyRepository.list()
                            }
                        },
                        assistanceResponses = responseStore.list(),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
                composable(Routes.NETWORK) {
                    NetworkScreen(
                        messagingManager = messagingManager,
                        discoveryManager = discoveryManager,
                        relayController = relayController,
                        contentPadding = innerPadding
                    )
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
                            },
                            languagePreference = languagePreference,
                            onLanguageChange = {
                                languageStore.save(it)
                                languagePreference = it
                            }
                        )
                    }
                }
                composable(Routes.COORDINATION) {
                    CoordinationScreen(
                        contentPadding = innerPadding,
                        emergencies = reports,
                        volunteerStore = volunteerStore,
                        resourceStore = resourceStore,
                        responseStore = responseStore,
                        safetyStore = safetyStore,
                        meshRelay = meshRelay,
                        localNodeId = localNodeId,
                        localAlias = profile?.displayName ?: "Local volunteer",
                        onSafetySos = {
                            val report = EmergencyReport(
                                type = EmergencyType.PERSONAL_SAFETY,
                                severity = EmergencySeverity.CRITICAL,
                                description = "Personal safety assistance requested",
                                createdAtMillis = System.currentTimeMillis(),
                                originId = localNodeId
                            )
                            emergencyRepository.save(report)
                            relayController.enqueue(report)
                            reports = emergencyRepository.list()
                        },
                        languagePreference = languagePreference
                    )
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
