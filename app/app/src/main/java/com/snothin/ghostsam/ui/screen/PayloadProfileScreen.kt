package com.snothin.ghostsam.ui.screen

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snothin.ghostsam.R
import com.snothin.ghostsam.data.device.DeviceSnapshot
import com.snothin.ghostsam.data.exploit.DfrPack
import com.snothin.ghostsam.data.exploit.ExploitPack
import com.snothin.ghostsam.data.exploit.PayloadScheme
import com.snothin.ghostsam.data.prefs.PayloadProfile
import com.snothin.ghostsam.data.prefs.PayloadProfileKind
import com.snothin.ghostsam.data.prefs.PayloadProfileStore
import com.snothin.ghostsam.data.prefs.SettingsPrefs
import com.snothin.ghostsam.data.prefs.newPayloadProfile
import com.snothin.ghostsam.ui.component.SelectionState
import com.snothin.ghostsam.ui.component.rememberSelection
import com.snothin.ghostsam.ui.theme.StatusColors
import com.snothin.ghostsam.ui.theme.isInDarkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

private class PayloadProfilesState(
    private val context: Context,
    private val resources: Resources,
    private val scope: CoroutineScope,
    val selection: SelectionState,
) {
    var profiles by mutableStateOf<List<PayloadProfile>>(emptyList())

    var showDeleteConfirm by mutableStateOf(false)

    var editTarget by mutableStateOf<PayloadProfile?>(null)
    var showEditDialog by mutableStateOf(false)

    val currentKind: PayloadProfileKind =
        if (SettingsPrefs.payloadScheme(context) == PayloadScheme.DfrStandalone) {
            PayloadProfileKind.Standalone
        } else {
            PayloadProfileKind.Standard
        }

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun deleteSelected() {
        val ids = selection.selectedIds
        if (ids.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val remaining = PayloadProfileStore(context).update { list ->
                list.filterNot { it.id in ids }.takeIf { it.size != list.size } ?: list
            }
            withContext(Dispatchers.Main) {
                profiles = remaining
                selection.exit()
            }
        }
    }

    fun saveProfile(name: String, preload: String, ksud: String, su: String, ko: String) {
        val kind = editTarget?.kind ?: currentKind
        when (kind) {
            PayloadProfileKind.Standard -> if (name.isBlank() || preload.isBlank() || ksud.isBlank() || su.isBlank()) {
                toast(resources.getString(R.string.payload_save_failed, resources.getString(R.string.payload_fields_missing)))
                return
            }
            PayloadProfileKind.Standalone -> if (name.isBlank() || (ksud.isBlank() && ko.isBlank())) {
                toast(
                    resources.getString(
                        R.string.payload_save_failed,
                        resources.getString(R.string.payload_fields_missing_standalone),
                    ),
                )
                return
            }
        }
        scope.launch {
            val (hashes, errorRes) = withContext(Dispatchers.IO) {
                when (kind) {
                    PayloadProfileKind.Standard -> {
                        val h = buildHashes(context, preload, ksud, su)
                        if (h == null) null to R.string.payload_verify_failed else h to null
                    }
                    PayloadProfileKind.Standalone -> checkStandaloneFiles(context, ksud, ko)
                }
            }
            if (hashes == null) {
                toast(
                    resources.getString(
                        R.string.payload_save_failed,
                        resources.getString(errorRes ?: R.string.payload_verify_failed),
                    ),
                )
                return@launch
            }
            // Disk write (AtomicFile + fd.sync) on IO; update is a mutex-guarded read-modify-write
            // (no lost updates against the wrap-up stats).
            val saved = withContext(Dispatchers.IO) {
                PayloadProfileStore(context).update { list ->
                    val next = list.toMutableList()
                    val target = editTarget
                    if (target != null) {
                        val index = next.indexOfFirst { it.id == target.id }
                        if (index >= 0) next[index] = target.copyForEdit(name, preload, ksud, su, ko, hashes)
                    } else {
                        next.add(0, newPayloadProfile(name, preload, ksud, su, ko, kind, hashes))
                    }
                    next
                }
            }
            profiles = saved
            showEditDialog = false
            editTarget = null
            toast(resources.getString(R.string.payload_saved))
        }
    }

    suspend fun loadNow() {
        profiles = withContext(Dispatchers.IO) { PayloadProfileStore(context).load() }
    }
}

