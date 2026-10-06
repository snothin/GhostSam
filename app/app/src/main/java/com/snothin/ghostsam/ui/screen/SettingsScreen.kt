package com.snothin.ghostsam.ui.screen

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Troubleshoot
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snothin.ghostsam.R
import com.snothin.ghostsam.data.channel.AdbController
import com.snothin.ghostsam.data.channel.ExecutionMode
import com.snothin.ghostsam.data.channel.ExploitChannel
import com.snothin.ghostsam.data.channel.SuController
import com.snothin.ghostsam.data.device.KernelSuProbe
import com.snothin.ghostsam.data.prefs.ExploitPrefs
import com.snothin.ghostsam.data.exploit.PayloadScheme
import com.snothin.ghostsam.data.exploit.RunState
import com.snothin.ghostsam.data.exploit.StandaloneDfr
import com.snothin.ghostsam.data.prefs.KsudVariant
import com.snothin.ghostsam.data.prefs.SettingsPrefs
import com.snothin.ghostsam.ui.component.NumberInputDialog
import com.snothin.ghostsam.ui.component.SectionLabel
import com.snothin.ghostsam.ui.navigation3.LocalNavigator
import com.snothin.ghostsam.ui.navigation3.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    var showAboutDialog by remember { mutableStateOf(false) }
    // Stable lambda: prevents recompositions from passing a new lambda to SettingsMain
    // and making it non-skippable.
    val openAbout = remember { { showAboutDialog = true } }

    SettingsMain(
        modifier = modifier,
        onOpenAbout = openAbout,
    )

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

private class SettingsMainState(
    val context: Context,
    val scope: CoroutineScope,
) {
    var localPayload by mutableStateOf(SettingsPrefs.localPayload(context))
    var stabilizeEnabled by mutableStateOf(SettingsPrefs.uptimeGateEnabled(context))
    var executionMode by mutableStateOf(SettingsPrefs.executionMode(context))
    var execStatus by mutableStateOf("")
    var payloadScheme by mutableStateOf(SettingsPrefs.payloadScheme(context))
    var schemeStatus by mutableStateOf("")
    var ksudVariant by mutableStateOf(SettingsPrefs.ksudVariant(context))
    var ksudVariantStatus by mutableStateOf("")
    var pinProbe by mutableStateOf(SettingsPrefs.pinProbe(context))
    var pinStatus by mutableStateOf("")
    // Variant-switch freeze: once this boot has activated, the loaded variant cannot be
    // swapped (late-load skips it); settings must never drift from the loaded variant.
    var kernelActivated by mutableStateOf(false)
    var kernelCheckTick by mutableIntStateOf(0)

    var stabilizeSeconds by mutableIntStateOf(SettingsPrefs.uptimeGateSeconds(context))
    var showSecondsDialog by mutableStateOf(false)
    var tcpPersist by mutableStateOf(SettingsPrefs.tcpPersist(context))
    var showTcpConfirm by mutableStateOf(false)
    var tcpPort by mutableIntStateOf(SettingsPrefs.tcpPort(context))
    var showTcpPortDialog by mutableStateOf(false)
    var tcpStatus by mutableStateOf("")
}

@Composable
private fun rememberSettingsMainState(): SettingsMainState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { SettingsMainState(context, scope) }
}

