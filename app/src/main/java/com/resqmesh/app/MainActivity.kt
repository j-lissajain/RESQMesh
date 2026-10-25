package com.resqmesh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.resqmesh.app.ui.ResqMeshApp
import com.resqmesh.app.ui.theme.RESQMeshTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RESQMeshTheme(darkTheme = true, dynamicColor = false) {
                ResqMeshApp()
            }
        }
    }
}
