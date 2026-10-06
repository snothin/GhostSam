package com.snothin.ghostsam.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.snothin.ghostsam.R
import com.snothin.ghostsam.data.prefs.ThemePrefs
import com.snothin.ghostsam.ui.component.SectionLabel
import com.snothin.ghostsam.ui.theme.keyColorOptions
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val LANGUAGE_VALUES = listOf("system", "zh-CN", "en")

@Composable
fun ThemeSettingsScreen(
    onBack: () -> Unit,
    onSettingsChanged: () -> Unit,
) {
    val context = LocalContext.current
    var themeMode by remember { mutableIntStateOf(ThemePrefs.themeMode(context)) }
    var keyColor by remember { mutableIntStateOf(ThemePrefs.keyColor(context)) }
    var monetEnabled by remember { mutableStateOf(ThemePrefs.monetEnabled(context)) }
    var enableFloating by remember { mutableStateOf(ThemePrefs.enableFloatingBottomBar(context)) }
    var pageScale by remember { mutableFloatStateOf(ThemePrefs.pageScale(context)) }
    var language by remember { mutableStateOf(ThemePrefs.language(context)) }

    fun setThemeMode(mode: Int) {
        ThemePrefs.setThemeMode(context, mode)
        themeMode = mode
        onSettingsChanged()
    }

    fun setKeyColor(color: Int) {
        ThemePrefs.setKeyColor(context, color)
        keyColor = color
        onSettingsChanged()
    }

    fun setMonet(enabled: Boolean) {
        ThemePrefs.setMonetEnabled(context, enabled)
        monetEnabled = enabled
        onSettingsChanged()
    }

    fun setEnableFloating(enabled: Boolean) {
        ThemePrefs.setEnableFloatingBottomBar(context, enabled)
        enableFloating = enabled
        onSettingsChanged()
    }

    fun setPageScale(value: Float) {
        ThemePrefs.setPageScale(context, value)
        pageScale = value
        onSettingsChanged()
    }

    fun setLanguage(value: String) {
        ThemePrefs.setLanguage(context, value)
        language = value
    }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_theme_and_language),
                largeTitle = stringResource(R.string.settings_theme_and_language),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = padding + PaddingValues(top = 12.dp),
            overscrollEffect = null,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionLabel(stringResource(R.string.section_language)) }
            item {
                top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.settings_language),
                        summary = stringResource(R.string.settings_language_summary),
                        items = listOf(
                            stringResource(R.string.settings_language_follow_system),
                            stringResource(R.string.settings_language_zh_cn),
                            stringResource(R.string.settings_language_en),
                        ),
                        startAction = {
                            Icon(
                                Icons.Rounded.Language,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = stringResource(R.string.settings_language),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        },
                        selectedIndex = LANGUAGE_VALUES.indexOf(language).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            setLanguage(LANGUAGE_VALUES[index])
                        },
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.section_theme)) }
            item {
                top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
                    val baseMode = if (themeMode >= 3) themeMode - 3 else themeMode
                    OverlayDropdownPreference(
                        title = stringResource(R.string.settings_color_mode),
                        items = listOf(
                            stringResource(R.string.settings_theme_mode_system),
                            stringResource(R.string.settings_theme_mode_light),
                            stringResource(R.string.settings_theme_mode_dark),
                        ),
                        startAction = {
                            Icon(
                                Icons.Rounded.BrightnessAuto,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = stringResource(R.string.settings_color_mode),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        },
                        selectedIndex = baseMode.coerceIn(0, 2),
                        onSelectedIndexChange = { index -> setThemeMode(index) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_monet),
                        startAction = {
                            Icon(
                                Icons.Rounded.Wallpaper,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = stringResource(R.string.settings_monet),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        },
                        checked = monetEnabled,
                        onCheckedChange = ::setMonet,
                    )
                    AnimatedVisibility(
                        visible = monetEnabled,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        val keyColors = keyColorOptions
                        OverlayDropdownPreference(
                            title = stringResource(R.string.settings_key_color),
                            items = keyColors.map { stringResource(it.labelRes) },
                            startAction = {
                                Icon(
                                    Icons.Rounded.Colorize,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_key_color),
                                    tint = MiuixTheme.colorScheme.onBackground,
                                )
                            },
                            selectedIndex = keyColors.indexOfFirst { it.value == keyColor }.coerceAtLeast(0),
                            onSelectedIndexChange = { index -> setKeyColor(keyColors[index].value) },
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f),
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_floating_bottom_bar),
                        startAction = {
                            Icon(
                                Icons.Rounded.Circle,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = stringResource(R.string.settings_floating_bottom_bar),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        },
                        checked = enableFloating,
                        onCheckedChange = ::setEnableFloating,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f),
                    )
                    var sliderValue by remember(pageScale) { mutableFloatStateOf(pageScale) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.ZoomOutMap,
                            modifier = Modifier.size(20.dp),
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.settings_page_scale),
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${(sliderValue * 100).toInt()}%",
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { setPageScale(sliderValue) },
                        valueRange = 0.8f..1.1f,
                        showKeyPoints = true,
                        keyPoints = listOf(0.8f, 0.9f, 1f, 1.1f),
                        magnetThreshold = 0.01f,
                        hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
