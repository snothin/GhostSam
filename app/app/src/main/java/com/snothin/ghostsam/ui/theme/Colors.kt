package com.snothin.ghostsam.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.snothin.ghostsam.R

val NatsumeGray = Color(0xFF617172)

// Semantic status color pair (container/content swap), shared across screens; the single
// home for the hardcoded pairs scattered around.
data class StatusColors(val container: Color, val content: Color) {
    companion object {
        fun success(isDark: Boolean) = StatusColors(
            container = if (isDark) Color(0xFF1A3825) else Color(0xFFDFFAE4),
            content = if (isDark) Color(0xFFDFFAE4) else Color(0xFF1A3825),
        )

        fun warning(isDark: Boolean) = StatusColors(
            container = if (isDark) Color(0xFF4D4400) else Color(0xFFFFF3CD),
            content = if (isDark) Color(0xFFFFF3CD) else Color(0xFF5D4E00),
        )

        fun beanYellow(isDark: Boolean) = StatusColors(
            container = if (isDark) Color(0xFF3A3120) else Color(0xFFF8E8C1),
            content = if (isDark) Color(0xFFF0E6CE) else Color(0xFF4A3A1A),
        )

        fun proven(isDark: Boolean) = StatusColors(
            container = if (isDark) Color(0xFF3E5230) else Color(0xFF96C24E),
            content = if (isDark) Color(0xFFF0F6E2) else Color(0xFF1E2A0E),
        )
    }
}

val SuccessAccent = Color(0xFF36D167)

val WarningAccent = Color(0xFFFFB300)

fun errorAccent(isDark: Boolean): Color =
    if (isDark) Color(0xFFCF6679) else Color(0xFFB3261E)

val KsudCardBlue = Color(0xFF619AC3)

val SuDeniedCardLight = Color(0xFFE6F2FF)

val SuDeniedCardInk = Color(0xFF163A5F)

val SuDeniedCardAccent = Color(0xFF1E5AA8)

data class KeyColorOption(val value: Int, val labelRes: Int)

val keyColorOptions = listOf(
    KeyColorOption(0, R.string.settings_key_color_default),
    KeyColorOption(Color(0xFFF44336).toArgb(), R.string.color_red),
    KeyColorOption(Color(0xFFE91E63).toArgb(), R.string.color_pink),
    KeyColorOption(Color(0xFF9C27B0).toArgb(), R.string.color_purple),
    KeyColorOption(Color(0xFF673AB7).toArgb(), R.string.color_deep_purple),
    KeyColorOption(Color(0xFF3F51B5).toArgb(), R.string.color_indigo),
    KeyColorOption(Color(0xFF2196F3).toArgb(), R.string.color_blue),
    KeyColorOption(Color(0xFF00BCD4).toArgb(), R.string.color_cyan),
    KeyColorOption(Color(0xFF009688).toArgb(), R.string.color_teal),
    KeyColorOption(Color(0xFF4FAF50).toArgb(), R.string.color_green),
    KeyColorOption(Color(0xFFFFEB3B).toArgb(), R.string.color_yellow),
    KeyColorOption(Color(0xFFFFC107).toArgb(), R.string.color_amber),
    KeyColorOption(Color(0xFFFF9800).toArgb(), R.string.color_orange),
    KeyColorOption(Color(0xFF795548).toArgb(), R.string.color_brown),
    KeyColorOption(Color(0xFF607D8F).toArgb(), R.string.color_blue_grey),
    KeyColorOption(Color(0xFFFF9CA8).toArgb(), R.string.color_sakura),
)
