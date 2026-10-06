package com.snothin.ghostsam.system

import android.content.Context
import java.io.File

/** Persist orchestration: pure command builders + a channel-agnostic exec wrapper; no privileged
 *  channel lives here (the caller injects exec per root shape). Order: inject -> soft reboot ->
 *  install -> verify; paths are controlled (no spaces); injector protocol [*]/[+]/[!]/[x]. */
object SystemPersist {

    const val COMPANION_PKG = "com.snothin.ghostsam.companion"

    data class Result(val ok: Boolean, val output: String, val failure: String? = null)

    fun companion(context: Context): Pair<File, String> = CompanionPack.resolve(context)

    fun baseApk(context: Context): String = context.applicationInfo.sourceDir

    fun injectorCommand(context: Context, mode: String, companionPath: String): String =
        "CLASSPATH=${baseApk(context)} app_process /system/bin --nice-name=ghostsam-inject " +
            "com.snothin.ghostsam.system.SystemInjectMain --mode $mode --apk $companionPath"

    suspend fun run(
        context: Context,
        mode: String,
        exec: suspend (String) -> String,
        companionPath: String = companion(context).first.absolutePath,
    ): Result {
        val output = try {
            exec(injectorCommand(context, mode, companionPath))
        } catch (t: Throwable) {
            return Result(false, "", t.message ?: t.javaClass.simpleName)
        }
        val failure = output.lineSequence()
            .firstOrNull { it.startsWith("[x] ") }
            ?.removePrefix("[x] ")
        val ok = failure == null && output.contains("[*] ghostsam-inject")
        return Result(ok, output, failure)
    }

    fun softRebootCommand(): String = "kill -9 $(pidof system_server)"

    fun frameworkProbeCommand(): String = "service check package"

    fun installCompanionCommand(companionPath: String): String =
        "pm install -r --user 0 $companionPath"

    const val ASSET_DIR = "/data/system/ghostsam"

    fun forceUninstallCommand(): String = "pm uninstall --user 0 $COMPANION_PKG"

    fun cleanAssetsCommand(): String = "rm -rf $ASSET_DIR"

    fun parseInjected(output: String): Boolean? =
        Regex("injected=(true|false)").find(output)
            ?.groupValues?.get(1)?.toBooleanStrictOrNull()

    fun verifyCommand(): String =
        "dumpsys package $COMPANION_PKG | grep -m2 -E \"userId=|sharedUser\""

    fun parseCompanionUid(output: String): Int? =
        Regex("userId=(\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull()
}
