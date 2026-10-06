package com.snothin.ghostsam.ui.screen

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snothin.ghostsam.R
import com.snothin.ghostsam.data.device.DeviceSnapshot
import com.snothin.ghostsam.data.prefs.ExploitPrefs
import com.snothin.ghostsam.data.exploit.KernelLine
import com.snothin.ghostsam.data.exploit.PayloadScheme
import com.snothin.ghostsam.data.prefs.SettingsPrefs
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import com.snothin.ghostsam.ui.component.HexInputDialog
import com.snothin.ghostsam.ui.component.NumberInputDialog
import com.snothin.ghostsam.ui.component.SectionLabel

private class MoreScreenState(val context: Context) {
    var dialog by mutableStateOf<NumberDialog?>(null)

    var walkTimeout by mutableIntStateOf(ExploitPrefs.walkTimeoutSec(context))
    var walkDelayMs by mutableDoubleStateOf(ExploitPrefs.walkDelay(context) / 1_000.0)
    var debugEnabled by mutableStateOf(ExploitPrefs.debugEnabled(context))
    var loadGateEnabled by mutableStateOf(ExploitPrefs.loadGateEnabled(context))
    var loadThreshold by mutableDoubleStateOf(ExploitPrefs.loadThreshold(context))
    var loadWaitSec by mutableIntStateOf((ExploitPrefs.loadWaitMaxMs(context) / 1000).toInt())
    var stallSec by mutableIntStateOf((ExploitPrefs.stallTimeoutMs(context) / 1000).toInt())
    var totalSec by mutableIntStateOf((ExploitPrefs.totalTimeoutMs(context) / 1000).toInt())

    var customEnabled by mutableStateOf(ExploitPrefs.customEnabled(context))
    var customFields by mutableStateOf(ExploitPrefs.customFields(context))
    var hexFieldIndex by mutableStateOf<Int?>(null)
    var skipKsu by mutableStateOf(SettingsPrefs.skipKsu(context))
    var allowShell by mutableStateOf(ExploitPrefs.allowShellEnabled(context))
    var autoSoftReboot by mutableStateOf(ExploitPrefs.autoSoftRebootEnabled(context))
    var paramsFallback by mutableStateOf(ExploitPrefs.paramsFallback(context))

    fun onCustomEnabledChange(enabled: Boolean) {
        customEnabled = enabled
        ExploitPrefs.setCustomEnabled(context, enabled)
        if (enabled) {
            val snapshot = DeviceSnapshot.current()
            if (customFields.all { it == 0L }) {
                val matched = KernelLine.match(snapshot.device, snapshot.incremental)
                if (matched != null) {
                    ExploitPrefs.fillCustomFields(context, matched)
                    customFields = ExploitPrefs.customFields(context)
                }
            }
        }
    }
}

@Composable
private fun rememberMoreScreenState(): MoreScreenState {
    val context = LocalContext.current
    return remember { MoreScreenState(context) }
}

@Composable
fun MoreScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()

    val state = rememberMoreScreenState()

    val scheme = SettingsPrefs.payloadScheme(context)
    val dfrRoute = scheme != PayloadScheme.Default
    val standaloneRoute = scheme == PayloadScheme.DfrStandalone

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        popupHost = { },
        topBar = {
            TopAppBar(
                title = stringResource(R.string.nav_more),
                largeTitle = stringResource(R.string.nav_more),
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
            contentPadding = padding + PaddingValues(top = 12.dp, bottom = 20.dp),
            overscrollEffect = null,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CustomParamsBlock(state, standaloneRoute)
            KsuModeBlock(state, dfrRoute, standaloneRoute)
            ExploitParamsBlock(state, standaloneRoute)
            DebugBlock(state, standaloneRoute)
            AppGatesBlock(state, standaloneRoute)
        }
    }

    state.dialog?.let { d ->
        NumberInputDialog(
            title = stringResource(d.titleRes),
            summary = stringResource(R.string.range_format, fmtNumber(d.min), fmtNumber(d.max)),
            current = d.current,
            min = d.min,
            max = d.max,
            suffix = d.suffix,
            onConfirm = { value ->
                d.onConfirm(value)
                state.dialog = null
            },
            onDismiss = { state.dialog = null },
        )
    }

    state.hexFieldIndex?.let { index ->
        HexInputDialog(
            title = ExploitPrefs.CUSTOM_FIELD_KEYS[index],
            current = state.customFields[index],
            onConfirm = { value ->
                val updated = state.customFields.copyOf().also { it[index] = value }
                state.customFields = updated
                ExploitPrefs.setCustomField(context, index, value)
                state.hexFieldIndex = null
            },
            onDismiss = { state.hexFieldIndex = null },
        )
    }
}

