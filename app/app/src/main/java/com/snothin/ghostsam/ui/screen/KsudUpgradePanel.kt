package com.snothin.ghostsam.ui.screen

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snothin.ghostsam.R
import com.snothin.ghostsam.data.Contract
import com.snothin.ghostsam.data.channel.AdbController
import com.snothin.ghostsam.data.channel.ExecutionMode
import com.snothin.ghostsam.data.channel.ExploitChannel
import com.snothin.ghostsam.data.channel.SuDaemonChannel
import com.snothin.ghostsam.data.device.KernelSuProbe
import com.snothin.ghostsam.ui.component.AutoScrollToBottom
import com.snothin.ghostsam.ui.component.LogText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun KsudLogBox(text: String) {
    val scrollState = rememberScrollState()
    AutoScrollToBottom(scrollState, text.length)
    top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
        LogText(
            text = text.ifBlank { stringResource(R.string.ksud_load_log_wait) },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .verticalScroll(scrollState)
                .padding(14.dp),
        )
    }
}

@Composable
internal fun KsudSourceRow(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = MiuixTheme.colorScheme.onBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else Color.Transparent,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = contentColor.copy(alpha = if (enabled) 1f else 0.35f),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = if (enabled) 1f else 0.4f),
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = if (enabled) 1f else 0.4f),
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircleOutline,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

private const val KSudLogLimit = 8000

private const val KSUD_LOG_FRAME_MS = 120L

private const val MAX_KSUD_BYTES = 64 * 1024 * 1024

internal class KsudUpgradeState(
    private val context: Context,
    private val evidence: HomeEvidence,
    private val scope: CoroutineScope,
) {
    var showKsudDialog by mutableStateOf(false)
    var ksudSourceBuiltin by mutableStateOf(true)
    var ksudFileUri by mutableStateOf<Uri?>(null)
    var ksudFileName by mutableStateOf<String?>(null)
    var loadingKsud by mutableStateOf(false)
    var ksudLog by mutableStateOf("")
    var ksudLoadFailed by mutableStateOf(false)

    var launchFilePicker: () -> Unit = {}

    fun open() {
        ksudLoadFailed = false
        ksudLog = ""
        showKsudDialog = true
    }

    private fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    private fun finalizeKsudUpgrade() {
        evidence.finalizeUpgrade()
        showKsudDialog = false
        toast(context.getString(R.string.ksud_load_success))
    }

    private suspend fun probeModule(executionMode: ExecutionMode): Boolean {
        repeat(12) {
            val ok = withContext(Dispatchers.IO) {
                if (executionMode == ExecutionMode.Adb && !AdbController.isConnected()) {
                    AdbController.reconnectLast() || AdbController.isConnected()
                }
                KernelSuProbe.isActiveViaShizuku()
            }
            if (ok) return true
            delay(1_000)
        }
        return false
    }

    private fun readKsudBytes(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                total += n
                if (total > MAX_KSUD_BYTES) return@runCatching null
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
    }.getOrNull()

    fun start(executionMode: ExecutionMode) {
        if (loadingKsud) return
        // Set synchronously (TOCTOU guard): no double-start before the coroutine's first frame.
        loadingKsud = true
        scope.launch {
            ksudLoadFailed = false
            ksudLog = ""
            val pending = StringBuilder()
            val pendingAt = longArrayOf(0L)
            val flushJob = arrayOfNulls<Job>(1)
            fun flushKsudLog() {
                val chunk = synchronized(pending) {
                    if (pending.isEmpty()) return
                    val text = pending.toString()
                    pending.setLength(0)
                    pendingAt[0] = android.os.SystemClock.elapsedRealtime()
                    text
                }
                ksudLog = (ksudLog + chunk).takeLast(KSudLogLimit)
            }
            fun appendKsudLog(text: String) {
                val due: Boolean
                val schedule: Boolean
                synchronized(pending) {
                    pending.append(text)
                    due = android.os.SystemClock.elapsedRealtime() - pendingAt[0] >= KSUD_LOG_FRAME_MS
                    schedule = !due && flushJob[0]?.isActive != true
                }
                when {
                    due -> flushKsudLog()
                    schedule -> flushJob[0] = scope.launch {
                        delay(KSUD_LOG_FRAME_MS)
                        flushKsudLog()
                    }
                }
            }
            fun log(line: String) = appendKsudLog(line + "\n")
            try {
                // 0) Module precheck: the temp-channel socket is unlinked only on 'K' success, so a gone
                // socket means the upgrade already finished; otherwise report the channel error.
                if (!withContext(Dispatchers.IO) { SuDaemonChannel.isAvailable() }) {
                    log("[i] temp channel gone — re-checking module…")
                    if (probeModule(executionMode)) {
                        log("[+] module verified (upgrade already done)")
                        finalizeKsudUpgrade()
                    } else {
                        log("[!] channel unavailable and module not detected")
                        ksudLoadFailed = true
                        toast(context.getString(R.string.ksud_load_failed, "channel gone"))
                    }
                    return@launch
                }
                val uri = ksudFileUri
                if (!ksudSourceBuiltin && uri != null) {
                    val bytes = withContext(Dispatchers.IO) { readKsudBytes(uri) }
                    if (bytes == null) {
                        log("[!] cannot read selected ksud file")
                        ksudLoadFailed = true
                        toast(context.getString(R.string.ksud_file_read_failed))
                        return@launch
                    }
                    log("[i] pushing ${ksudFileName ?: "custom ksud"}…")
                    withContext(Dispatchers.IO) {
                        ExploitChannel.writeFile(Contract.KSUD, "755", bytes.inputStream())
                    }
                    log("[i] ksud pushed")
                } else {
                    log("[i] source: bundled ksud (already staged)")
                }
                log("[i] refreshing .ksud-stage…")
                val stageOut = SuDaemonChannel.execPrivileged(
                    "cp ${Contract.KSUD} ${Contract.KSUD_STAGE} 2>&1 && " +
                        "chmod 755 ${Contract.KSUD_STAGE} 2>&1",
                )
                when {
                    stageOut == null -> log("[!] channel unavailable (reconnect wireless debugging?)")
                    stageOut.isNotBlank() -> log("[!] stage: $stageOut")
                    else -> log("[i] stage ready")
                }
                log("[i] late-load…")
                val output = SuDaemonChannel.execLateLoad { chunk ->
                    appendKsudLog(chunk)
                }
                if (output == null) {
                    log("[!] late-load failed (daemon unreachable or timeout)")
                }
                // 3) Confirm: module probe; no module but the socket is gone also counts as loaded
                // (the daemon unlinks it only on the K-success path).
                log("[i] probing kernel module…")
                var ok = probeModule(executionMode)
                if (!ok && !withContext(Dispatchers.IO) { SuDaemonChannel.isAvailable() }) {
                    log("[i] socket closed by daemon → late-load succeeded")
                    ok = true
                }
                log(if (ok) "[+] module verified" else "[-] module not detected")
                if (ok) {
                    finalizeKsudUpgrade()
                } else {
                    ksudLoadFailed = true
                    toast(
                        context.getString(
                            R.string.ksud_load_failed,
                            output?.take(160) ?: "channel error",
                        ),
                    )
                }
            } finally {
                flushKsudLog()
                loadingKsud = false
            }
        }
    }
}

