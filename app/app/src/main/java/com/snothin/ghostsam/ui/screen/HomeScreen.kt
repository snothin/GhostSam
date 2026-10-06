package com.snothin.ghostsam.ui.screen

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.VerifiedUser
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.snothin.ghostsam.R
import com.snothin.ghostsam.data.channel.AdbController
import com.snothin.ghostsam.data.channel.AdbPairService
import com.snothin.ghostsam.data.device.DeviceMatcher
import com.snothin.ghostsam.data.device.DeviceSnapshot
import com.snothin.ghostsam.data.channel.ExecutionMode
import com.snothin.ghostsam.data.exploit.ExploitPhase
import com.snothin.ghostsam.data.exploit.ExploitViewModel
import com.snothin.ghostsam.data.exploit.PayloadScheme
import com.snothin.ghostsam.data.exploit.isRunning
import com.snothin.ghostsam.data.device.KernelSuProbe
import com.snothin.ghostsam.data.prefs.KsudVariant
import com.snothin.ghostsam.data.prefs.PayloadProfile
import com.snothin.ghostsam.data.prefs.PayloadProfileKind
import com.snothin.ghostsam.data.prefs.PayloadProfileStore
import com.snothin.ghostsam.data.prefs.PersistPrefs
import com.snothin.ghostsam.data.prefs.SettingsPrefs
import com.snothin.ghostsam.system.CompanionClient
import com.snothin.ghostsam.data.channel.ShizukuController
import com.snothin.ghostsam.ui.component.rememberShizukuConnection
import com.snothin.ghostsam.ui.navigation3.LocalNavigator
import com.snothin.ghostsam.ui.navigation3.Route
import com.snothin.ghostsam.ui.theme.NatsumeGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
) {
    HomeMiuix(modifier)
}

