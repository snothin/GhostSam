package com.snothin.ghostsam.system

import android.content.Context
import android.os.SystemClock
import com.snothin.ghostsam.R
import com.snothin.ghostsam.data.channel.ExploitChannel
import com.snothin.ghostsam.data.channel.SuController
import com.snothin.ghostsam.data.channel.SuDaemonChannel
import com.snothin.ghostsam.data.channel.TIMEOUT_MARKER
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Install/update sequence (settings page + post-DFR auto-prep): root fast path `pm install -r`;
 *  failure -> detached script (inject -> uninstall residue -> kill system_server -> install ->
 *  restart networkstack); no root -> wireless-debugging / Shizuku channel direct install. */
object CompanionInstaller {

    suspend fun install(
        context: Context,
        update: Boolean,
        builtin: CompanionPack.Builtin,
        onProgress: (String) -> Unit,
    ): String {
        val canRoot = withContext(Dispatchers.IO) {
            SuController.isAvailable() || SuDaemonChannel.isAvailable()
        }
        if (!canRoot) {
            return if (ExploitChannel.isAvailable()) {
                installViaChannel(context, update, builtin, onProgress)
            } else {
                context.getString(R.string.settings_companion_install_no_root)
            }
        }
        val stagePath = "/data/local/tmp/gs-companion.apk"
        val logFile = File(context.filesDir, "companion/install.log")
        val base = context.applicationInfo.sourceDir
        val comp = builtin.file.absolutePath
        rootExec("cp $comp $stagePath; chmod 644 $stagePath", 60_000)
        val out = rootExec("pm install -r --user 0 $stagePath", 180_000)
        val after = CompanionClient.state(context)
        val ok = out.contains("Success") && after.installed && after.uid == 1000 &&
            (after.versionCode ?: 0L) >= builtin.versionCode
        if (ok) {
            if (!update) return context.getString(R.string.settings_companion_install_done, builtin.versionName)
            dispatchDetached(
                logFile,
                "echo \"[*] restart framework\"; kill -9 $(pidof system_server); " +
                    "i=0; while ! service check package 2>/dev/null | grep -q \"package: found\"; " +
                    "do sleep 1; i=$((i+1)); [ \$i -gt 240 ] && { echo \"[x] framework timeout\"; exit 9; }; done; " +
                    "kill -9 $(pidof com.android.networkstack.process) 2>/dev/null; echo \"[done]\"",
            )
            return context.getString(R.string.settings_companion_update_reboot_pending)
        }
        val script = buildString {
            append("umask 000; echo \"[*] start\"; ")
            append("OUT=$(CLASSPATH=$base app_process /system/bin com.snothin.ghostsam.system.SystemInjectMain ")
            append("--mode check --apk $comp 2>&1); echo \"\$OUT\"; ")
            append("case \"\$OUT\" in *injected=true*) echo \"[*] injection present\" ;; *) echo \"[*] injecting\"; ")
            append("CLASSPATH=$base app_process /system/bin com.snothin.ghostsam.system.SystemInjectMain ")
            append("--mode inject --apk $comp 2>&1 ;; esac; ")
            append("pm uninstall --user 0 com.snothin.ghostsam.companion 2>/dev/null; ")
            append("kill -9 $(pidof system_server); i=0; ")
            append("while ! service check package 2>/dev/null | grep -q \"package: found\"; ")
            append("do sleep 1; i=$((i+1)); [ \$i -gt 240 ] && { echo \"[x] framework timeout\"; exit 9; }; done; ")
            append("echo \"[*] installing\"; pm install -r --user 0 $stagePath 2>&1; ")
            append("kill -9 $(pidof com.android.networkstack.process) 2>/dev/null; echo \"[done]\"")
        }
        dispatchDetached(logFile, script)
        onProgress(context.getString(R.string.settings_companion_install_reboot_pending))
        val deadline = SystemClock.elapsedRealtime() + 8 * 60_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(2_000)
            val st = runCatching { CompanionClient.state(context) }.getOrNull()
            if (st != null && st.installed && st.uid == 1000 && (st.versionCode ?: 0L) >= builtin.versionCode) {
                return context.getString(R.string.settings_companion_install_done, builtin.versionName)
            }
            val tail = withContext(Dispatchers.IO) { readLogTail(logFile, 5) }
            if (tail.isNotEmpty()) {
                onProgress(context.getString(R.string.settings_companion_install_reboot_pending) + "\n" + tail)
            }
        }
        return context.getString(R.string.settings_companion_install_timeout)
    }

    private suspend fun installViaChannel(
        context: Context,
        update: Boolean,
        builtin: CompanionPack.Builtin,
        onProgress: (String) -> Unit,
    ): String {
        onProgress(context.getString(R.string.settings_companion_install_channel_push))
        val stagePath = "/data/local/tmp/gs-companion.apk"
        val pushError = withContext(Dispatchers.IO) {
            runCatching { ExploitChannel.writeFile(stagePath, "644", builtin.file.inputStream()) }
                .exceptionOrNull()
        }
        if (pushError != null) {
            return context.getString(
                R.string.settings_companion_install_channel_fail,
                "${pushError.javaClass.simpleName}: ${pushError.message.orEmpty()}",
            )
        }
        val out = ExploitChannel.execShell("pm install -r --user 0 $stagePath", 180_000)
        val after = CompanionClient.state(context)
        if (out.contains("Success") && after.installed && after.uid == 1000 &&
            (after.versionCode ?: 0L) >= builtin.versionCode
        ) {
            return if (update) {
                context.getString(R.string.settings_companion_update_channel_pending, builtin.versionName)
            } else {
                context.getString(R.string.settings_companion_install_done, builtin.versionName)
            }
        }
        val reason = when {
            out.contains("SHARED_USER_INCOMPATIBLE") ->
                context.getString(R.string.settings_companion_install_need_inject)
            out == TIMEOUT_MARKER -> context.getString(R.string.settings_companion_install_timeout)
            after.installed && after.uid != 1000 ->
                context.getString(R.string.settings_companion_install_channel_uid, after.uid ?: -1)
            else -> out.lineSequence().firstOrNull { it.isNotBlank() }?.take(160) ?: "unknown"
        }
        return context.getString(R.string.settings_companion_install_channel_fail, reason)
    }

    private suspend fun rootExec(command: String, timeoutMillis: Long): String {
        if (SuController.isAvailable()) return SuController.execCommand(command, timeoutMillis)
        return SuDaemonChannel.execPrivileged(command, timeoutMillis)
            ?: "ERROR: root channel unavailable"
    }

    /** Detached execution: output to [logFile] (umask 000, app-readable); setsid so a su/app exit
     *  (incl. soft reboot) cannot interrupt the device-side sequence. */
    private suspend fun dispatchDetached(logFile: File, script: String) {
        logFile.parentFile?.mkdirs()
        rootExec(
            "umask 000; setsid sh -c '$script' > ${logFile.absolutePath} 2>&1 &",
            15_000,
        )
    }

    private fun readLogTail(logFile: File, lines: Int): String = runCatching {
        val all = logFile.readText().trim().lines()
        all.subList(maxOf(0, all.size - lines), all.size).joinToString("\n").take(600)
    }.getOrDefault("")
}
