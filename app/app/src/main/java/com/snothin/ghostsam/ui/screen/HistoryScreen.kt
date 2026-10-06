package com.snothin.ghostsam.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snothin.ghostsam.R
import com.snothin.ghostsam.data.exploit.DeviceLogRecovery
import com.snothin.ghostsam.data.exploit.ExploitHistoryEntry
import com.snothin.ghostsam.data.exploit.ExploitHistoryStore
import com.snothin.ghostsam.data.exploit.ExploitRunResult
import com.snothin.ghostsam.ui.component.LogText
import com.snothin.ghostsam.ui.component.rememberSelection
import com.snothin.ghostsam.ui.theme.StatusColors
import com.snothin.ghostsam.ui.theme.SuccessAccent
import com.snothin.ghostsam.ui.theme.errorAccent
import com.snothin.ghostsam.ui.theme.isInDarkTheme
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import androidx.compose.foundation.background
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val RUNNING_REFRESH_MS = 3_000L

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    onDetailModeChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<ExploitHistoryEntry>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var liveTick by remember { mutableStateOf(0) }
    val selection = rememberSelection()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(isVisible) {
        if (!isVisible) return@LaunchedEffect
        val store = ExploitHistoryStore(context)
        entries = withContext(Dispatchers.IO) {
            // Device run.log tail salvage (visible right after a reboot): skipped while a run is active;
            // salvage first, then mark interrupted runs.
            if (ExploitHistoryStore.currentActiveRunId == null) {
                DeviceLogRecovery.recover(store, context) { DeviceLogRecovery.readRunLogText(context) }
            }
            store.closeInterruptedRuns()
        }
        while (true) {
            val running = entries.filter { it.result == ExploitRunResult.Running }
            if (running.isEmpty()) break
            delay(RUNNING_REFRESH_MS)
            val refreshed = withContext(Dispatchers.IO) {
                running.mapNotNull { store.loadEntry(it.id) }
            }
            if (refreshed.isEmpty()) break
            val byId = refreshed.associateBy { it.id }
            entries = entries.map { entry ->
                byId[entry.id]?.let { full ->
                    if (entry.id == selectedId) full else full.copy(log = "")
                } ?: entry
            }
            liveTick++
        }
    }
    val selected = entries.firstOrNull { it.id == selectedId }

    fun deleteSelected() {
        val ids = selection.selectedIds
        if (ids.isEmpty()) return
        val store = ExploitHistoryStore(context)
        scope.launch(Dispatchers.IO) {
            ids.forEach { store.delete(it) }
            withContext(Dispatchers.Main) {
                entries = entries.filterNot { it.id in ids }
                selection.exit()
            }
        }
    }

    BackHandler(enabled = selection.selectionMode) { selection.exit() }
    BackHandler(enabled = selected != null) { selectedId = null }
    LaunchedEffect(selected != null) { onDetailModeChanged(selected != null) }
    DisposableEffect(Unit) {
        onDispose { onDetailModeChanged(false) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.data?.let { uri ->
            entries.firstOrNull { it.id == selectedId }?.let { entry ->
                scope.launch(Dispatchers.IO) { saveRunLog(context, uri, entry) }
            }
        }
    }
    val exportZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.data?.let { uri ->
            val toExport = entries.filter { it.id in selection.selectedIds }
            if (toExport.isNotEmpty()) {
                scope.launch(Dispatchers.IO) { exportSelectedLogs(context, uri, toExport) }
            }
            selection.exit()
        }
    }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = stringResource(
                    when {
                        selection.selectionMode -> R.string.history_selected_format
                        selected != null -> R.string.history_detail_title
                        else -> R.string.nav_history
                    },
                    selection.selectedIds.size,
                ),
                largeTitle = stringResource(
                    when {
                        selection.selectionMode -> R.string.history_selected_format
                        selected != null -> R.string.history_detail_title
                        else -> R.string.nav_history
                    },
                    selection.selectedIds.size,
                ),
                navigationIcon = {
                    if (selection.selectionMode || selected != null) {
                        IconButton(onClick = {
                            if (selection.selectionMode) selection.exit() else selectedId = null
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                },
                actions = {
                    if (selection.selectionMode) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.history_delete),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                        IconButton(onClick = {
                            exportZipLauncher.launch(
                                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_TITLE, zipFileName())
                                },
                            )
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Archive,
                                contentDescription = stringResource(R.string.history_export_zip),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    } else if (selected != null) {
                        IconButton(onClick = {
                            exportLauncher.launch(
                                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = logExportMimeType
                                    putExtra(Intent.EXTRA_TITLE, runLogFileName(selected))
                                },
                            )
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = stringResource(R.string.history_export),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = selected,
            contentKey = { it?.id ?: "history-list" },
            label = "history-detail",
        ) { entry ->
            if (entry == null) {
                HistoryListContent(
                    entries = entries,
                    padding = padding,
                    nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                    selectionMode = selection.selectionMode,
                    selectedIds = selection.selectedIds,
                    onEntryClick = {
                        if (selection.selectionMode) {
                            selection.toggle(it.id)
                        } else {
                            selectedId = it.id
                        }
                    },
                    onEntryLongClick = { selection.enter(it.id) },
                )
            } else {
                HistoryDetailContent(
                    entry = entry,
                    liveTick = if (entry.result == ExploitRunResult.Running) liveTick else 0,
                    padding = padding,
                    nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.history_delete_confirm_title, selection.selectedIds.size),
            summary = stringResource(R.string.history_delete_confirm_body),
            onDismissRequest = { showDeleteConfirm = false },
            content = {
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = { showDeleteConfirm = false },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(R.string.history_delete),
                        onClick = {
                            showDeleteConfirm = false
                            deleteSelected()
                        },
                        modifier = Modifier.weight(1f),
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            },
        )
    }
}

@Composable
private fun HistoryListContent(
    entries: List<ExploitHistoryEntry>,
    padding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onEntryClick: (ExploitHistoryEntry) -> Unit,
    onEntryLongClick: (ExploitHistoryEntry) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(nestedScrollConnection)
            .padding(horizontal = 12.dp),
        contentPadding = padding + PaddingValues(top = 12.dp),
        overscrollEffect = null,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (entries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.history_placeholder),
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }
        } else {
            items(entries, key = { it.id }) { entry ->
                HistoryCard(
                    entry = entry,
                    selectionMode = selectionMode,
                    selected = entry.id in selectedIds,
                    onClick = { onEntryClick(entry) },
                    onLongClick = { onEntryLongClick(entry) },
                )
            }
        }
    }
}

