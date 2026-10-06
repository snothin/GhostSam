package com.snothin.ghostsam.data.channel

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings as AndroidSettings
import io.github.muntashirakon.adb.AdbConnection
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.android.AdbMdns
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import com.snothin.ghostsam.R
import com.snothin.ghostsam.data.prefs.SettingsPrefs

object AdbController {

    private const val MDNS_SERVICE_PAIRING = "adb-tls-pairing"
    private const val MDNS_SERVICE_CONNECT = "adb-tls-connect"

    /** Loopback connect timeout: the first classic-TCP dial may need the on-device "Allow USB debugging?" prompt. */
    private const val LOOPBACK_CONNECT_TIMEOUT_MS = 60_000L

    private const val CONNECT_TIMEOUT_MS = 15_000L

    private const val TCP_PREFS = "adb_tcp"
    private const val KEY_ARMED_PORT = "armed_port"
    private const val KEY_ARMED_BOOT = "armed_boot"

    private const val ARM_LOOPBACK_ATTEMPTS = 8

    @Volatile
    private var bridge: AdbBridge? = null

    @Volatile
    private var connection: AdbConnection? = null

    @Volatile
    private var suppressConnectionEvents = false

    /** Connect-switch mutex: close-old -> build-new -> assign must not interleave with armTcp/reconnectLast
     *  (a lost reference leaks the old adbd session). */
    private val connectMutex = Mutex()

    private val WC_SIZE_PREFIX = Regex("^\\s*(\\d+)")

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var armedPortValue: Int = 0

    @Volatile
    var lastConnectNeedsPairing: Boolean = false
        private set

    fun hasPairedBefore(): Boolean =
        appContext?.let { SettingsPrefs.adbPaired(it) } ?: false

    private val _armedPortFlow = MutableStateFlow(0)

    val armedPortFlow: StateFlow<Int> = _armedPortFlow.asStateFlow()

    private val _armErrorFlow = MutableStateFlow<String?>(null)

    val armErrorFlow: StateFlow<String?> = _armErrorFlow.asStateFlow()

    private val _connectedFlow = MutableStateFlow(false)

    /** Session-state flow: UI must subscribe, never sample isConnected (sampling hits the TCP-switch gap
     *  and reads a false disconnect). */
    val connectedFlow: StateFlow<Boolean> = _connectedFlow.asStateFlow()

    @Volatile
    private var lastHost: String? = null

    @Volatile
    private var lastPort: Int = 5555

    fun init(context: Context) {
        appContext = context.applicationContext
        if (bridge == null) {
            bridge = AdbBridge(context.applicationContext)
        }
        restoreArmedState()
    }

    private fun bridge(): AdbBridge = bridge ?: error("AdbController not initialized")

    private fun uiText(resId: Int): String = appContext?.getString(resId).orEmpty()

    fun isConnected(): Boolean = connection != null

