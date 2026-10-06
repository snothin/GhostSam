package com.snothin.ghostsam.ui.screen

import android.content.Context
import android.content.res.Resources
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.snothin.ghostsam.R
import com.snothin.ghostsam.data.channel.SelinuxController
import com.snothin.ghostsam.data.channel.SelinuxState
import com.snothin.ghostsam.data.channel.SuDaemonChannel
import com.snothin.ghostsam.data.device.KernelSuProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

internal class SelinuxSectionState(
    private val context: Context,
    private val resources: Resources,
    private val scope: CoroutineScope,
) {
    var selinuxState by mutableStateOf(SelinuxState.Unknown)
    var showSelinuxConfirm by mutableStateOf(false)

    private fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun refresh() {
        scope.launch { selinuxState = SelinuxController.query() }
    }

    fun locked(noksuSession: Boolean): Boolean =
        noksuSession && selinuxState == SelinuxState.Permissive

    fun onSummaryClick(noksuSession: Boolean) {
        if (locked(noksuSession)) {
            toast(resources.getString(R.string.selinux_noksu_permissive_locked))
        } else if (selinuxState == SelinuxState.Unknown) {
            toggle(noksuSession)
        } else {
            showSelinuxConfirm = true
        }
    }

    fun toggle(noksuSession: Boolean) {
        // no-ksu permissive session: switching back to enforcing is forbidden; SELinux denies
        // temp_su.sock access and the channel locks up (reboot required).
        if (locked(noksuSession)) {
            toast(resources.getString(R.string.selinux_noksu_permissive_locked))
        } else {
            val current = selinuxState
            if (current == SelinuxState.Unknown) {
                scope.launch {
                    val denied = withContext(Dispatchers.IO) {
                        KernelSuProbe.isRootedWithSuDenied(context)
                    }
                    toast(
                        resources.getString(
                            if (denied) R.string.tools_su_denied else R.string.tools_no_permission,
                        ),
                    )
                }
            } else {
                scope.launch {
                    if (SelinuxController.toggle(current)) {
                        refresh()
                    } else {
                        toast(resources.getString(R.string.selinux_toggle_failed))
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberSelinuxSectionState(
    shizukuConnected: Boolean,
    suAvailable: Boolean,
    adbConnected: Boolean,
): SelinuxSectionState {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val state = remember { SelinuxSectionState(context, resources, scope) }
    LaunchedEffect(shizukuConnected, suAvailable, adbConnected) {
        val channelOk = shizukuConnected || suAvailable || adbConnected ||
            withContext(Dispatchers.IO) { SuDaemonChannel.isAvailable() }
        if (channelOk) state.refresh()
    }
    return state
}

@Composable
internal fun SelinuxConfirmDialog(state: SelinuxSectionState, noksuSession: Boolean) {
    if (state.showSelinuxConfirm && state.selinuxState != SelinuxState.Unknown) {
        val targetLabel = stringResource(
            if (state.selinuxState == SelinuxState.Enforcing) R.string.selinux_permissive
            else R.string.advanced_selinux_enforcing,
        )
        WindowDialog(
            show = true,
            title = stringResource(R.string.selinux_confirm_title),
            summary = stringResource(
                R.string.selinux_confirm_body,
                selinuxLabel(state.selinuxState),
                targetLabel,
            ),
            onDismissRequest = { state.showSelinuxConfirm = false },
            content = {
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = { state.showSelinuxConfirm = false },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(android.R.string.ok),
                        onClick = {
                            state.showSelinuxConfirm = false
                            state.toggle(noksuSession)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            },
        )
    }
}
