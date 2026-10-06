package com.snothin.ghostsam.ui.screen

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snothin.ghostsam.R
import com.snothin.ghostsam.data.channel.AdbController
import com.snothin.ghostsam.data.channel.ShizukuController
import com.snothin.ghostsam.data.channel.SuController
import com.snothin.ghostsam.data.channel.SuDaemonChannel
import com.snothin.ghostsam.ui.component.AutoScrollToBottom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TERMINAL_BUFFER_LIMIT = 8_000

private const val TERMINAL_FRAME_MS = 80L

@Composable
fun SystemTerminalScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scrollBehavior = MiuixScrollBehavior()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var input by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("$ ") }
    val buffer = remember { MutableStateFlow("") }
    val process = remember { mutableStateOf<Process?>(null) }
    // Disposal flag: if the page exits during the NonCancellable spawn, reap the process at
    // once (a created root shell must not be left ownerless).
    val disposed = remember { mutableStateOf(false) }

    val pending = remember { StringBuilder() }
    val pendingAt = remember { longArrayOf(0L) }
    val flushJob = remember { arrayOfNulls<Job>(1) }

    fun flushPending() {
        val chunk = synchronized(pending) {
            if (pending.isEmpty()) return
            val text = pending.toString()
            pending.setLength(0)
            pendingAt[0] = SystemClock.elapsedRealtime()
            text
        }
        buffer.update { (it + chunk).takeLast(TERMINAL_BUFFER_LIMIT) }
    }

    fun appendFramed(line: String) {
        val due: Boolean
        val schedule: Boolean
        synchronized(pending) {
            pending.append(line).append('\n')
            due = SystemClock.elapsedRealtime() - pendingAt[0] >= TERMINAL_FRAME_MS
            schedule = !due && flushJob[0]?.isActive != true
        }
        when {
            due -> flushPending()
            schedule -> flushJob[0] = scope.launch {
                delay(TERMINAL_FRAME_MS)
                flushPending()
            }
        }
    }

    LaunchedEffect(Unit) {
        fun adopt(p: Process): Boolean {
            if (disposed.value) {
                p.destroy()
                return false
            }
            process.value = p
            return true
        }

        var rootShell = false
        val suProc = withContext(Dispatchers.IO + NonCancellable) { SuController.spawnShell() }
        rootShell = suProc != null
        if (suProc != null && !adopt(suProc)) return@LaunchedEffect
        val proc = suProc
            // su_daemon channel (probe-based), usable as no-ksu residue; the no-arg client is the 'I'
            // interactive root shell.
            ?: withContext(Dispatchers.IO) { SuDaemonChannel.spawnShell()?.also { rootShell = true } }
                ?.takeIf { adopt(it) }
            ?: withContext(Dispatchers.IO) {
                runCatching {
                    when {
                        AdbController.isConnected() -> AdbController.exec(arrayOf("sh"))
                        ShizukuController.isGranted() -> ShizukuController.exec(arrayOf("sh"))
                        else -> null
                    }
                }.getOrNull()
            }?.takeIf { adopt(it) }
        if (proc == null) {
            buffer.update { it + resources.getString(R.string.terminal_failed) }
            return@LaunchedEffect
        }
        if (rootShell) prompt = "# "
        buffer.update { it + resources.getString(R.string.terminal_banner) }

        fun readLoop(source: () -> java.io.BufferedReader) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val reader = source()
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            appendFramed(line)
                        }
                        flushPending()
                    } catch (_: Exception) {
                    }
                }
            }
        }
        readLoop { proc.inputStream.bufferedReader() }
        readLoop { proc.errorStream.bufferedReader() }

        withContext(Dispatchers.IO) {
            runCatching {
                proc.outputStream.write("pwd\nid\n".toByteArray(Charsets.UTF_8))
                proc.outputStream.flush()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            disposed.value = true
            process.value?.destroy()
        }
    }

    fun sendCommand() {
        val cmd = input.trim()
        if (cmd.isEmpty()) return
        input = ""
        flushPending()
        buffer.update { it + prompt + cmd + "\n" }
        val proc = process.value ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    proc.outputStream.write((cmd + "\n").toByteArray(Charsets.UTF_8))
                    proc.outputStream.flush()
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .add(WindowInsets.ime)
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.terminal_title),
                largeTitle = stringResource(R.string.terminal_title),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Output box: the log subscription lives inside it; keeping it in the screen body
            // re-ran the whole page on every 80ms frame.
            TerminalOutputBox(
                buffer = buffer,
                scrollState = scrollState,
                nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                TextField(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 100.dp)
                        .border(
                            width = 1.dp,
                            color = MiuixTheme.colorScheme.outline.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                        ),
                    value = input,
                    onValueChange = { input = it },
                    maxLines = 4,
                    trailingIcon = {
                        IconButton(onClick = { sendCommand() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = null,
                                modifier = Modifier.width(20.dp),
                                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TerminalOutputBox(
    buffer: MutableStateFlow<String>,
    scrollState: ScrollState,
    nestedScrollConnection: NestedScrollConnection,
    modifier: Modifier = Modifier,
) {
    val outputText by buffer.collectAsState()
    AutoScrollToBottom(scrollState, outputText.length)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .nestedScroll(nestedScrollConnection)
            .background(Color(0xFF000000), RoundedCornerShape(12.dp))
            .verticalScroll(scrollState)
            .padding(12.dp),
    ) {
        Text(
            text = outputText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = Color(0xFFE5E5E5),
        )
    }
}