@Composable
private fun rememberPayloadProfilesState(): PayloadProfilesState {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val selection = rememberSelection()
    val state = remember { PayloadProfilesState(context, resources, scope, selection) }
    LaunchedEffect(Unit) { state.loadNow() }
    return state
}

@Composable
fun PayloadProfileScreen(
    onBack: () -> Unit,
) {
    val state = rememberPayloadProfilesState()

    BackHandler(enabled = state.selection.selectionMode) { state.selection.exit() }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        popupHost = { },
        topBar = {
            TopAppBar(
                title = stringResource(
                    if (state.selection.selectionMode) R.string.history_selected_format else R.string.payload_title,
                    state.selection.selectedIds.size,
                ),
                largeTitle = stringResource(
                    if (state.selection.selectionMode) R.string.history_selected_format else R.string.payload_title,
                    state.selection.selectedIds.size,
                ),
                navigationIcon = {
                    IconButton(onClick = { if (state.selection.selectionMode) state.selection.exit() else onBack() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
                actions = {
                    if (state.selection.selectionMode) {
                        IconButton(onClick = { state.showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.payload_delete),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    } else {
                        IconButton(onClick = { state.editTarget = null; state.showEditDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.payload_add),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
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
            if (state.profiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.payload_empty),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                }
            } else {
                items(state.profiles, key = { it.id }) { profile ->
                    ProfileItem(
                        profile = profile,
                        selectionMode = state.selection.selectionMode,
                        selected = profile.id in state.selection.selectedIds,
                        onClick = {
                            if (state.selection.selectionMode) {
                                state.selection.toggle(profile.id)
                            } else {
                                state.editTarget = profile
                                state.showEditDialog = true
                            }
                        },
                        onLongClick = { state.selection.enter(profile.id) },
                    )
                }
            }
        }
    }

    if (state.showEditDialog) {
        ProfileEditDialog(
            title = stringResource(
                if (state.editTarget != null) R.string.payload_edit_title else R.string.payload_add_title,
            ),
            initial = state.editTarget,
            kind = state.editTarget?.kind ?: state.currentKind,
            onSave = state::saveProfile,
            onDismiss = {
                state.showEditDialog = false
                state.editTarget = null
            },
        )
    }

    if (state.showDeleteConfirm) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.payload_delete_confirm_title, state.selection.selectedIds.size),
            summary = stringResource(R.string.history_delete_confirm_body),
            onDismissRequest = { state.showDeleteConfirm = false },
            content = {
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = { state.showDeleteConfirm = false },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(R.string.payload_delete),
                        onClick = {
                            state.showDeleteConfirm = false
                            state.deleteSelected()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            },
        )
    }
}

@Composable
private fun ProfileItem(
    profile: PayloadProfile,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isDark = isInDarkTheme()
    val proven = profile.isProven
    val provenColors = StatusColors.proven(isDark)
    val beanYellow = StatusColors.beanYellow(isDark)
    val bg = when {
        selectionMode && selected -> beanYellow.container
        proven -> provenColors.container
        else -> Color.Transparent
    }
    val contentColor = when {
        selectionMode && selected -> beanYellow.content
        proven -> provenColors.content
        else -> MiuixTheme.colorScheme.onBackground
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bg)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            if (selectionMode) {
                Icon(
                    imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = contentColor,
                )
            } else {
                Icon(
                    imageVector = if (proven) Icons.Rounded.CheckCircleOutline else Icons.Rounded.Code,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (proven) provenColors.content else MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = profile.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = contentColor,
                    )
                    if (profile.kind == PayloadProfileKind.Standalone) {
                        Text(
                            text = stringResource(R.string.payload_kind_standalone),
                            fontSize = 10.sp,
                            color = contentColor.copy(alpha = 0.8f),
                            modifier = Modifier
                                .background(contentColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    text = profile.hashes,
                    fontSize = 11.sp,
                    color = contentColor.copy(alpha = 0.7f),
                )
                val runsText = stringResource(
                    R.string.payload_runs_format,
                    profile.runCount,
                    profile.successCount,
                    profile.failCount,
                )
                val lastRunMillis = profile.lastRunAtMillis
                val lastRunText = remember(lastRunMillis) { lastRunMillis?.let(::formatTime) }
                Text(
                    text = if (lastRunText == null) {
                        runsText
                    } else {
                        "$runsText · " + stringResource(R.string.payload_last_run, lastRunText)
                    },
                    fontSize = 12.sp,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun ProfileEditDialog(
    title: String,
    initial: PayloadProfile?,
    kind: PayloadProfileKind,
    onSave: (name: String, preload: String, ksud: String, su: String, ko: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val standalone = kind == PayloadProfileKind.Standalone
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var preload by remember(initial) { mutableStateOf(initial?.preloadPath ?: "") }
    var ksud by remember(initial) { mutableStateOf(initial?.ksudPath ?: "") }
    var su by remember(initial) { mutableStateOf(initial?.suDaemonPath ?: "") }
    var ko by remember(initial) { mutableStateOf(initial?.koPath ?: "") }
    val kmiHint = remember { DfrPack.detectKmi(DeviceSnapshot.current().kernelVersion) ?: "?" }
    var pickingField by remember { mutableStateOf<Int?>(null) }
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val field = pickingField
        pickingField = null
        if (uri != null && field != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val value = uri.toString()
            when (field) {
                0 -> preload = value
                1 -> ksud = value
                2 -> su = value
                3 -> ko = value
            }
        }
    }
    WindowDialog(
        show = true,
        title = title,
        summary = stringResource(
            if (standalone) R.string.payload_pick_hint_standalone else R.string.payload_pick_hint,
        ),
        onDismissRequest = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfilePathField(
                    label = stringResource(R.string.payload_name_label),
                    value = name,
                    onValueChange = { name = it },
                )
                if (standalone) {
                    PayloadFilePicker(
                        label = stringResource(R.string.payload_file_ko),
                        uri = ko,
                        onClick = {
                            pickingField = 3
                            pickLauncher.launch(arrayOf("*/*"))
                        },
                    )
                    PayloadFilePicker(
                        label = stringResource(R.string.payload_file_ksud),
                        uri = ksud,
                        onClick = {
                            pickingField = 1
                            pickLauncher.launch(arrayOf("*/*"))
                        },
                    )
                    Text(
                        text = stringResource(R.string.payload_kmi_hint, kmiHint),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                } else {
                    PayloadFilePicker(
                        label = stringResource(R.string.payload_file_preload),
                        uri = preload,
                        onClick = {
                            pickingField = 0
                            pickLauncher.launch(arrayOf("*/*"))
                        },
                    )
                    PayloadFilePicker(
                        label = stringResource(R.string.payload_file_ksud),
                        uri = ksud,
                        onClick = {
                            pickingField = 1
                            pickLauncher.launch(arrayOf("*/*"))
                        },
                    )
                    PayloadFilePicker(
                        label = stringResource(R.string.payload_file_su_daemon),
                        uri = su,
                        onClick = {
                            pickingField = 2
                            pickLauncher.launch(arrayOf("*/*"))
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(R.string.payload_save),
                    onClick = { onSave(name.trim(), preload.trim(), ksud.trim(), su.trim(), ko.trim()) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )
}

@Composable
private fun PayloadFilePicker(
    label: String,
    uri: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val displayName by if (uri.isBlank()) {
        remember { mutableStateOf("") }
    } else {
        produceState(initialValue = uri.substringAfterLast('/').ifBlank { uri }, uri) {
            value = withContext(Dispatchers.IO) { uriDisplayName(context, uri) }
        }
    }
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.08f))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (uri.isBlank()) Icons.Rounded.Add else Icons.Rounded.Description,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Text(
                text = if (uri.isBlank()) {
                    stringResource(R.string.payload_no_file)
                } else {
                    displayName
                },
                fontSize = 14.sp,
                maxLines = 1,
                color = if (uri.isBlank()) {
                    MiuixTheme.colorScheme.onBackgroundVariant
                } else {
                    MiuixTheme.colorScheme.onBackground
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun uriDisplayName(context: Context, uriString: String): String {
    val name = runCatching {
        context.contentResolver.query(
            Uri.parse(uriString),
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()
    return name ?: uriString.substringAfterLast('/').ifBlank { uriString }
}

private fun buildHashes(context: Context, preload: String, ksud: String, su: String): String? {
    val parts = listOf(preload, ksud, su).map { uriString ->
        if (!uriString.startsWith("content://")) {
            null
        } else {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use {
                    ExploitPack.sha256(it).take(8)
                }
            }.getOrNull()
        }
    }
    if (parts.any { it == null }) return null
    return "p=${parts[0]} k=${parts[1]} s=${parts[2]}"
}

// Standalone validation: ksud = arm64 ELF containing "late-load"; ko = arm64 ELF, >=4096B,
// 4-byte aligned; blank = use the built-in (hash slot "-").
private fun checkStandaloneFiles(context: Context, ksudUri: String, koUri: String): Pair<String?, Int?> {
    val ksudBytes = ksudUri.takeIf { it.isNotBlank() }?.let {
        readUriBytes(context, it) ?: return null to R.string.payload_verify_failed
    }
    val koBytes = koUri.takeIf { it.isNotBlank() }?.let {
        readUriBytes(context, it) ?: return null to R.string.payload_verify_failed
    }
    if (ksudBytes != null && (!isArm64Elf(ksudBytes) || !ksudBytes.containsAscii("late-load"))) {
        return null to R.string.payload_verify_ksud_failed
    }
    if (koBytes != null && (!isArm64Elf(koBytes) || koBytes.size < 4096 || koBytes.size % 4 != 0)) {
        return null to R.string.payload_verify_ko_failed
    }
    fun part(tag: String, bytes: ByteArray?) = "$tag=${bytes?.let { sha256Hex(it).take(8) } ?: "-"}"
    return "${part("k", ksudBytes)} ${part("o", koBytes)}" to null
}

private fun readUriBytes(context: Context, uriString: String, maxBytes: Int = 64 * 1024 * 1024): ByteArray? =
    runCatching {
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
            val buffer = ByteArray(64 * 1024)
            val out = java.io.ByteArrayOutputStream()
            var total = 0
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                total += n
                if (total > maxBytes) return@runCatching null
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
    }.getOrNull()

private fun isArm64Elf(bytes: ByteArray): Boolean =
    bytes.size >= 20 &&
        bytes[0] == 0x7F.toByte() && bytes[1] == 0x45.toByte() &&
        bytes[2] == 0x4C.toByte() && bytes[3] == 0x46.toByte() &&
        (bytes[18].toInt() and 0xFF) == 0xB7 && (bytes[19].toInt() and 0xFF) == 0x00

private fun ByteArray.containsAscii(needle: String): Boolean {
    val pattern = needle.encodeToByteArray()
    if (pattern.isEmpty() || size < pattern.size) return false
    outer@ for (i in 0..size - pattern.size) {
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) continue@outer
        }
        return true
    }
    return false
}

private fun sha256Hex(bytes: ByteArray): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

@Composable
private fun ProfilePathField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.height(4.dp))
        TextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            maxLines = 1,
            onValueChange = onValueChange,
        )
    }
}

private val profileTimeFormat by lazy { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

private fun formatTime(millis: Long): String = profileTimeFormat.format(Date(millis))
