package com.snothin.ghostsam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.snothin.ghostsam.data.prefs.ThemePrefs
import com.snothin.ghostsam.data.channel.ExploitChannel
import com.snothin.ghostsam.data.prefs.SettingsPrefs
import com.snothin.ghostsam.data.channel.AdbController
import com.snothin.ghostsam.ui.GhostSamScaffold
import com.snothin.ghostsam.ui.theme.GhostSamTheme
import com.snothin.ghostsam.ui.theme.LocalColorMode
import com.snothin.ghostsam.ui.theme.LocalEnableFloatingBottomBar
import com.snothin.ghostsam.ui.theme.ThemeController

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Built-in adb wireless-debug bridge init (idempotent; the default exploit channel).
        AdbController.init(this)
        ExploitChannel.setMode(SettingsPrefs.executionMode(this))

        setContent {
            var appSettings by remember { mutableStateOf(ThemeController.getAppSettings(this)) }
            var enableFloatingBottomBar by remember {
                mutableStateOf(ThemePrefs.enableFloatingBottomBar(this))
            }
            var pageScale by remember { mutableFloatStateOf(ThemePrefs.pageScale(this)) }
            val darkMode = appSettings.colorMode.resolveDark(isSystemInDarkTheme())

            LaunchedEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                )
                window.isNavigationBarContrastEnforced = false
            }

            val systemDensity = LocalDensity.current
            val density = remember(systemDensity, pageScale) {
                Density(systemDensity.density * pageScale, systemDensity.fontScale)
            }

            CompositionLocalProvider(
                LocalColorMode provides appSettings.colorMode.value,
                LocalEnableFloatingBottomBar provides enableFloatingBottomBar,
                LocalDensity provides density,
            ) {
                GhostSamTheme(appSettings = appSettings) {
                    GhostSamScaffold(
                        onRefreshSettings = {
                            enableFloatingBottomBar = ThemePrefs.enableFloatingBottomBar(this)
                            pageScale = ThemePrefs.pageScale(this)
                            appSettings = ThemeController.getAppSettings(this)
                        },
                    )
                }
            }
        }
    }
}

