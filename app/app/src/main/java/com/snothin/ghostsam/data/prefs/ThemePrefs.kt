package com.snothin.ghostsam.data.prefs

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.LocaleList

object ThemePrefs {
    private const val PREFS_NAME = "theme"

    private const val THEME_MODE = "theme_mode"
    private const val MONET_ENABLED = "monet_enabled"
    private const val KEY_COLOR = "key_color"
    private const val ENABLE_FLOATING_BOTTOM_BAR = "enable_floating_bottom_bar"
    private const val PAGE_SCALE = "page_scale"

    fun language(context: Context): String {
        val locales = runCatching {
            context.getSystemService(LocaleManager::class.java).applicationLocales
        }.getOrNull() ?: return "system"
        return if (locales.isEmpty) "system" else locales[0].toLanguageTag()
    }

    fun setLanguage(context: Context, value: String) {
        runCatching {
            val manager = context.getSystemService(LocaleManager::class.java)
            manager.applicationLocales = if (value == "system") {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(value)
            }
        }
    }

    fun themeMode(context: Context): Int =
        prefs(context).getInt(THEME_MODE, 0)

    fun setThemeMode(context: Context, value: Int) {
        prefs(context).edit().putInt(THEME_MODE, value).apply()
    }

    fun monetEnabled(context: Context): Boolean =
        prefs(context).getBoolean(MONET_ENABLED, false)

    fun setMonetEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(MONET_ENABLED, value).apply()
    }

    fun keyColor(context: Context): Int =
        prefs(context).getInt(KEY_COLOR, 0)

    fun setKeyColor(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_COLOR, value).apply()
    }

    fun enableFloatingBottomBar(context: Context): Boolean =
        prefs(context).getBoolean(ENABLE_FLOATING_BOTTOM_BAR, false)

    fun setEnableFloatingBottomBar(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(ENABLE_FLOATING_BOTTOM_BAR, value).apply()
    }

    fun pageScale(context: Context): Float =
        prefs(context).getFloat(PAGE_SCALE, 1f).coerceIn(0.8f, 1.1f)

    fun setPageScale(context: Context, value: Float) {
        prefs(context).edit().putFloat(PAGE_SCALE, value.coerceIn(0.8f, 1.1f)).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
