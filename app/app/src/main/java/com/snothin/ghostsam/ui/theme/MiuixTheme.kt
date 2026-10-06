package com.snothin.ghostsam.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@Composable
fun MiuixGhostSamTheme(
    appSettings: AppSettings,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && systemDarkTheme)

    val resolvedKeyColor: Color? = appSettings.keyColor.takeIf { it != 0 }?.let { Color(it) }

    // Controller construction is expensive (7 MutableStates + palette caches): rebuild only
    // when inputs change (no setters; a fresh instance is equivalent).
    val controller = remember(appSettings, darkTheme) {
        ThemeController(
            when (appSettings.colorMode) {
                ColorMode.SYSTEM -> ColorSchemeMode.System
                ColorMode.LIGHT -> ColorSchemeMode.Light
                ColorMode.DARK -> ColorSchemeMode.Dark
                ColorMode.MONET_SYSTEM -> ColorSchemeMode.MonetSystem
                ColorMode.MONET_LIGHT -> ColorSchemeMode.MonetLight
                ColorMode.MONET_DARK, ColorMode.DARK_AMOLED -> ColorSchemeMode.MonetDark
            },
            keyColor = resolvedKeyColor,
            isDark = darkTheme,
            paletteStyle = ThemePaletteStyle.TonalSpot,
            colorSpec = ThemeColorSpec.Spec2025,
        )
    }

    MiuixTheme(
        controller = controller,
        content = {
            LaunchedEffect(darkTheme) {
                val window = (context as? Activity)?.window ?: return@LaunchedEffect
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            CompositionLocalProvider(
                LocalContentColor provides MiuixTheme.colorScheme.onBackground,
            ) {
                content()
            }
        }
    )
}
