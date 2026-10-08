package com.berpod.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.berpod.app.ui.common.LocalDownloads
import com.berpod.app.ui.navigation.BerpodApp
import com.berpod.app.ui.theme.BerpodTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* denial just means no visible notification */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as BerpodApplication).container

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val darkMode by container.themeSettings.darkMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            BerpodTheme(
                darkOverride = darkMode,
                // Flips what is showing now, so the first tap on a light app always makes it dark.
                onToggle = { container.themeSettings.setDarkMode(!(darkMode ?: systemDark)) }
            ) {
                val downloads by container.downloadRepository.downloads.collectAsState()
                CompositionLocalProvider(LocalDownloads provides downloads) {
                    BerpodApp(container = container)
                }
            }
        }
    }
}
