package com.snothin.ghostsam.ui.screen

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.snothin.ghostsam.R
import com.snothin.ghostsam.data.channel.ExploitChannel
import com.snothin.ghostsam.data.channel.SuController
import com.snothin.ghostsam.data.exploit.PayloadScheme
import com.snothin.ghostsam.data.exploit.RunState
import com.snothin.ghostsam.system.CompanionClient
import com.snothin.ghostsam.system.CompanionInstaller
import com.snothin.ghostsam.system.CompanionPack
import com.snothin.ghostsam.system.SystemPersist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val COMPANION_STATUS_CLEAR_MS = 3_000L

internal enum class InjectionState { Unknown, Checking, NoRoot, Injected, Absent, Failed }

internal class CompanionSectionState(context: Context) {
    var companionState by mutableStateOf(CompanionClient.state(context))
    var builtinCompanion by mutableStateOf<CompanionPack.Builtin?>(null)
    var injectionState by mutableStateOf(InjectionState.Unknown)
    var injectionSuAvailable by mutableStateOf(false)
    var injectCheckTick by mutableIntStateOf(0)
    var showInstallConfirm by mutableStateOf(false)
    var showUninstallConfirm by mutableStateOf(false)
    var showInjectRemoveConfirm by mutableStateOf(false)
    var keepInjection by mutableStateOf(true)
    var companionStatus by mutableStateOf("")
    var companionBusy by mutableStateOf(false)
}

