package com.snothin.ghostsam.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.snothin.ghostsam.data.prefs.ThemePrefs

// Color mode: 0-2 plain; 3-5 Monet; 6 AMOLED black.
enum class ColorMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    MONET_SYSTEM(3),
    MONET_LIGHT(4),
    MONET_DARK(5),
    DARK_AMOLED(6); // no UI entry; kept for legacy preference compatibility

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: SYSTEM
    }

    val isSystem: Boolean get() = value == 0 || value == 3
    val isDark: Boolean get() = value == 2 || value == 5 || value == 6
    val isMonet: Boolean get() = value >= 3

    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        LIGHT, MONET_LIGHT -> false
        DARK, MONET_DARK, DARK_AMOLED -> true
        SYSTEM, MONET_SYSTEM -> systemDark
    }

    fun toNonMonetMode(): Int = when (this) {
        MONET_SYSTEM -> 0
        MONET_LIGHT -> 1
        MONET_DARK, DARK_AMOLED -> 2
        else -> value
    }

    fun toMonetMode(): Int = when (this) {
        SYSTEM -> 3
        LIGHT -> 4
        DARK -> 5
        else -> value
    }
}

data class AppSettings(
    val colorMode: ColorMode,
    val keyColor: Int,
)

object ThemeController {
    fun getAppSettings(context: Context): AppSettings {
        val monet = ThemePrefs.monetEnabled(context)
        var colorModeValue = ThemePrefs.themeMode(context)
        val colorMode = ColorMode.fromValue(colorModeValue)
        colorModeValue = if (!monet && colorMode.isMonet) {
            colorMode.toNonMonetMode()
        } else if (monet && !colorMode.isMonet) {
            colorMode.toMonetMode()
        } else {
            colorModeValue
        }

        val keyColor = ThemePrefs.keyColor(context)
        return AppSettings(ColorMode.fromValue(colorModeValue), keyColor)
    }
}

@Composable
fun GhostSamTheme(
    appSettings: AppSettings,
    content: @Composable () -> Unit
) {
    MiuixGhostSamTheme(
        appSettings = appSettings,
        content = content
    )
}

@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean =
    ColorMode.fromValue(LocalColorMode.current).resolveDark(isSystemInDarkTheme())

val LocalColorMode = staticCompositionLocalOf { 0 }

val LocalEnableFloatingBottomBar = staticCompositionLocalOf { false }
