package com.snothin.ghostsam.data.channel

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/** Shizuku connection management; [execPrivileged] is the unified privileged chain:
 *  su -> su_daemon -> adb -> Shizuku. */
object ShizukuController {
    private const val PERMISSION_REQUEST_CODE = 0x5352

    suspend fun execPrivileged(command: String, timeoutMillis: Long = 20_000): String =
        withContext(Dispatchers.IO) {
            if (SuController.isAvailable()) {
                val out = SuController.execCommand(command, timeoutMillis)
                if (isSuFailure(out)) {
                    SuController.invalidateCache()
                } else {
                    return@withContext out
                }
            }
            val rootOut = SuDaemonChannel.execPrivileged(command, timeoutMillis)
            if (rootOut != null) {
                return@withContext rootOut
            }
            if (AdbController.isConnected()) {
                return@withContext AdbController.execCommand(command, timeoutMillis)
            }
            if (!isGranted()) {
                return@withContext "$CHANNEL_ERROR_PREFIX: no channel (adb not connected, Shizuku not granted, su not available)"
            }
            execCommand(command, timeoutMillis)
        }

    /** su failure = channel error / explicit denial. NEVER match version banners or normal output
     *  substrings: a false positive clears the cache and re-runs a non-idempotent command. */
    private fun isSuFailure(output: String): Boolean =
        isChannelFailure(output) || output.contains("denied", ignoreCase = true)

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    suspend fun pingUntilRunning(timeoutMillis: Long = 3_000): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isRunning()) return true
            delay(100)
        }
        return isRunning()
    }

    fun isGranted(): Boolean = try {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    private val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val granted: StateFlow<Boolean> = callbackFlow {
        fun update() {
            trySend(isGranted())
        }

        val received = object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() = update()
        }
        val dead = object : Shizuku.OnBinderDeadListener {
            override fun onBinderDead() {
                trySend(false)
            }
        }
        val perm = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) = update()
        }
        Shizuku.addBinderReceivedListener(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(perm)
        update()
        awaitClose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(perm)
        }
    }.stateIn(
        stateScope,
        SharingStarted.WhileSubscribed(5_000),
        initialValue = isGranted(),
    )

    suspend fun requestPermission(): Boolean {
        if (isGranted()) return true
        if (!isRunning()) return false
        return suspendCancellableCoroutine { continuation ->
            lateinit var listener: Shizuku.OnRequestPermissionResultListener
            listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode == PERMISSION_REQUEST_CODE) {
                    Shizuku.removeRequestPermissionResultListener(listener)
                    continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            continuation.invokeOnCancellation {
                Shizuku.removeRequestPermissionResultListener(listener)
            }
            try {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            } catch (error: Throwable) {
                Shizuku.removeRequestPermissionResultListener(listener)
                continuation.resume(false)
            }
        }
    }

    fun exec(cmd: Array<String>, env: Array<String>? = null, dir: String? = null): Process {
        val binder = Shizuku.getBinder()
            ?: throw IllegalStateException("Shizuku binder is not available")
        return RemoteProcess(IShizukuService.Stub.asInterface(binder).newProcess(cmd, env, dir))
    }

    /** Run one command synchronously as shell (sh -c); merged output with house conventions
     *  ("(exit N, no output)" / "ERROR: <msg>" / "(timeout)"). No timed waitFor: the remote
     *  exitValue exception crosses Binder as a plain RuntimeException - poll isAlive() instead. */
    suspend fun execCommand(
        command: String,
        timeoutMillis: Long = 20_000,
    ): String = withContext(Dispatchers.IO) {
        try {
            val process = exec(arrayOf("sh", "-c", command))
            try {
                collectProcessOutput(process, timeoutMillis)
            } finally {
                if (process.isAlive) process.destroy()
            }
        } catch (error: Throwable) {
            channelError(error)
        }
    }

    fun writeFile(remotePath: String, mode: String, source: InputStream) {
        val parent = remotePath.substringBeforeLast('/', missingDelimiterValue = "")
        val mkdir = if (parent.isNotEmpty()) "mkdir -p '$parent' && " else ""
        val process = exec(arrayOf("sh", "-c", "${mkdir}cat > '$remotePath' && chmod $mode '$remotePath'"))
        val exitCode = try {
            process.outputStream.use { output ->
                source.use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            }
            process.waitFor()
        } finally {
            if (process.isAlive) process.destroy()
        }
        check(exitCode == 0) { "Failed to stage $remotePath (exit $exitCode)" }
    }

    private class RemoteProcess(private val remote: IRemoteProcess) : Process() {
        private val input by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.getInputStream()) }
        private val output by lazy { ParcelFileDescriptor.AutoCloseOutputStream(remote.getOutputStream()) }
        private val error by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.getErrorStream()) }

        override fun getInputStream(): InputStream = input
        override fun getOutputStream(): OutputStream = output
        override fun getErrorStream(): InputStream = error
        override fun waitFor(): Int = remote.waitFor()
        override fun exitValue(): Int = remote.exitValue()

        override fun destroy() {
            runCatching { remote.destroy() }
        }

        override fun destroyForcibly(): Process {
            destroy()
            return this
        }

        override fun isAlive(): Boolean = remote.alive()
    }
}