    suspend fun pair(host: String, port: Int, code: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                bridge().pair(host, port, code)
                appContext?.let { SettingsPrefs.setAdbPaired(it, true) }
                true
            } catch (_: Throwable) {
                false
            }
        }

    suspend fun connect(
        host: String,
        port: Int,
        timeoutMs: Long = CONNECT_TIMEOUT_MS,
        allowFirstTimeAuthorisation: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        connectMutex.withLock { connectLocked(host, port, timeoutMs, allowFirstTimeAuthorisation) }
    }

    private fun connectLocked(
        host: String,
        port: Int,
        timeoutMs: Long,
        allowFirstTimeAuthorisation: Boolean,
        recordLast: Boolean = true,
    ): Boolean {
        lastConnectNeedsPairing = false
        try {
            disconnectLocked()
            setConnectionLocked(bridge().connect(host, port, timeoutMs, allowFirstTimeAuthorisation))
            if (recordLast) {
                lastHost = host
                lastPort = port
            }
            appContext?.let { SettingsPrefs.setAdbPaired(it, true) }
            return true
        } catch (error: Throwable) {
            if (error is AdbPairingRequiredException) lastConnectNeedsPairing = true
            return false
        }
    }

    suspend fun reconnectLast(): Boolean {
        val armed = armedPortValue
        if (armed > 0) {
            val loopback = withContext(Dispatchers.IO) {
                connectMutex.withLock {
                    connectLocked("127.0.0.1", armed, CONNECT_TIMEOUT_MS, false, recordLast = false)
                }
            }
            if (loopback) return true
            clearArmed()
            _armErrorFlow.value = uiText(R.string.tcp_arm_error_expired)
        }
        val host = lastHost ?: return false
        return connect(host, lastPort)
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        connectMutex.withLock { disconnectLocked() }
    }

    private fun disconnectLocked() {
        runCatching { connection?.close() }
        setConnectionLocked(null)
    }

    private fun setConnectionLocked(conn: AdbConnection?) {
        connection = conn
        if (!suppressConnectionEvents) _connectedFlow.value = conn != null
    }

    // TCP mode (no-WiFi reconnect): arm once per boot; the loopback channel can then reconnect any number of
    // times (adbd state does not outlive a reboot).

    const val TCP_PORT = 5555

    fun configuredPort(): Int =
        appContext?.let { SettingsPrefs.tcpPort(it) } ?: TCP_PORT

    fun isArmed(): Boolean = armedPortValue > 0

    /** Arm TCP: request `tcpip:<port>` (adbd restarts) then loopback-reconnect; null = success, else user-visible error.
     *  Lock boundary: the blocking open runs OUTSIDE connectMutex; the lock does the bounded reconnect only. */
    suspend fun armTcp(port: Int = 0): String? = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext uiText(R.string.tcp_arm_error_not_connected)
        val targetPort = if (port > 0) port else configuredPort()
        try {
            val stream = conn.open("tcpip:$targetPort")
            readStreamBestEffort(stream)
            runCatching { stream.close() }
        } catch (_: Throwable) {
        }
        // Planned switch: no intermediate connection events (anti-flicker); the suppression window, the switch
        // and the final publish share one lock section.
        connectMutex.withLock {
            suppressConnectionEvents = true
            try {
                armTcpLocked(targetPort)
            } finally {
                suppressConnectionEvents = false
                _connectedFlow.value = connection != null
            }
        }
    }

    private suspend fun armTcpLocked(targetPort: Int): String? {
        disconnectLocked()
        var lastError = ""
        repeat(ARM_LOOPBACK_ATTEMPTS) { attempt ->
            try {
                val timeout = if (attempt == 0) LOOPBACK_CONNECT_TIMEOUT_MS else 5_000L
                setConnectionLocked(
                    bridge().connect(
                        "127.0.0.1", targetPort, timeout, allowFirstTimeAuthorisation = true,
                    ),
                )
                // Loopback dials never overwrite lastHost/lastPort (the LAN address must survive for self-healing).
                markArmed(targetPort)
                return null
            } catch (error: Throwable) {
                lastError = "${error.javaClass.simpleName}: ${error.message}"
                delay(700)
            }
        }
        // All loopback attempts failed: replay the original connection (a failed switch must not lose it).
        lastHost?.let { host ->
            runCatching { connectLocked(host, lastPort, CONNECT_TIMEOUT_MS, false) }
        }
        val reason = lastError.ifBlank { uiText(R.string.tcp_arm_error_unknown) }
        _armErrorFlow.value = reason
        return reason
    }

    suspend fun disarmTcp(): String = withContext(Dispatchers.IO) {
        val conn = connection
        var requested = false
        if (conn != null) {
            requested = try {
                val stream = conn.open("usb:")
                readStreamBestEffort(stream)
                runCatching { stream.close() }
                true
            } catch (_: Throwable) {
                false
            }
        }
        connectMutex.withLock {
            if (conn != null) disconnectLocked()
            clearArmed()
            when {
                requested -> uiText(R.string.tcp_disarm_done)
                conn != null -> uiText(R.string.tcp_disarm_usb_unconfirmed)
                else -> uiText(R.string.tcp_disarm_listener_left)
            }
        }
    }

    private fun tcpPrefs(): SharedPreferences? =
        appContext?.getSharedPreferences(TCP_PREFS, Context.MODE_PRIVATE)

    private fun markArmed(port: Int) {
        armedPortValue = port
        _armedPortFlow.value = port
        _armErrorFlow.value = null
        tcpPrefs()?.edit()
            ?.putInt(KEY_ARMED_PORT, port)
            ?.putString(KEY_ARMED_BOOT, currentBootId())
            ?.apply()
    }

    private fun clearArmed() {
        armedPortValue = 0
        _armedPortFlow.value = 0
        _armErrorFlow.value = null
        tcpPrefs()?.edit()?.remove(KEY_ARMED_PORT)?.remove(KEY_ARMED_BOOT)?.apply()
    }

    private fun restoreArmedState() {
        val prefs = tcpPrefs() ?: return
        val port = prefs.getInt(KEY_ARMED_PORT, 0)
        if (port <= 0) {
            armedPortValue = 0
            _armedPortFlow.value = 0
            return
        }
        val boot = prefs.getString(KEY_ARMED_BOOT, null)
        armedPortValue = if (boot != null && boot == currentBootId()) port else 0
        _armedPortFlow.value = armedPortValue
        if (armedPortValue == 0) prefs.edit().remove(KEY_ARMED_PORT).remove(KEY_ARMED_BOOT).apply()
    }

    private fun currentBootId(): String {
        val context = appContext
        if (context != null) {
            val bootCount = runCatching {
                AndroidSettings.Global.getInt(
                    context.contentResolver, AndroidSettings.Global.BOOT_COUNT, -1,
                )
            }.getOrDefault(-1)
            if (bootCount >= 0) return "bc:$bootCount"
        }
        return runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }
            .getOrDefault("unknown")
    }

    private fun readStreamBestEffort(stream: AdbStream, timeoutMs: Long = 2_000): String {
        val holder = arrayOf("")
        val reader = Thread {
            runCatching {
                val buffer = StringBuilder()
                stream.openInputStream().bufferedReader().use { input ->
                    val chunk = CharArray(256)
                    while (true) {
                        val n = input.read(chunk)
                        if (n <= 0) break
                        buffer.append(chunk, 0, n)
                    }
                }
                holder[0] = buffer.toString()
            }
        }
        reader.isDaemon = true
        reader.start()
        reader.join(timeoutMs)
        return holder[0]
    }

    private fun requireConnection(): AdbConnection =
        connection ?: throw IllegalStateException("ADB not connected")

    fun exec(cmd: Array<String>, env: Array<String>? = null, dir: String? = null): Process {
        val conn = requireConnection()
        // A failed stream open must NEVER disconnect(): that would silently kill every session on this connection
        // (incl. a running payload). Timeout/failure is the caller's; reconnect only after the stream is dead.
        val stream = conn.open("shell:" + shellCommand(cmd, env))
        return AdbProcess(stream)
    }

    suspend fun execCommand(command: String, timeoutMillis: Long = 20_000): String =
        withContext(Dispatchers.IO) {
            val process = try {
                exec(arrayOf("sh", "-c", command))
            } catch (error: Throwable) {
                return@withContext channelError(error)
            }
            try {
                collectProcessOutputCancellable(process, timeoutMillis)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                channelError(error)
            }
        }

    /** Write a local file + chmod via one `cat > && chmod` stream, then read back `wc -c` and compare:
     *  a silently truncated staging shows up immediately with byte counts (3 retries on jitter). */
    fun writeFile(remotePath: String, mode: String, source: InputStream) {
        val conn = requireConnection()
        val parent = remotePath.substringBeforeLast('/', missingDelimiterValue = "")
        val mkdir = if (parent.isNotEmpty()) "mkdir -p '$parent' && " else ""
        val stream = conn.open("shell:${mkdir}cat > '$remotePath' && chmod $mode '$remotePath'")
        var written = 0L
        stream.use { current ->
            val output = current.openOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = source.read(buffer)
                if (n <= 0) break
                output.write(buffer, 0, n)
                written += n
            }
            output.flush()
        }
        var remoteSize: Long? = null
        var lastOut = ""
        for (attempt in 0 until 3) {
            lastOut = collectProcessOutput(exec(arrayOf("wc", "-c", remotePath)), 3_000).trim()
            remoteSize = WC_SIZE_PREFIX.find(lastOut)?.groupValues?.get(1)?.toLongOrNull()
            if (remoteSize == written) return
            if (attempt < 2) {
                try {
                    Thread.sleep(300)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        val remoteBytes = remoteSize
            ?: throw IllegalStateException(
                "staging write readback failed (3 attempts): ${lastOut.take(160)}",
            )
        throw IllegalStateException("staging write truncated: $remotePath ($remoteBytes != $written)")
    }

    suspend fun discoverPairingPort(context: Context, timeoutMs: Long = 5_000): Pair<String?, Int>? =
        discover(context, MDNS_SERVICE_PAIRING, timeoutMs)

    suspend fun discoverConnectPort(context: Context, timeoutMs: Long = 5_000): Pair<String?, Int>? =
        discover(context, MDNS_SERVICE_CONNECT, timeoutMs)

    private suspend fun discover(
        context: Context,
        serviceType: String,
        timeoutMs: Long,
    ): Pair<String?, Int>? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(timeoutMs.milliseconds) {
            var mdns: AdbMdns? = null
            try {
                suspendCancellableCoroutine { continuation ->
                    var found: Pair<String?, Int>? = null
                    val discovery = AdbMdns(context, serviceType) { hostAddress, port ->
                        if (port > 0 && found == null && continuation.isActive) {
                            found = hostAddress?.hostAddress to port
                            continuation.resume(found)
                        }
                    }
                    mdns = discovery
                    discovery.start()
                    continuation.invokeOnCancellation { discovery.stop() }
                }
            } finally {
                runCatching { mdns?.stop() }
            }
        }
    }

    /** Join exec argv into one adb shell line `sh -c '<env><cmd>'`: the final shell inherits env,
     *  so LD_PRELOAD takes effect in its constructor. */
    private fun shellCommand(cmd: Array<String>, env: Array<String>?): String {
        val envPrefix = env?.joinToString(" ") { "$it " }.orEmpty()
        // Args with spaces/quotes MUST be quoted individually; joining first and quoting once hands
        // only the first word to the inner sh -c (silent failure).
        return envPrefix + cmd.joinToString(" ") { it.shQuote() }
    }

    private fun String.shQuote(): String =
        if (all { it.isLetterOrDigit() || it in "-_./:=," }) this
        else "'" + replace("'", "'\\''") + "'"
}