@Composable
internal fun rememberKsudUpgradeState(evidence: HomeEvidence): KsudUpgradeState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { KsudUpgradeState(context, evidence, scope) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            state.ksudFileUri = uri
            scope.launch {
                state.ksudFileName = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                        }
                    }.getOrNull()
                }
            }
            state.ksudSourceBuiltin = false
        }
    }
    state.launchFilePicker = { launcher.launch(arrayOf("*/*")) }
    return state
}

@Composable
internal fun KsudUpgradeDialog(state: KsudUpgradeState, executionMode: ExecutionMode) {
    if (state.showKsudDialog) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.ksud_load_title),
            summary = stringResource(
                if (state.loadingKsud || state.ksudLoadFailed) R.string.ksud_load_log_summary
                else R.string.ksud_load_summary,
            ),
            onDismissRequest = { if (!state.loadingKsud) state.showKsudDialog = false },
            content = {
                Column {
                    if (state.loadingKsud || state.ksudLoadFailed) {
                        KsudLogBox(text = state.ksudLog)
                    } else {
                        KsudSourceRow(
                            icon = Icons.Rounded.VerifiedUser,
                            title = stringResource(R.string.payload_builtin),
                            description = stringResource(R.string.ksud_source_builtin_desc),
                            selected = state.ksudSourceBuiltin,
                            enabled = true,
                            onClick = {
                                state.ksudSourceBuiltin = true
                                state.ksudFileUri = null
                                state.ksudFileName = null
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 0.5.dp,
                            color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f),
                        )
                        KsudSourceRow(
                            icon = Icons.Rounded.Code,
                            title = stringResource(R.string.ksud_source_file),
                            description = state.ksudFileName
                                ?: stringResource(R.string.ksud_source_file_desc),
                            selected = !state.ksudSourceBuiltin && state.ksudFileUri != null,
                            enabled = true,
                            onClick = { state.launchFilePicker() },
                        )
                    }
                    if (state.ksudLoadFailed) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(
                                text = stringResource(R.string.ksud_load_close),
                                onClick = { state.showKsudDialog = false },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(20.dp))
                            TextButton(
                                text = stringResource(R.string.ksud_load_back),
                                onClick = { state.ksudLoadFailed = false },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            if (!state.loadingKsud) {
                                TextButton(
                                    text = stringResource(android.R.string.cancel),
                                    onClick = { state.showKsudDialog = false },
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(20.dp))
                            }
                            TextButton(
                                text = stringResource(
                                    if (state.loadingKsud) R.string.ksud_load_loading
                                    else R.string.ksud_load_confirm,
                                ),
                                onClick = {
                                    if (state.loadingKsud) return@TextButton
                                    state.ksudLog = ""
                                    state.ksudLoadFailed = false
                                    state.start(executionMode)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    }
                }
            },
        )
    }
}
