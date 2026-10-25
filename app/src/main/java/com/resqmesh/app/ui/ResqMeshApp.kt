package com.resqmesh.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.resqmesh.app.data.LocalProfile
import com.resqmesh.app.data.LocalProfileStore
import com.resqmesh.app.ui.screens.DashboardScreen
import com.resqmesh.app.ui.screens.ProfileSetupScreen
import com.resqmesh.app.ui.screens.WelcomeScreen
import kotlinx.coroutines.launch

private enum class AppDestination {
    WELCOME,
    PROFILE_SETUP,
    DASHBOARD
}

@Composable
fun ResqMeshApp() {
    val context = LocalContext.current
    val profileStore = remember { LocalProfileStore(context.applicationContext) }
    val savedProfile = remember { profileStore.load() }
    var profile by remember { mutableStateOf(savedProfile) }
    var destination by remember {
        mutableStateOf(
            if (savedProfile == null) AppDestination.WELCOME else AppDestination.DASHBOARD
        )
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun showNotImplementedMessage() {
        coroutineScope.launch {
            snackbarHostState.showSnackbar("Not implemented yet")
        }
    }

    fun goBack() {
        destination = when (destination) {
            AppDestination.PROFILE_SETUP -> {
                if (profile == null) AppDestination.WELCOME else AppDestination.DASHBOARD
            }

            AppDestination.DASHBOARD -> AppDestination.DASHBOARD
            AppDestination.WELCOME -> AppDestination.WELCOME
        }
    }

    BackHandler(enabled = destination == AppDestination.PROFILE_SETUP) {
        goBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when (destination) {
            AppDestination.WELCOME -> WelcomeScreen(
                contentPadding = innerPadding,
                onGetStarted = { destination = AppDestination.PROFILE_SETUP }
            )

            AppDestination.PROFILE_SETUP -> ProfileSetupScreen(
                existingProfile = profile,
                contentPadding = innerPadding,
                onBack = ::goBack,
                onSave = { updatedProfile ->
                    profileStore.save(updatedProfile)
                    profile = updatedProfile
                    destination = AppDestination.DASHBOARD
                }
            )

            AppDestination.DASHBOARD -> profile?.let { currentProfile ->
                DashboardScreen(
                    profile = currentProfile,
                    contentPadding = innerPadding,
                    onEditProfile = { destination = AppDestination.PROFILE_SETUP },
                    onActionClick = { showNotImplementedMessage() }
                )
            }
        }
    }
}
