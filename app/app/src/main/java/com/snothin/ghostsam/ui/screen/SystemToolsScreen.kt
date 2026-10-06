package com.snothin.ghostsam.ui.screen

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snothin.ghostsam.R
import com.snothin.ghostsam.data.channel.AdbController
import com.snothin.ghostsam.data.device.KernelSuProbe
import com.snothin.ghostsam.data.channel.ShizukuController
import com.snothin.ghostsam.data.channel.SuController
import com.snothin.ghostsam.data.channel.SuDaemonChannel
import com.snothin.ghostsam.data.SystemTool
import com.snothin.ghostsam.data.SystemToolKind
import com.snothin.ghostsam.data.SystemTools
import com.snothin.ghostsam.data.channel.NO_OUTPUT_MARKER
import com.snothin.ghostsam.data.channel.isChannelFailure
import com.snothin.ghostsam.ui.component.rememberShizukuConnection
import com.snothin.ghostsam.ui.navigation3.LocalNavigator
import com.snothin.ghostsam.ui.navigation3.Route
import com.snothin.ghostsam.ui.theme.NatsumeGray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val SNACKBAR_MILLIS = 3_000L

private const val ADB_POLL_MILLIS = 3_000L

@Composable
fun SystemToolsScreen(
    onBack: () -> Unit,
    tools: List<SystemTool> = SystemTools,
    titleRes: Int = R.string.tools_title,
    showMoreEntry: Boolean = true,
) {
    val context = LocalContext.current.applicationContext
    val scrollBehavior = MiuixScrollBehavior()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }

    val shizukuConnected = rememberShizukuConnection()
    var adbConnected by remember { mutableStateOf(AdbController.isConnected()) }
    var suAvailable by remember { mutableStateOf(false) }
    var suDaemonOk by remember { mutableStateOf(false) }
    val channelOk = suAvailable || suDaemonOk || adbConnected || shizukuConnected
    LaunchedEffect(Unit) {
        suAvailable = withContext(Dispatchers.IO) { SuController.isAvailable() }
        suDaemonOk = withContext(Dispatchers.IO) { SuDaemonChannel.isAvailable() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(ADB_POLL_MILLIS)
            adbConnected = AdbController.isConnected()
        }
    }

    var availability by remember { mutableStateOf<Map<Int, Boolean>?>(null) }
    LaunchedEffect(channelOk) {
        if (!channelOk) {
            availability = null
            return@LaunchedEffect
        }
        val channel = if (suAvailable || suDaemonOk) "su" else "sh"
        // Concurrency capped: 16 parallel privileged shells used to squeeze the device's adb channel.
        val gate = Semaphore(4)
        availability = try {
            coroutineScope {
                tools.map { tool ->
                    async(Dispatchers.IO) {
                        gate.withPermit {
                            val key = "$channel:${tool.titleRes}"
                            val cached = ToolAvailabilityCache.get(key)
                            if (cached != null) {
                                tool.titleRes to cached
                            } else {
                                val check = tool.checkCommand
                                if (check == null) {
                                    tool.titleRes to true
                                } else {
                                    // "Error: ..." is device output; channel failure/timeout/empty sentinels count as
                                    // unavailable; flaky results are not cached (a hiccup must not dim a tool).
                                    val out = ShizukuController.execPrivileged(check, timeoutMillis = 5_000)
                                    if (isChannelFailure(out)) {
                                        tool.titleRes to false
                                    } else {
                                        val available = out.isNotBlank() &&
                                            !out.startsWith(NO_OUTPUT_MARKER) && !out.startsWith("Error")
                                        ToolAvailabilityCache.put(key, available)
                                        tool.titleRes to available
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll().toMap()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
    }

    var lastActionAt by remember { mutableLongStateOf(0L) }
    fun debounced(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastActionAt < SNACKBAR_MILLIS) return false
        lastActionAt = now
        return true
    }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun snackbar(text: String) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = text,
                duration = SnackbarDuration.Custom(SNACKBAR_MILLIS),
            )
        }
    }

    suspend fun gateNoChannel(): Boolean {
        val denied = withContext(Dispatchers.IO) { KernelSuProbe.isRootedWithSuDenied(context) }
        if (denied) {
            toast(context.getString(R.string.tools_su_denied))
            return false
        }
        val granted = ShizukuController.requestPermission()
        if (!granted) {
            toast(context.getString(R.string.tools_no_permission))
        }
        return granted
    }

    fun runTool(tool: SystemTool) {
        scope.launch {
            if (tool.titleRes == R.string.tools_shell_identity && !debounced()) return@launch
            if (!channelOk) {
                if (!gateNoChannel()) return@launch
            }
            val result = ShizukuController.execPrivileged(tool.command)
            if (tool.titleRes == R.string.tools_shell_identity) {
                snackbar(result)
            } else if (isChannelFailure(result) || result.startsWith("Error")) {
                toast(result)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        snackbarHost = {
            SnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.padding(bottom = 40.dp),
                content = { data ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xE6303030))
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Text(
                            text = data.visuals.message,
                            color = Color.White,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 20,
                        )
                    }
                },
            )
        },
        topBar = {
            TopAppBar(
                title = stringResource(titleRes),
                largeTitle = stringResource(titleRes),
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
            tools.forEach { tool ->
                item(key = tool.titleRes) {
                    val available = availability?.get(tool.titleRes) ?: true
                    val dimmed = !channelOk || !available
                    val iconTint = if (dimmed) {
                        NatsumeGray.copy(alpha = 0.4f)
                    } else {
                        NatsumeGray
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = stringResource(tool.titleRes),
                            summary = stringResource(tool.summaryRes) +
                                if (!available) stringResource(R.string.tools_unavailable_suffix) else "",
                            startAction = {
                                Icon(
                                    toolIcons[tool.titleRes] ?: Icons.Rounded.MoreHoriz,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(tool.titleRes),
                                    tint = iconTint,
                                )
                            },
                            onClick = {
                                if (!channelOk) {
                                    scope.launch {
                                        if (!debounced()) return@launch
                                        gateNoChannel()
                                    }
                                    return@ArrowPreference
                                }
                                if (!available) {
                                    if (debounced()) {
                                        toast(context.getString(R.string.tools_component_missing))
                                    }
                                    return@ArrowPreference
                                }
                                when (tool.kind) {
                                    SystemToolKind.TERMINAL -> navigator.push(Route.SystemTerminal)
                                    SystemToolKind.COMMAND -> runTool(tool)
                                }
                            },
                        )
                    }
                }
            }
            if (showMoreEntry) {
                item(key = "more_entry") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = stringResource(R.string.tools_more_title),
                            summary = stringResource(R.string.tools_more_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.MoreHoriz,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.tools_more_title),
                                    tint = NatsumeGray,
                                )
                            },
                            onClick = { navigator.push(Route.SystemToolsMore) },
                        )
                    }
                }
            }
        }
    }
}

