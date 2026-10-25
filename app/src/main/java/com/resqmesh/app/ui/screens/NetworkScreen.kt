package com.resqmesh.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resqmesh.app.bluetooth.BleMessagingManager
import com.resqmesh.app.ui.components.BleDiscoverySection
import com.resqmesh.app.ui.components.BluetoothReadinessSection

@Composable
fun NetworkScreen(
    messagingManager: BleMessagingManager,
    contentPadding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
    ) {
        BluetoothReadinessSection(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        BleDiscoverySection(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            messagingManager = messagingManager
        )
    }
}