@Composable
internal fun rememberCompanionSectionState(payloadScheme: PayloadScheme): CompanionSectionState {
    val context = LocalContext.current
    val state = remember { CompanionSectionState(context) }
    LaunchedEffect(payloadScheme) {
        state.builtinCompanion = if (payloadScheme == PayloadScheme.Dfr) {
            runCatching { withContext(Dispatchers.IO) { CompanionPack.builtin(context) } }.getOrNull()
        } else {
            null
        }
    }
    LaunchedEffect(payloadScheme, state.injectCheckTick) {
        if (payloadScheme != PayloadScheme.Dfr) {
            state.injectionState = InjectionState.Unknown
            return@LaunchedEffect
        }
        state.injectionState = InjectionState.Checking
        val root = withContext(Dispatchers.IO) { SuController.isAvailable() }
        state.injectionSuAvailable = root
        state.injectionState = if (root) {
            withContext(Dispatchers.IO) { detectInjectionState(context) }
        } else {
            InjectionState.NoRoot
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.companionState = CompanionClient.state(context)
                state.injectCheckTick += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        try {
            awaitCancellation()
        } finally {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(state.companionStatus, state.companionBusy) {
        if (state.companionStatus.isNotEmpty() && !state.companionBusy) {
            delay(COMPANION_STATUS_CLEAR_MS)
            state.companionStatus = ""
        }
    }
    return state
}

@Composable
internal fun SettingsCompanionSection(
    state: CompanionSectionState,
    payloadScheme: PayloadScheme,
) {
    if (payloadScheme != PayloadScheme.Dfr) return
    val context = LocalContext.current
    val companionReady = state.companionState.installed
    val builtin = state.builtinCompanion
    val needsUpdate = companionReady && builtin != null &&
        builtin.versionCode > (state.companionState.versionCode ?: 0L)

    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        thickness = 0.5.dp,
        color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f),
    )
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = if (companionReady) {
                state.companionState.versionName?.let {
                    context.getString(R.string.settings_companion_state_installed, it)
                } ?: context.getString(R.string.settings_companion_state_installed_plain)
            } else {
                context.getString(R.string.settings_companion_state_absent)
            },
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Text(
            text = context.getString(
                when (state.injectionState) {
                    InjectionState.Unknown -> R.string.settings_companion_inject_unknown
                    InjectionState.Checking -> R.string.settings_companion_inject_checking
                    // No root: "component running as system uid" is itself direct evidence of injection
                    // (a shared-uid install must pass signature checks).
                    InjectionState.NoRoot -> if (
                        state.companionState.installed && state.companionState.uid == 1000
                    ) {
                        R.string.settings_companion_inject_effective
                    } else {
                        R.string.settings_companion_inject_noroot
                    }
                    InjectionState.Injected -> R.string.settings_companion_inject_injected
                    InjectionState.Absent -> R.string.settings_companion_inject_absent
                    InjectionState.Failed -> R.string.settings_companion_inject_failed
                },
            ),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
    if (builtin != null && (!companionReady || needsUpdate)) {
        ArrowPreference(
            title = stringResource(
                if (needsUpdate) R.string.settings_companion_update
                else R.string.settings_companion_install,
            ),
            summary = if (needsUpdate) {
                context.getString(
                    R.string.settings_companion_update_summary,
                    builtin.versionName,
                    state.companionState.versionName ?: "?",
                )
            } else {
                context.getString(
                    R.string.settings_companion_install_summary,
                    builtin.versionName,
                )
            },
            startAction = {
                Icon(
                    Icons.Rounded.AdminPanelSettings,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(
                        if (needsUpdate) R.string.settings_companion_update
                        else R.string.settings_companion_install,
                    ),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            },
            onClick = {
                when {
                    state.companionBusy -> state.companionStatus =
                        context.getString(R.string.settings_companion_uninstall_busy)
                    RunState.exploitActive -> state.companionStatus =
                        context.getString(R.string.settings_exec_busy_hint)
                    else -> state.showInstallConfirm = true
                }
            },
        )
    }
    if (companionReady) {
        ArrowPreference(
            title = stringResource(R.string.settings_companion_uninstall),
            summary = stringResource(R.string.settings_companion_uninstall_summary),
            startAction = {
                Icon(
                    Icons.Rounded.Delete,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(R.string.settings_companion_uninstall),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            },
            onClick = {
                when {
                    state.companionBusy -> state.companionStatus =
                        context.getString(R.string.settings_companion_uninstall_busy)
                    RunState.exploitActive -> state.companionStatus =
                        context.getString(R.string.settings_exec_busy_hint)
                    else -> {
                        state.keepInjection = true
                        state.showUninstallConfirm = true
                    }
                }
            },
        )
    } else if (state.injectionState == InjectionState.Injected) {
        ArrowPreference(
            title = stringResource(R.string.settings_companion_inject_remove),
            summary = stringResource(R.string.settings_companion_inject_remove_summary),
            startAction = {
                Icon(
                    Icons.Rounded.Delete,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(R.string.settings_companion_inject_remove),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            },
            onClick = {
                when {
                    state.companionBusy -> state.companionStatus =
                        context.getString(R.string.settings_companion_uninstall_busy)
                    RunState.exploitActive -> state.companionStatus =
                        context.getString(R.string.settings_exec_busy_hint)
                    else -> state.showInjectRemoveConfirm = true
                }
            },
        )
    }
    if (state.companionStatus.isNotEmpty()) {
        Text(
            text = state.companionStatus,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
internal fun SettingsCompanionDialogs(state: CompanionSectionState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (state.showInstallConfirm) {
        val builtin = state.builtinCompanion
        val asUpdate = state.companionState.installed
        OverlayDialog(
            show = true,
            title = stringResource(
                if (asUpdate) R.string.settings_companion_update_confirm_title
                else R.string.settings_companion_install_confirm_title,
            ),
            summary = if (asUpdate) {
                context.getString(
                    R.string.settings_companion_update_confirm_body,
                    builtin?.versionName ?: "?",
                    state.companionState.versionName ?: "?",
                )
            } else {
                context.getString(
                    R.string.settings_companion_install_confirm_body,
                    builtin?.versionName ?: "?",
                )
            },
            onDismissRequest = { state.showInstallConfirm = false },
            content = {
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = { state.showInstallConfirm = false },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(
                            if (asUpdate) R.string.settings_companion_update
                            else R.string.settings_companion_install,
                        ),
                        onClick = {
                            state.showInstallConfirm = false
                            val target = state.builtinCompanion
                            if (target == null) {
                                state.companionStatus = context.getString(
                                    R.string.settings_companion_install_failed,
                                    "builtin asset unavailable",
                                )
                            } else {
                                state.companionBusy = true
                                state.companionStatus = context.getString(R.string.settings_companion_install_started)
                                scope.launch {
                                    val text = runCatching {
                                        CompanionInstaller.install(context, asUpdate, target) { state.companionStatus = it }
                                    }.getOrElse {
                                        context.getString(
                                            R.string.settings_companion_install_failed,
                                            it.message ?: it.javaClass.simpleName,
                                        )
                                    }
                                    state.companionState = withContext(Dispatchers.IO) { CompanionClient.state(context) }
                                    state.companionStatus = text
                                    state.injectCheckTick += 1
                                    state.companionBusy = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        )
    }

    if (state.showUninstallConfirm) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.settings_companion_uninstall_confirm_title),
            summary = stringResource(R.string.settings_companion_uninstall_confirm_body),
            onDismissRequest = { state.showUninstallConfirm = false },
            content = {
                Column {
                    SwitchPreference(
                        title = stringResource(R.string.settings_companion_uninstall_keep),
                        summary = stringResource(
                            if (state.injectionSuAvailable) {
                                R.string.settings_companion_uninstall_keep_summary
                            } else {
                                R.string.settings_companion_uninstall_keep_locked
                            },
                        ),
                        checked = state.keepInjection,
                        onCheckedChange = { checked ->
                            state.keepInjection = if (state.injectionSuAvailable) checked else true
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            text = stringResource(android.R.string.cancel),
                            onClick = { state.showUninstallConfirm = false },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(20.dp))
                        TextButton(
                            text = stringResource(R.string.settings_companion_uninstall),
                            onClick = {
                                state.showUninstallConfirm = false
                                state.companionBusy = true
                                state.companionStatus = context.getString(R.string.settings_companion_uninstall_started)
                                val keep = state.keepInjection
                                scope.launch {
                                    val lines = mutableListOf<String>()
                                    // 1) Main path: root direct uninstall + asset cleanup; the component's self-uninstall is
                                    // SELinux-denied on real devices.
                                    val root = withContext(Dispatchers.IO) { SuController.isAvailable() }
                                    var gone = false
                                    if (root) {
                                        withContext(Dispatchers.IO) {
                                            SuController.execCommand(SystemPersist.forceUninstallCommand())
                                            SuController.execCommand(SystemPersist.cleanAssetsCommand())
                                        }
                                        gone = awaitCompanionUninstalled(context, 30_000)
                                    }
                                    if (!gone && !root && ExploitChannel.isAvailable()) {
                                        lines += context.getString(R.string.settings_companion_uninstall_via_channel)
                                        val out = ExploitChannel.execShell(SystemPersist.forceUninstallCommand(), 60_000)
                                        gone = awaitCompanionUninstalled(context, 20_000)
                                        if (!gone) {
                                            out.lineSequence().firstOrNull { it.isNotBlank() }
                                                ?.let { lines += it.take(160) }
                                        }
                                    }
                                    if (!gone) {
                                        if (!root) {
                                            lines += context.getString(R.string.settings_companion_uninstall_fallback)
                                        }
                                        runCatching {
                                            CompanionClient.request(context, "uninstall-self", timeoutMs = 30_000L)
                                        }
                                        gone = awaitCompanionUninstalled(context, 20_000)
                                    }
                                    lines += if (gone) {
                                        context.getString(R.string.settings_companion_uninstall_done)
                                    } else {
                                        context.getString(
                                            R.string.settings_companion_uninstall_failed,
                                            context.getString(
                                                if (root) {
                                                    R.string.settings_companion_uninstall_no_reply
                                                } else {
                                                    R.string.settings_companion_uninstall_no_root
                                                },
                                            ),
                                        )
                                    }
                                    // 4) Injection disposal only after the companion is gone; removing the key first would
                                    // fail signature checks on the next boot scan.
                                    if (gone) {
                                        val root = withContext(Dispatchers.IO) { SuController.isAvailable() }
                                        when {
                                            keep -> lines += context.getString(R.string.settings_companion_inject_kept)
                                            !root -> lines += context.getString(
                                                R.string.settings_companion_uninstall_keep_locked,
                                            )
                                            else -> {
                                                val result = withContext(Dispatchers.IO) {
                                                    SystemPersist.run(
                                                        context, "uninstall",
                                                        exec = { SuController.execCommand(it) },
                                                    )
                                                }
                                                lines += if (result.ok) {
                                                    context.getString(R.string.settings_companion_inject_removed)
                                                } else {
                                                    context.getString(
                                                        R.string.settings_companion_inject_remove_failed,
                                                        result.failure ?: "?",
                                                    )
                                                }
                                            }
                                        }
                                    } else if (!keep) {
                                        lines += context.getString(R.string.settings_companion_inject_skipped_installed)
                                    }
                                    state.companionState = withContext(Dispatchers.IO) { CompanionClient.state(context) }
                                    state.companionStatus = lines.joinToString("\n")
                                    state.injectCheckTick += 1
                                    state.companionBusy = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
        )
    }

    if (state.showInjectRemoveConfirm) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.settings_companion_inject_remove_confirm_title),
            summary = stringResource(R.string.settings_companion_inject_remove_confirm_body),
            onDismissRequest = { state.showInjectRemoveConfirm = false },
            content = {
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = { state.showInjectRemoveConfirm = false },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(R.string.settings_companion_inject_remove),
                        onClick = {
                            state.showInjectRemoveConfirm = false
                            state.companionBusy = true
                            state.companionStatus = context.getString(R.string.settings_companion_inject_remove_started)
                            scope.launch {
                                val installed = withContext(Dispatchers.IO) {
                                    CompanionClient.state(context).installed
                                }
                                val line = if (installed) {
                                    context.getString(R.string.settings_companion_inject_skipped_installed)
                                } else {
                                    val result = withContext(Dispatchers.IO) {
                                        SystemPersist.run(
                                            context, "uninstall",
                                            exec = { SuController.execCommand(it) },
                                        )
                                    }
                                    if (result.ok) {
                                        context.getString(R.string.settings_companion_inject_removed)
                                    } else {
                                        context.getString(
                                            R.string.settings_companion_inject_remove_failed,
                                            result.failure ?: "?",
                                        )
                                    }
                                }
                                state.companionStatus = line
                                state.injectCheckTick += 1
                                state.companionBusy = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        )
    }
}

private suspend fun detectInjectionState(context: Context): InjectionState {
    val result = SystemPersist.run(context, "check", exec = { SuController.execCommand(it) })
    return when {
        !result.ok -> InjectionState.Failed
        SystemPersist.parseInjected(result.output) == true -> InjectionState.Injected
        SystemPersist.parseInjected(result.output) == false -> InjectionState.Absent
        else -> InjectionState.Failed
    }
}

private suspend fun awaitCompanionUninstalled(context: Context, timeoutMs: Long): Boolean {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
        if (!withContext(Dispatchers.IO) { CompanionClient.state(context).installed }) return true
        delay(700)
    }
    return !withContext(Dispatchers.IO) { CompanionClient.state(context).installed }
}