/** AdbStream wrapped as a standard [Process]: a drain thread relays into a [PipedInputStream];
 *  waitFor() waits for EOF (stream close = remote exit). Exit codes are NOT carried (waitFor()
 *  always 0) - the real rc comes from the boot status file's status= field. */
private class AdbProcess(private val stream: AdbStream) : Process() {

    companion object {
        private const val PIPE_SIZE = 16 * 1024
    }

    private val pipeIn = PipedInputStream(PIPE_SIZE)
    private val pipeOut = PipedOutputStream(pipeIn)
    private val done = CountDownLatch(1)

    private val drainThread = Thread {
        try {
            val input = stream.openInputStream()
            val buf = ByteArray(0x2000)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                pipeOut.write(buf, 0, n)
            }
        } catch (_: Throwable) {
        } finally {
            try {
                pipeOut.close()
            } catch (_: Throwable) {
            }
            done.countDown()
        }
    }.apply {
        isDaemon = true
        start()
    }

    override fun getInputStream(): InputStream = pipeIn

    override fun getOutputStream(): OutputStream = stream.openOutputStream()

    override fun getErrorStream(): InputStream = EmptyInputStream

    override fun waitFor(): Int {
        done.await()
        return 0
    }

    /** Timed wait; true = exited, false = timeout (caller destroys). No bare waitFor (it can hang). */
    override fun waitFor(timeout: Long, unit: java.util.concurrent.TimeUnit): Boolean =
        try {
            done.await(timeout, unit)
        } catch (_: InterruptedException) {
            false
        }

    override fun exitValue(): Int {
        if (isAlive()) throw IllegalThreadStateException("process hasn't exited")
        return 0
    }

    override fun isAlive(): Boolean = done.count > 0

    override fun destroy() {
        runCatching { stream.close() }
        runCatching { pipeIn.close() }
    }

    override fun destroyForcibly(): Process {
        destroy()
        return this
    }
}