private fun LazyListScope.CustomParamsBlock(state: MoreScreenState, standaloneRoute: Boolean) {
    if (!standaloneRoute) {
    item { SectionLabel(stringResource(R.string.section_custom_params)) }
    item {
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = stringResource(R.string.custom_params_enabled),
                summary = stringResource(R.string.custom_params_enabled_summary),
                startAction = {
                    Icon(
                        Icons.Rounded.Tune,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = stringResource(R.string.custom_params_enabled),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
                checked = state.customEnabled,
                onCheckedChange = { state.onCustomEnabledChange(it) },
            )
        }
    }
    item {
        AnimatedVisibility(
            visible = state.customEnabled,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    val snapshot = remember { DeviceSnapshot.current() }
                    val matched = remember(snapshot.device, snapshot.incremental) {
                        KernelLine.match(snapshot.device, snapshot.incremental)
                    }
                    Text(
                        text = if (matched != null) {
                            stringResource(R.string.custom_params_matched, matched.lineId)
                        } else {
                            stringResource(R.string.custom_params_no_match, snapshot.device, snapshot.incremental)
                        },
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                    ExploitPrefs.CUSTOM_FIELD_KEYS.forEachIndexed { index, key ->
                        if (index > 0) DividerMiuix()
                        val value = state.customFields[index]
                        NumberPreference(
                            title = key,
                            summary = stringResource(customFieldSummaries[index]),
                            valueText = "0x%x".format(value),
                            icon = Icons.Rounded.Tune,
                            onClick = { state.hexFieldIndex = index },
                        )
                    }
                }
            }
        }
    }
    }
}

