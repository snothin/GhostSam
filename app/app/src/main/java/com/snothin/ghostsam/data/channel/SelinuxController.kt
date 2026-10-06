package com.snothin.ghostsam.data.channel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SelinuxState {
    Enforcing,
    Permissive,
    Unknown,
}

/** SELinux query/toggle; channel order: su -> su_daemon -> adb shell -> Shizuku shell;
 *  Unknown/false when none is available. */
object SelinuxController {

    suspend fun query(): SelinuxState = withContext(Dispatchers.IO) {
        runCatching {
            val output = if (SuController.isAvailable()) {
                SuController.execCommand("getenforce", timeoutMillis = 5_000)
            } else if (SuDaemonChannel.isAvailable()) {
                SuDaemonChannel.execPrivileged("getenforce", timeoutMillis = 5_000)
            } else if (AdbController.isConnected()) {
                AdbController.execCommand("getenforce", timeoutMillis = 5_000)
            } else if (ShizukuController.isGranted()) {
                collectProcessOutput(ShizukuController.exec(arrayOf("getenforce")), 5_000)
            } else {
                return@runCatching SelinuxState.Unknown
            }
            when (output?.trim()) {
                "Enforcing" -> SelinuxState.Enforcing
                "Permissive" -> SelinuxState.Permissive
                else -> SelinuxState.Unknown
            }
        }.getOrDefault(SelinuxState.Unknown)
    }

    suspend fun toggle(current: SelinuxState): Boolean = withContext(Dispatchers.IO) {
        if (current == SelinuxState.Unknown) return@withContext false
        val target = if (current == SelinuxState.Enforcing) "0" else "1"
        val expected = if (current == SelinuxState.Enforcing) "Permissive" else "Enforcing"
        when {
            SuController.isAvailable() -> runCatching {
                SuController.execCommand("setenforce $target", timeoutMillis = 5_000)
                SuController.execCommand("getenforce", timeoutMillis = 5_000).trim() == expected
            }.getOrDefault(false)
            SuDaemonChannel.isAvailable() -> runCatching {
                SuDaemonChannel.execPrivileged("setenforce $target") != null &&
                    SuDaemonChannel.execPrivileged("getenforce", timeoutMillis = 5_000)
                        .orEmpty().trim() == expected
            }.getOrDefault(false)
            AdbController.isConnected() -> runCatching {
                collectProcessOutput(AdbController.exec(arrayOf("su", "-c", "setenforce $target")), 10_000)
                AdbController.execCommand("getenforce", timeoutMillis = 5_000).trim() == expected
            }.getOrDefault(false)
            ShizukuController.isGranted() -> runCatching {
                collectProcessOutput(ShizukuController.exec(arrayOf("su", "-c", "setenforce $target")), 10_000)
                collectProcessOutput(ShizukuController.exec(arrayOf("getenforce")), 5_000).trim() == expected
            }.getOrDefault(false)
            else -> false
        }
    }
}