@Composable
private fun HomeMiuix(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scrollBehavior = MiuixScrollBehavior()

    val device = remember { DeviceSnapshot.current() }
    var baseband by remember { mutableStateOf("") }
    var oneUiVersion by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            baseband = DeviceSnapshot.readBaseband()
            oneUiVersion = DeviceSnapshot.readOneUiVersion()
        }
    }
    val seriesMatch = remember { DeviceMatcher.match(device) }

    val shizukuConnected = rememberShizukuConnection()
    val scope = rememberCoroutineScope()
    var executionMode by remember { mutableStateOf(SettingsPrefs.executionMode(context)) }
    LaunchedEffect(Unit) {
        SettingsPrefs.executionModeFlow.drop(1).collect { executionMode = it }
    }
    val useShizuku = executionMode == ExecutionMode.Shizuku
    var tcpPersist by remember { mutableStateOf(SettingsPrefs.tcpPersist(context)) }
    LaunchedEffect(Unit) {
        SettingsPrefs.tcpPersistFlow.drop(1).collect { tcpPersist = it }
    }
    val tcpArmedPort by AdbController.armedPortFlow.collectAsState()
    val tcpArmed = tcpArmedPort > 0
    // Adb connection state: subscribe connectedFlow (real-time); the old broadcast + ON_RESUME
    // sampling read a fake disconnect during the TCP loopback switch.
    val adbConnected by AdbController.connectedFlow.collectAsState()
    val channelConnected = when (executionMode) {
        ExecutionMode.Shizuku -> shizukuConnected
        ExecutionMode.Adb -> adbConnected
    }
    val evidence = rememberHomeEvidence(channelConnected)
    val ksuActive = evidence.ksuActive
    val moduleActive = evidence.moduleActive
    val noksuSession = evidence.noksuSession
    val suAvailable = evidence.suAvailable
    val suProbeDone = evidence.suProbeDone
    val suDenied = evidence.suDenied
    val managerInstalled = evidence.managerInstalled
    val ksudUpgraded = evidence.ksudUpgraded
    val rootInfo = evidence.rootInfo
    val exploitViewModel: ExploitViewModel = viewModel()
    val exploitPhaseFlow = remember(exploitViewModel) {
        exploitViewModel.uiState.map { it.phase }.distinctUntilChanged()
    }
    val exploitPhase by exploitPhaseFlow.collectAsState(exploitViewModel.uiState.value.phase)
    val exploiting = exploitPhase.isRunning
    val persistRunFlow = remember(exploitViewModel) {
        exploitViewModel.uiState.map { it.persistRun }.distinctUntilChanged()
    }
    val persistRun by persistRunFlow.collectAsState(exploitViewModel.uiState.value.persistRun)

    var payloadEnabled by remember { mutableStateOf(SettingsPrefs.localPayload(context)) }
    LaunchedEffect(Unit) {
        SettingsPrefs.localPayloadFlow.drop(1).collect { payloadEnabled = it }
    }
    var payloadScheme by remember { mutableStateOf(SettingsPrefs.payloadScheme(context)) }
    LaunchedEffect(Unit) {
        SettingsPrefs.payloadSchemeFlow.drop(1).collect { payloadScheme = it }
    }
    var ksudVariant by remember { mutableStateOf(SettingsPrefs.ksudVariant(context)) }
    LaunchedEffect(Unit) {
        SettingsPrefs.ksudVariantFlow.drop(1).collect { ksudVariant = it }
    }
    val nextVariant = remember(payloadScheme, ksudVariant) {
        SettingsPrefs.effectiveKsudVariant(context) == KsudVariant.Next
    }
    val payloadRouteKind =
        if (payloadScheme == PayloadScheme.DfrStandalone) {
            PayloadProfileKind.Standalone
        } else {
            PayloadProfileKind.Standard
        }
    var profiles by remember { mutableStateOf<List<PayloadProfile>>(emptyList()) }
    var showPayloadPicker by remember { mutableStateOf(false) }
    val ksudUpgrade = rememberKsudUpgradeState(evidence)
    var companionState by remember { mutableStateOf(CompanionClient.state(context)) }
    var persistHost by remember { mutableStateOf(PersistPrefs.host(context)) }
    LaunchedEffect(exploitPhase) {
        if (exploitPhase == ExploitPhase.Succeeded || exploitPhase == ExploitPhase.Failed) {
            persistHost = PersistPrefs.host(context)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                companionState = CompanionClient.state(context)
                persistHost = PersistPrefs.host(context)
                scope.launch {
                    profiles = withContext(Dispatchers.IO) { PayloadProfileStore(context).load() }
                    evidence.refreshManager()
                    evidence.refreshSuOnResume()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        try {
            awaitCancellation()
        } finally {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val onShizukuClick = {
        if (!shizukuConnected) {
            scope.launch {
                ShizukuController.pingUntilRunning()
                ShizukuController.requestPermission()
            }
        }
    }
    val navigator = LocalNavigator.current

    val tempChannelActive = ksuActive && noksuSession && !moduleActive && !ksudUpgraded

    val selinux = rememberSelinuxSectionState(shizukuConnected, suAvailable, adbConnected)

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    // Persist (companion, DFR form): ready = DFR scheme + companion installed; tapping before
    // activation drives the chain (no wireless debugging); active keeps "open Manager".
    val persistReady = payloadScheme == PayloadScheme.Dfr && companionState.installed
    val standaloneReady = payloadScheme == PayloadScheme.DfrStandalone && !ksuActive

    val persistHostLine = if (persistReady) {
        persistHost?.let {
            resources.getString(R.string.persist_host_line, it.proc, it.pid, it.starts)
        } ?: resources.getString(R.string.persist_host_pending)
    } else {
        null
    }

    val pairPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            AdbPairService.start(context)
        } else {
            toast(resources.getString(R.string.adb_pair_notif_denied))
            val activity = context as? Activity
            if (activity != null && !activity.shouldShowRequestPermissionRationale(
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            ) {
                runCatching {
                    activity.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName),
                    )
                }
            }
        }
    }

    fun startAdbPair() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pairPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            AdbPairService.start(context)
        }
    }

    fun connectChannel() {
        if (useShizuku) {
            onShizukuClick()
            return
        }
        scope.launch {
            val silent = AdbController.reconnectLast()
            Log.i(
                "GhostSam",
                "connectChannel: silentReconnect=$silent connected=${AdbController.isConnected()}",
            )
            if (!silent && !AdbController.isConnected()) {
                startAdbPair()
            }
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    AdbPairService.ACTION_ADB_CONNECTED -> {
                        val silent = intent.getBooleanExtra(AdbPairService.EXTRA_SILENT, false)
                        if (!silent && !evidence.ksuActive) toast(context.getString(R.string.adb_pair_success))
                    }
                    AdbPairService.ACTION_ADB_FAILED -> {
                        toast(intent.getStringExtra(AdbPairService.EXTRA_ERROR).orEmpty())
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(AdbPairService.ACTION_ADB_CONNECTED)
                addAction(AdbPairService.ACTION_ADB_FAILED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    fun onStatusCardClick() {
    if (persistReady && !ksuActive) {
        navigator.push(Route.Exploit)
        exploitViewModel.startPersist()
    } else if (standaloneReady && !managerInstalled) {
        // Standalone fail-closed precheck: without the KernelSU Manager, only hint installation.
    } else if (standaloneReady) {
        if (payloadEnabled) {
            showPayloadPicker = true
        } else {
            navigator.push(Route.Exploit)
            exploitViewModel.start()
        }
    } else when {
        tempChannelActive -> {
            // no-ksu: root goes through the bridged channel (SELinux denies the app a direct
            // temp_su.sock connection); tapping while disconnected reconnects (adb / Shizuku).
            if (useShizuku) {
                if (!shizukuConnected) onShizukuClick()
            } else if (!adbConnected) {
                connectChannel()
            }
        }
        ksuActive -> openKernelSUManager(context)
        // Only "running" re-enters the exploit page; a stale Failed phase must not hijack the tap.
        exploiting ->
            navigator.push(Route.Exploit)
        channelConnected && !managerInstalled -> {
        }
        channelConnected && suDenied -> {
            openKernelSUManager(context)
        }
        channelConnected -> {
            if (payloadEnabled) {
                showPayloadPicker = true
            } else {
                navigator.push(Route.Exploit)
                exploitViewModel.start()
            }
        }
        else -> {
            if (!persistReady) connectChannel()
        }
    }
    }

    top.yukonga.miuix.kmp.basic.Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        topBar = {
            top.yukonga.miuix.kmp.basic.TopAppBar(
                title = stringResource(R.string.app_name),
                largeTitle = stringResource(R.string.app_name),
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
            val status = when {
                ksuActive -> SummonStatus.Ready
                exploiting -> SummonStatus.Working
                persistReady -> SummonStatus.Persisted
                standaloneReady && !managerInstalled -> SummonStatus.ManagerMissing
                standaloneReady -> SummonStatus.Standalone
                channelConnected && !noksuSession && !managerInstalled -> SummonStatus.ManagerMissing
                channelConnected && !noksuSession && suDenied -> SummonStatus.SuDenied
                channelConnected -> SummonStatus.Ready
                else -> SummonStatus.Disconnected
            }
            item {
                SummonStatusCard(
                    status = status,
                    ksuActive = ksuActive,
                    noKsu = tempChannelActive,
                    rootInfo = rootInfo,
                    useShizuku = useShizuku,
                    channelConnected = channelConnected,
                    disconnectedDetailRes = when (executionMode) {
                        ExecutionMode.Shizuku -> R.string.home_status_disconnected_detail_shizuku
                        ExecutionMode.Adb -> R.string.home_status_disconnected_detail_adb
                    },
                    disconnectedDetailOverride = if (executionMode == ExecutionMode.Adb && tcpPersist) {
                        context.getString(
                            if (tcpArmed) R.string.home_no_wifi_ready_detail
                            else R.string.home_no_wifi_waiting_detail,
                        )
                    } else {
                        null
                    },
                    workingDetailOverride = if (persistRun && exploiting) {
                        context.getString(R.string.home_status_working_detail_persist)
                    } else {
                        null
                    },
                    persistHostLine = persistHostLine,
                    standaloneRoute = payloadScheme == PayloadScheme.DfrStandalone,
                    nextVariant = nextVariant,
                    onClick = { onStatusCardClick() },
                )
            }

            if (tempChannelActive) {
                item {
                    KsudUpgradeCard(onClick = { ksudUpgrade.open() })
                }
            }

            val standaloneRoute = payloadScheme == PayloadScheme.DfrStandalone
            val suDeniedConnectVisible =
                ksuActive && !tempChannelActive && !suAvailable && !channelConnected &&
                    suProbeDone
            if (suDeniedConnectVisible) {
                item {
                    SuDeniedConnectCard(
                        onClick = { if (!standaloneRoute) connectChannel() },
                        showAction = !standaloneRoute,
                        hintRes = if (standaloneRoute) {
                            R.string.su_denied_hint_standalone
                        } else {
                            R.string.su_denied_connect_hint
                        },
                    )
                }
            }

            item {
                DeviceInfoCard(
                device = device,
                baseband = baseband,
                oneUi = oneUiVersion,
                seriesMatch = seriesMatch,
            )
            }

            item {
                StatusSummaryCard(
                    selinuxState = selinux.selinuxState,
                    onClick = { selinux.onSummaryClick(noksuSession) },
                )
            }

            if (payloadEnabled) {
                item {
                    PayloadConfigCard(
                        profileCount = profiles.size,
                        successCount = profiles.count { it.isProven },
                        onClick = { navigator.push(Route.PayloadProfiles) },
                    )
                }
            }

            item {
                top.yukonga.miuix.kmp.basic.Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { navigator.push(Route.SystemTools) },
                    showIndication = true,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = NatsumeGray,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.tools_card_title),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = stringResource(R.string.tools_card_summary),
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                }
            }
        }
    }

    KsudUpgradeDialog(ksudUpgrade, executionMode)

    if (showPayloadPicker) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.payload_pick_title),
            onDismissRequest = { showPayloadPicker = false },
            content = {
                Column {
                    PayloadPickRow(
                        icon = Icons.Rounded.VerifiedUser,
                        title = stringResource(R.string.payload_builtin),
                        description = stringResource(
                            if (payloadRouteKind == PayloadProfileKind.Standalone) {
                                R.string.payload_builtin_desc_standalone
                            } else {
                                R.string.payload_builtin_desc
                            },
                        ),
                        onClick = {
                            showPayloadPicker = false
                            navigator.push(Route.Exploit)
                            exploitViewModel.start()
                        },
                    )
                    profiles.filter { it.kind == payloadRouteKind }.forEach { profile ->
                        PayloadPickRow(
                            icon = if (profile.isProven) Icons.Rounded.CheckCircleOutline else Icons.Rounded.Code,
                            title = profile.name,
                            description = stringResource(
                                R.string.payload_runs_format,
                                profile.runCount,
                                profile.successCount,
                                profile.failCount,
                            ),
                            onClick = {
                                showPayloadPicker = false
                                navigator.push(Route.Exploit)
                                exploitViewModel.start(profile.id)
                            },
                        )
                    }
                }
            },
        )
    }

    SelinuxConfirmDialog(selinux, noksuSession)
}

private fun openKernelSUManager(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(KernelSuProbe.preferredManagerPackage(context))
        ?: return
    runCatching { context.startActivity(intent) }
        .onFailure { Log.w("GhostSam", "open KernelSU Manager failed", it) }
}

@Composable
private fun PayloadPickRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}