private fun LazyListScope.KsuModeBlock(state: MoreScreenState, dfrRoute: Boolean, standaloneRoute: Boolean) {
    val context = state.context
    item { SectionLabel(stringResource(R.string.section_ksu_mode)) }
    item {
        Card(modifier = Modifier.fillMaxWidth()) {
            if (!standaloneRoute) {
            SwitchPreference(
                title = stringResource(R.string.settings_skip_ksu),
                summary = stringResource(
                    if (dfrRoute) R.string.settings_skip_ksu_summary_dfr
                    else R.string.settings_skip_ksu_summary,
                ),
                startAction = {
                    Icon(
                        Icons.Rounded.Link,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = stringResource(R.string.settings_skip_ksu),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
                checked = state.skipKsu,
                enabled = !dfrRoute,
                onCheckedChange = {
                    SettingsPrefs.setSkipKsu(context, it)
                    state.skipKsu = it
                },
            )
            DividerMiuix()
            }
            SwitchPreference(
                title = stringResource(R.string.settings_allow_shell),
                summary = stringResource(R.string.settings_allow_shell_summary),
                startAction = {
                    Icon(
                        Icons.Rounded.Terminal,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = stringResource(R.string.settings_allow_shell),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
                checked = state.allowShell,
                onCheckedChange = {
                    state.allowShell = it
                    ExploitPrefs.setAllowShellEnabled(context, it)
                },
            )
            if (standaloneRoute) {
                DividerMiuix()
                SwitchPreference(
                    title = stringResource(R.string.settings_auto_soft_reboot),
                    summary = stringResource(R.string.settings_auto_soft_reboot_summary),
                    startAction = {
                        Icon(
                            Icons.Rounded.Sync,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = stringResource(R.string.settings_auto_soft_reboot),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    },
                    checked = state.autoSoftReboot,
                    onCheckedChange = {
                        state.autoSoftReboot = it
                        ExploitPrefs.setAutoSoftRebootEnabled(context, it)
                    },
                )
            }
        }
    }
}

private fun LazyListScope.ExploitParamsBlock(state: MoreScreenState, standaloneRoute: Boolean) {
    val context = state.context
    if (!standaloneRoute) {
    item { SectionLabel(stringResource(R.string.section_exploit_params)) }
    item {
        Card(modifier = Modifier.fillMaxWidth()) {
            NumberPreference(
                title = stringResource(R.string.exploit_walk_timeout),
                summary = stringResource(R.string.exploit_walk_timeout_summary),
                valueText = "$state.walkTimeout s",
                icon = Icons.Rounded.Timer,
                onClick = { state.dialog = NumberDialog(R.string.exploit_walk_timeout, state.walkTimeout.toDouble(), 1.0, 10.0, "s") { state.walkTimeout = it.toInt(); ExploitPrefs.setWalkTimeoutSec(context, it.toInt()) } },
            )
            DividerMiuix()
            NumberPreference(
                title = stringResource(R.string.exploit_walk_delay),
                summary = stringResource(R.string.exploit_walk_delay_summary),
                valueText = "${state.walkDelayMs}ms",
                icon = Icons.Rounded.Speed,
                onClick = { state.dialog = NumberDialog(R.string.exploit_walk_delay, state.walkDelayMs, 0.0, 1_000.0, "ms") { state.walkDelayMs = it; ExploitPrefs.setWalkDelay(context, (it * 1_000).toInt()) } },
            )
            DividerMiuix()
            SwitchPreference(
                title = stringResource(R.string.exploit_params_fallback),
                summary = stringResource(R.string.exploit_params_fallback_summary),
                startAction = {
                    Icon(
                        Icons.AutoMirrored.Rounded.AltRoute,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = stringResource(R.string.exploit_params_fallback),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
                checked = state.paramsFallback,
                onCheckedChange = {
                    state.paramsFallback = it
                    ExploitPrefs.setParamsFallback(context, it)
                },
            )
        }
    }
    }
}

private fun LazyListScope.DebugBlock(state: MoreScreenState, standaloneRoute: Boolean) {
    val context = state.context
    if (!standaloneRoute) {
    item { SectionLabel(stringResource(R.string.section_debug)) }
    item {
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = stringResource(R.string.exploit_debug),
                summary = stringResource(R.string.exploit_debug_summary),
                startAction = {
                    Icon(
                        Icons.Rounded.BugReport,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = stringResource(R.string.exploit_debug),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
                checked = state.debugEnabled,
                onCheckedChange = {
                    state.debugEnabled = it
                    ExploitPrefs.setDebugEnabled(context, it)
                },
            )
        }
    }
    }
}

private fun LazyListScope.AppGatesBlock(state: MoreScreenState, standaloneRoute: Boolean) {
    val context = state.context
    if (!standaloneRoute) {
    item { SectionLabel(stringResource(R.string.section_app_gates)) }
    item {
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = stringResource(R.string.exploit_load_gate),
                summary = stringResource(R.string.exploit_load_gate_summary),
                startAction = {
                    Icon(
                        Icons.Rounded.Speed,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = stringResource(R.string.exploit_load_gate),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
                checked = state.loadGateEnabled,
                onCheckedChange = {
                    state.loadGateEnabled = it
                    ExploitPrefs.setLoadGateEnabled(context, it)
                },
            )
            AnimatedVisibility(
                visible = state.loadGateEnabled,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    DividerMiuix()
                    NumberPreference(
                        title = stringResource(R.string.exploit_load_threshold),
                        summary = stringResource(R.string.exploit_load_threshold_summary),
                        valueText = String.format(java.util.Locale.US, "%.1f", state.loadThreshold),
                        icon = Icons.Rounded.Speed,
                        onClick = { state.dialog = NumberDialog(R.string.exploit_load_threshold, state.loadThreshold, 1.0, 20.0, "") { state.loadThreshold = it; ExploitPrefs.setLoadThreshold(context, it) } },
                    )
                    DividerMiuix()
                    NumberPreference(
                        title = stringResource(R.string.exploit_load_wait),
                        summary = stringResource(R.string.exploit_load_wait_summary),
                        valueText = "$state.loadWaitSec s",
                        icon = Icons.Rounded.HourglassTop,
                        onClick = { state.dialog = NumberDialog(R.string.exploit_load_wait, state.loadWaitSec.toDouble(), 10.0, 300.0, "s") { state.loadWaitSec = it.toInt(); ExploitPrefs.setLoadWaitMaxSec(context, it.toInt()) } },
                    )
                }
            }
            DividerMiuix()
            NumberPreference(
                title = stringResource(R.string.exploit_stall_timeout),
                summary = stringResource(R.string.exploit_stall_timeout_summary),
                valueText = "$state.stallSec s",
                icon = Icons.Rounded.Timer,
                onClick = { state.dialog = NumberDialog(R.string.exploit_stall_timeout, state.stallSec.toDouble(), 10.0, 300.0, "s") { state.stallSec = it.toInt(); ExploitPrefs.setStallTimeoutSec(context, it.toInt()) } },
            )
            DividerMiuix()
            NumberPreference(
                title = stringResource(R.string.exploit_total_timeout),
                summary = stringResource(R.string.exploit_total_timeout_summary),
                valueText = "$state.totalSec s",
                icon = Icons.Rounded.Schedule,
                onClick = { state.dialog = NumberDialog(R.string.exploit_total_timeout, state.totalSec.toDouble(), 60.0, 600.0, "s") { state.totalSec = it.toInt(); ExploitPrefs.setTotalTimeoutSec(context, it.toInt()) } },
            )
        }
    }
    }
}

private data class NumberDialog(
    val titleRes: Int,
    val current: Double,
    val min: Double,
    val max: Double,
    val suffix: String,
    val onConfirm: (Double) -> Unit,
)

private fun fmtNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

private val customFieldSummaries = intArrayOf(
    R.string.custom_field_summary_tracefs_event_id,
    R.string.custom_field_summary_tracefs_worker_caller_off,
    R.string.custom_field_summary_root_task_group_off,
    R.string.custom_field_summary_init_task_off,
    R.string.custom_field_summary_misc_list_off,
    R.string.custom_field_summary_uinput_misc_off,
    R.string.custom_field_summary_simple_attr_read_off,
    R.string.custom_field_summary_simple_attr_write_off,
    R.string.custom_field_summary_debugfs_u64_get_off,
    R.string.custom_field_summary_debugfs_u64_set_off,
    R.string.custom_field_summary_default_llseek_off,
    R.string.custom_field_summary_debugfs_u64_format_off,
    R.string.custom_field_summary_call_usermodehelper_exec_work_off,
    R.string.custom_field_summary_system_unbound_wq_off,
    R.string.custom_field_summary_selinux_enforcing_off,
    R.string.custom_field_summary_phys_offset,
    R.string.custom_field_summary_phys_load,
)

@Composable
private fun NumberPreference(
    title: String,
    summary: String,
    valueText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = summary,
        startAction = {
            Icon(
                icon,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = title,
                tint = MiuixTheme.colorScheme.onBackground,
            )
        },
        endActions = {
            Text(
                text = valueText,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun DividerMiuix() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        thickness = 0.5.dp,
        color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f),
    )
}