private val toolIcons: Map<Int, ImageVector> = mapOf(
    R.string.tools_terminal to Icons.Rounded.Terminal,
    R.string.tools_shell_identity to Icons.Rounded.Fingerprint,
    R.string.tools_band_selection to Icons.Rounded.SettingsInputAntenna,
    R.string.tools_service_mode to Icons.Rounded.Radio,
    R.string.tools_band_priority to Icons.AutoMirrored.Rounded.Sort,
    R.string.tools_preconfig to Icons.Rounded.Tune,
    R.string.tools_global_hidden to Icons.Rounded.Visibility,
    R.string.tools_field_test to Icons.Rounded.Science,
    R.string.tools_debug_menu to Icons.Rounded.BugReport,
    R.string.tools_usb_settings to Icons.Rounded.Usb,
    R.string.tools_ims_settings to Icons.Rounded.Call,
    R.string.tools_dm_mode to Icons.Rounded.AdminPanelSettings,
    R.string.tools_regional_mode to Icons.Rounded.Public,
    R.string.tools_parser_secret_code to Icons.Rounded.Dialpad,
    R.string.tools_sideload to Icons.Rounded.SystemUpdate,
)

private object ToolAvailabilityCache {
    private const val TTL_MILLIS = 60_000L

    private data class Entry(val available: Boolean, val atMillis: Long)

    private val cache = HashMap<String, Entry>()

    @Synchronized
    fun get(key: String): Boolean? {
        val entry = cache[key] ?: return null
        if (SystemClock.elapsedRealtime() - entry.atMillis > TTL_MILLIS) {
            cache.remove(key)
            return null
        }
        return entry.available
    }

    @Synchronized
    fun put(key: String, available: Boolean) {
        cache[key] = Entry(available, SystemClock.elapsedRealtime())
    }
}