@Composable
private fun HistoryDetailContent(
    entry: ExploitHistoryEntry,
    liveTick: Int,
    padding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
) {
    val appContext = LocalContext.current.applicationContext
    var logText by remember(entry.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(entry.id, liveTick) {
        logText = withContext(Dispatchers.IO) {
            ExploitHistoryStore(appContext).loadEntry(entry.id)?.log ?: entry.log
        }
    }
    val logScrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HistoryResultCard(entry)
        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
            SelectionContainer {
                LogText(
                    text = logText?.ifBlank { stringResource(R.string.exploit_log_empty) } ?: "",
                    modifier = Modifier
                        .fillMaxSize()
                        // nestedScroll must wrap verticalScroll (outside): only then does the inner scrollable
                        // report to the outer connection (collapsing the large title).
                        .nestedScroll(nestedScrollConnection)
                        .verticalScroll(logScrollState)
                        .padding(14.dp),
                )
            }
        }
    }
}

@Composable
private fun HistoryResultCard(entry: ExploitHistoryEntry) {
    val isDark = isInDarkTheme()
    val colors = when (entry.result) {
        ExploitRunResult.Succeeded -> StatusColors.success(isDark)
        ExploitRunResult.Failed -> StatusColors(
            MiuixTheme.colorScheme.errorContainer,
            MiuixTheme.colorScheme.onErrorContainer,
        )
        ExploitRunResult.Running -> StatusColors(
            MiuixTheme.colorScheme.primaryContainer,
            MiuixTheme.colorScheme.onPrimaryContainer,
        )
    }
    val icon = resultIcon(entry.result)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.container)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = colors.content,
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = resultLabel(entry.result),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.content,
                )
                Text(
                    text = historyDetail(entry),
                    fontSize = 12.sp,
                    color = colors.content.copy(alpha = 0.7f),
                )
                Text(
                    text = stringResource(R.string.history_started, formatHistoryTime(entry.startedAtMillis)),
                    fontSize = 11.sp,
                    color = colors.content.copy(alpha = 0.7f),
                )
                entry.completedAtMillis?.let { completed ->
                    Text(
                        text = stringResource(R.string.history_completed, formatHistoryTime(completed)),
                        fontSize = 11.sp,
                        color = colors.content.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    entry: ExploitHistoryEntry,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isDark = isInDarkTheme()
    val resultColor = when (entry.result) {
        ExploitRunResult.Succeeded -> SuccessAccent
        ExploitRunResult.Failed -> errorAccent(isDark)
        ExploitRunResult.Running -> MiuixTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Icon(
                    imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.outline,
                )
                Spacer(Modifier.width(10.dp))
            }
            if (!selectionMode) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = resultIcon(entry.result),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = resultColor,
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    text = resultLabel(entry.result),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = historyDetail(entry),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

private fun resultIcon(result: ExploitRunResult): ImageVector = when (result) {
    ExploitRunResult.Succeeded -> Icons.Rounded.CheckCircle
    ExploitRunResult.Failed -> Icons.Rounded.Error
    ExploitRunResult.Running -> Icons.Rounded.Sync
}

@Composable
private fun resultLabel(result: ExploitRunResult): String = stringResource(
    when (result) {
        ExploitRunResult.Succeeded -> R.string.history_succeeded
        ExploitRunResult.Failed -> R.string.history_failed
        ExploitRunResult.Running -> R.string.history_running
    },
)

private fun historyDetail(entry: ExploitHistoryEntry): String = buildString {
    append(formatHistoryTime(entry.startedAtMillis))
    entry.completedAtMillis?.let { completed ->
        val seconds = ((completed - entry.startedAtMillis) / 1000.0).coerceAtLeast(0.0)
        append(" · %.1fs".format(seconds))
    }
    entry.exitCode?.let { append(" · exit $it") }
}

private val historyTimeFormat: DateFormat by lazy {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault())
}

private fun formatHistoryTime(millis: Long): String =
    historyTimeFormat.format(Date(millis))

private fun exportStamp(prefix: String, millis: Long): String =
    "$prefix-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(millis))

private fun runLogFileName(entry: ExploitHistoryEntry): String =
    exportStamp("ghostsam", entry.startedAtMillis) + ".log"

// Export MIME: DocumentsUI appends the MIME's default extension on OneUI, turning
// "<name>.log" into "<name>.log.txt"; pick a type without one, else fall back to text/plain.
private val logExportMimeType: String by lazy {
    listOf("text/x-log", "application/x-log").firstOrNull {
        MimeTypeMap.getSingleton().getExtensionFromMimeType(it).let { ext -> ext == null || ext == "log" }
    } ?: "text/plain"
}

private fun saveRunLog(context: Context, uri: Uri, entry: ExploitHistoryEntry) {
    runCatching {
        val log = ExploitHistoryStore(context).loadEntry(entry.id)?.log ?: entry.log
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(log.toByteArray(Charsets.UTF_8))
        }
    }
}

private fun exportSelectedLogs(context: Context, uri: Uri, entries: List<ExploitHistoryEntry>) {
    runCatching {
        val store = ExploitHistoryStore(context)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                entries.forEach { entry ->
                    val full = store.loadEntry(entry.id) ?: entry
                    zip.putNextEntry(ZipEntry(runLogFileName(full)))
                    zip.write(full.log.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }
    }
}

private fun zipFileName(): String =
    exportStamp("ghostsam-logs", System.currentTimeMillis()) + ".zip"