@Composable
private fun SettingsMain(
    modifier: Modifier = Modifier,
    onOpenAbout: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val state = rememberSettingsMainState()

    val useShizuku = state.executionMode == ExecutionMode.Shizuku
    val standaloneRoute = state.payloadScheme == PayloadScheme.DfrStandalone

    LaunchedEffect(state.kernelCheckTick) {
        state.kernelActivated = withContext(Dispatchers.IO) {
            kernelActivatedNow(context) || SuController.isAvailable()
        }
    }
    val companionSection = rememberCompanionSectionState(state.payloadScheme)
    val settingsLifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(settingsLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.kernelCheckTick += 1
            }
        }
        settingsLifecycleOwner.lifecycle.addObserver(observer)
        try {
            awaitCancellation()
        } finally {
            settingsLifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val tcpArmedPort by AdbController.armedPortFlow.collectAsState()
    val tcpArmError by AdbController.armErrorFlow.collectAsState()
    val tcpStatusText = remember(state.tcpStatus, state.tcpPersist, tcpArmedPort, tcpArmError) {
        state.tcpStatus.ifEmpty {
            when {
                !state.tcpPersist -> ""
                tcpArmedPort > 0 -> context.getString(R.string.tcp_status_armed, tcpArmedPort)
                tcpArmError != null -> context.getString(R.string.tcp_status_failed, tcpArmError.orEmpty())
                else -> context.getString(R.string.tcp_status_deferred)
            }
        }
    }
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        popupHost = { },
        topBar = {
            TopAppBar(
                title = stringResource(R.string.nav_settings),
                largeTitle = stringResource(R.string.nav_settings),
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
            contentPadding = padding + PaddingValues(top = 12.dp, bottom = 20.dp),
            overscrollEffect = null,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThemeBlock(onOpen = { navigator.push(Route.ColorPalette) })
            SchemeBlock(state, companionSection, standaloneRoute)
            ExecutionBlock(state, standaloneRoute, useShizuku, tcpStatusText)
            ParamsBlock(state, standaloneRoute, onOpenMore = { navigator.push(Route.More) })
            AboutBlock(onOpenAbout)
        }
    }

    if (state.showSecondsDialog) {
        NumberInputDialog(
            title = stringResource(R.string.settings_stabilize_seconds),
            summary = stringResource(R.string.settings_stabilize_seconds_summary),
            current = state.stabilizeSeconds.toDouble(),
            min = SettingsPrefs.UPTIME_GATE_MIN_SECONDS.toDouble(),
            max = SettingsPrefs.UPTIME_GATE_MAX_SECONDS.toDouble(),
            suffix = "s",
            integerOnly = true,
            onConfirm = { seconds ->
                SettingsPrefs.setUptimeGateSeconds(context, seconds.toInt())
                state.stabilizeSeconds = seconds.toInt()
                state.showSecondsDialog = false
            },
            onDismiss = { state.showSecondsDialog = false },
        )
    }

    if (state.showTcpPortDialog) {
        NumberInputDialog(
            title = stringResource(R.string.settings_tcp_port),
            summary = stringResource(R.string.settings_tcp_port_summary),
            current = state.tcpPort.toDouble(),
            min = SettingsPrefs.TCP_PORT_MIN.toDouble(),
            max = SettingsPrefs.TCP_PORT_MAX.toDouble(),
            suffix = "",
            integerOnly = true,
            onConfirm = { value ->
                val port = value.toInt()
                SettingsPrefs.setTcpPort(context, port)
                state.tcpPort = port
                state.showTcpPortDialog = false
                state.scope.launch {
                    state.tcpStatus = if (
                        state.tcpPersist && AdbController.isArmed() &&
                        AdbController.isConnected() && !RunState.exploitActive
                    ) {
                        val error = AdbController.armTcp()
                        if (error == null) "" else context.getString(R.string.tcp_status_failed, error)
                    } else {
                        context.getString(R.string.tcp_port_saved, port)
                    }
                }
            },
            onDismiss = { state.showTcpPortDialog = false },
        )
    }

    if (state.showTcpConfirm) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.tcp_persist_dialog_title),
            summary = stringResource(R.string.tcp_persist_dialog_body),
            onDismissRequest = { state.showTcpConfirm = false },
            content = {
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = { state.showTcpConfirm = false },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(R.string.tcp_persist_enable),
                        onClick = {
                            state.showTcpConfirm = false
                            if (RunState.exploitActive) {
                                state.tcpStatus = context.getString(R.string.tcp_busy_hint)
                            } else {
                                SettingsPrefs.setTcpPersist(context, true)
                                state.tcpPersist = true
                                state.tcpStatus = ""
                                state.scope.launch {
                                    if (AdbController.isConnected()) {
                                        val error = AdbController.armTcp()
                                        if (error != null) {
                                            state.tcpStatus = context.getString(R.string.tcp_status_failed, error)
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            },
        )
    }

    SettingsCompanionDialogs(companionSection)
}

private fun LazyListScope.ThemeBlock(onOpen: () -> Unit) {
    item { SectionLabel(stringResource(R.string.section_theme)) }
    item {
        top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = stringResource(R.string.settings_theme_and_language),
                summary = stringResource(R.string.settings_theme_and_language_summary),
                startAction = {
                    Icon(
                        Icons.Rounded.Palette,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = stringResource(R.string.settings_theme_and_language),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
                onClick = { onOpen() },
            )
        }
    }
}

private fun LazyListScope.SchemeBlock(
    state: SettingsMainState,
    companionSection: CompanionSectionState,
    standaloneRoute: Boolean,
) {
    val context = state.context
    item { SectionLabel(stringResource(R.string.section_scheme)) }
    item {
        top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
            if (state.schemeStatus.isNotEmpty()) {
                Text(
                    text = state.schemeStatus,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            OverlayDropdownPreference(
                title = stringResource(R.string.settings_scheme),
                summary = stringResource(
                    when (state.payloadScheme) {
                        PayloadScheme.Dfr -> R.string.settings_scheme_summary_dfr
                        PayloadScheme.DfrStandalone -> R.string.settings_scheme_summary_standalone
                        else -> R.string.settings_scheme_summary_default
                    },
                ),
                items = listOf(
                    stringResource(R.string.settings_scheme_default),
                    stringResource(R.string.settings_scheme_dfr),
                    stringResource(R.string.settings_scheme_standalone),
                ),
                startAction = {
                    Icon(
                        Icons.AutoMirrored.Rounded.AltRoute,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = stringResource(R.string.settings_scheme),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
                selectedIndex = state.payloadScheme.ordinal.coerceIn(0, PayloadScheme.entries.size - 1),
                onSelectedIndexChange = { index ->
                    if (RunState.exploitActive) {
                        // Safety gate: no scheme switching while running (preflight froze this run's scheme).
                        state.schemeStatus = context.getString(R.string.settings_exec_busy_hint)
                    } else {
                        val next = PayloadScheme.entries[index]
                        SettingsPrefs.setPayloadScheme(context, next)
                        state.payloadScheme = next
                        state.schemeStatus = ""
                    }
                },
            )
            if (state.ksudVariantStatus.isNotEmpty()) {
                Text(
                    text = state.ksudVariantStatus,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            OverlayDropdownPreference(
                title = stringResource(R.string.settings_ksud_variant),
                summary = stringResource(
                    when {
                        !standaloneRoute -> R.string.settings_ksud_variant_locked
                        state.kernelActivated -> R.string.settings_ksud_variant_active
                        else -> R.string.settings_ksud_variant_summary
                    },
                ),
                items = listOf(
                    stringResource(R.string.settings_ksud_variant_default),
                    stringResource(R.string.settings_ksud_variant_next),
                ),
                startAction = {
                    Icon(
                        Icons.Rounded.Memory,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = stringResource(R.string.settings_ksud_variant),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
                selectedIndex = (if (standaloneRoute) state.ksudVariant else KsudVariant.Default)
                    .ordinal.coerceIn(0, KsudVariant.entries.size - 1),
                onSelectedIndexChange = { index ->
                    when {
                        RunState.exploitActive -> {
                            state.ksudVariantStatus = context.getString(R.string.settings_exec_busy_hint)
                        }
                        kernelActivatedNow(context) -> {
                            state.kernelActivated = true
                            state.ksudVariantStatus = context.getString(R.string.settings_ksud_variant_active)
                        }
                        else -> {
                            val next = KsudVariant.entries[index]
                            SettingsPrefs.setKsudVariant(context, next)
                            state.ksudVariant = next
                            state.ksudVariantStatus = ""
                        }
                    }
                },
                enabled = standaloneRoute && !state.kernelActivated,
            )
            if (state.payloadScheme != PayloadScheme.Default) {
                if (state.pinStatus.isNotEmpty()) {
                    val running = stringResource(R.string.settings_pin_probe_running)
                    if (state.pinStatus != running) {
                        LaunchedEffect(state.pinStatus) {
                            delay(5_000)
                            state.pinStatus = ""
                        }
                    }
                    Text(
                        text = state.pinStatus,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.settings_pin_probe),
                    summary = stringResource(R.string.settings_pin_probe_summary),
                    startAction = {
                        Icon(
                            Icons.Rounded.Troubleshoot,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = stringResource(R.string.settings_pin_probe),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    },
                    checked = state.pinProbe,
                    onCheckedChange = { checked ->
                        if (RunState.exploitActive) {
                            state.pinStatus = context.getString(R.string.settings_exec_busy_hint)
                        } else {
                            SettingsPrefs.setPinProbe(context, checked)
                            state.pinProbe = checked
                            state.pinStatus = ""
                            if (checked) {
                                state.pinStatus = context.getString(R.string.settings_pin_probe_running)
                                state.scope.launch {
                                    val raw = withContext(Dispatchers.IO) { StandaloneDfr.pinProbe(context) }
                                    state.pinStatus = pinStatusText(context, raw)
                                }
                            }
                        }
                    },
                )
            }
            SettingsCompanionSection(companionSection, state.payloadScheme)
        }
    }
}

private fun pinStatusText(context: Context, raw: String): String {
    if (raw.isBlank()) return context.getString(R.string.settings_pin_probe_unavailable)
    val verdict = raw.lineSequence().firstOrNull { it.startsWith("pin.verdict=") }
        ?.substringAfter('=')?.trim().orEmpty()
    val ms = raw.lineSequence().firstOrNull { it.startsWith("pin.ms=") }
        ?.substringAfter('=')?.trim().orEmpty()
    val base = when (verdict) {
        "pinned" -> context.getString(R.string.settings_pin_probe_supported)
        "copied" -> context.getString(R.string.settings_pin_probe_copied)
        else -> context.getString(R.string.settings_pin_probe_unavailable)
    }
    return if (ms.isNotEmpty()) "$base · ${ms}ms" else base
}

private fun LazyListScope.ExecutionBlock(
    state: SettingsMainState,
    standaloneRoute: Boolean,
    useShizuku: Boolean,
    tcpStatusText: String,
) {
    val context = state.context
    if (!standaloneRoute) {
    item { SectionLabel(stringResource(R.string.section_execution)) }
    item {
        top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
            val execStateText = remember(state.execStatus, state.executionMode) {
                state.execStatus.ifEmpty {
                    context.getString(
                        when (state.executionMode) {
                            ExecutionMode.Shizuku -> R.string.settings_exec_state_shizuku
                            else -> R.string.settings_exec_state_adb
                        },
                    )
                }
            }
            Text(
                text = execStateText,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            SwitchPreference(
                title = stringResource(R.string.settings_use_shizuku),
                summary = stringResource(R.string.settings_use_shizuku_summary),
                startAction = {
                    Icon(
                        painterResource(R.drawable.ic_shizuku),
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = stringResource(R.string.settings_use_shizuku),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
                checked = useShizuku,
                onCheckedChange = { checked ->
                    if (RunState.exploitActive) {
                        state.execStatus = context.getString(R.string.settings_exec_busy_hint)
                    } else {
                        val next = if (checked) ExecutionMode.Shizuku else ExecutionMode.Adb
                        SettingsPrefs.setExecutionMode(context, next)
                        ExploitChannel.setMode(next)
                        state.executionMode = next
                        state.execStatus = ""
                    }
                },
            )
            if (state.executionMode == ExecutionMode.Adb) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f),
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_tcp_persist),
                    summary = stringResource(R.string.settings_tcp_persist_summary),
                    startAction = {
                        Icon(
                            Icons.Rounded.Wifi,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = stringResource(R.string.settings_tcp_persist),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    },
                    checked = state.tcpPersist,
                    onCheckedChange = { checked ->
                        if (RunState.exploitActive) {
                            // Safety gate: switching while running would kill the session (adbd restarts).
                            state.tcpStatus = context.getString(R.string.tcp_busy_hint)
                        } else if (checked) {
                            state.showTcpConfirm = true
                        } else {
                            SettingsPrefs.setTcpPersist(context, false)
                            state.tcpPersist = false
                            state.scope.launch { state.tcpStatus = AdbController.disarmTcp() }
                        }
                    },
                )
                if (tcpStatusText.isNotEmpty()) {
                    Text(
                        text = tcpStatusText,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                AnimatedVisibility(
                    visible = state.tcpPersist,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_tcp_port),
                        summary = stringResource(R.string.settings_tcp_port_summary),
                        startAction = {
                            Icon(
                                Icons.Rounded.Numbers,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = stringResource(R.string.settings_tcp_port),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        },
                        endActions = {
                            Text(
                                text = state.tcpPort.toString(),
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        },
                        onClick = { state.showTcpPortDialog = true },
                    )
                }
            }
        }
    }
    }
}

private fun LazyListScope.ParamsBlock(
    state: SettingsMainState,
    standaloneRoute: Boolean,
    onOpenMore: () -> Unit,
) {
    val context = state.context
    item { SectionLabel(stringResource(R.string.section_parameters)) }
    item {
        top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = stringResource(R.string.settings_local_payload),
                summary = stringResource(R.string.settings_local_payload_summary),
                startAction = {
                    Icon(
                        Icons.Rounded.Code,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = stringResource(R.string.settings_local_payload),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
                checked = state.localPayload,
                onCheckedChange = {
                    SettingsPrefs.setLocalPayload(context, it)
                    state.localPayload = it
                },
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 0.5.dp,
                color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f),
            )
            if (!standaloneRoute) {
            SwitchPreference(
                title = stringResource(R.string.settings_boot_stabilize),
                summary = stringResource(R.string.settings_boot_stabilize_summary),
                startAction = {
                    Icon(
                        Icons.Rounded.Schedule,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = stringResource(R.string.settings_boot_stabilize),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
                checked = state.stabilizeEnabled,
                onCheckedChange = {
                    SettingsPrefs.setUptimeGateEnabled(context, it)
                    state.stabilizeEnabled = it
                },
            )
            AnimatedVisibility(
                visible = state.stabilizeEnabled,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                ArrowPreference(
                    title = stringResource(R.string.settings_stabilize_seconds),
                    summary = stringResource(R.string.settings_stabilize_seconds_summary),
                    startAction = {
                        Icon(
                            Icons.Rounded.Timer,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = stringResource(R.string.settings_stabilize_seconds),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    },
                    endActions = {
                        Text(
                            text = stringResource(R.string.settings_seconds_value, state.stabilizeSeconds),
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    },
                    onClick = { state.showSecondsDialog = true },
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 0.5.dp,
                color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f),
            )
            }
            ArrowPreference(
                title = stringResource(R.string.settings_more),
                summary = stringResource(R.string.settings_more_summary),
                startAction = {
                    Icon(
                        Icons.Rounded.MoreHoriz,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = stringResource(R.string.settings_more),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
                onClick = { onOpenMore() },
            )
        }
    }
}

private fun LazyListScope.AboutBlock(onOpenAbout: () -> Unit) {
    item { SectionLabel(stringResource(R.string.section_about)) }
    item {
        top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = stringResource(R.string.settings_about),
                startAction = {
                    Icon(
                        Icons.Rounded.Info,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = stringResource(R.string.settings_about),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
                onClick = onOpenAbout,
            )
        }
    }
}

private fun kernelActivatedNow(context: Context): Boolean =
    KernelSuProbe.isActive() ||
        ExploitPrefs.isReceiptVerified(context, ExploitPrefs.currentBootToken())
