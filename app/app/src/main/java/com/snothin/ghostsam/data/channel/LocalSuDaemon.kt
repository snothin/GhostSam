package com.snothin.ghostsam.data.channel

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.SystemClock
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import com.snothin.ghostsam.data.Contract

/** Local AF_UNIX client: in a permissive session the app connects temp_su.sock directly (no adb/Shizuku
 *  bridge); enforcing denies the connect. Protocol: 'C' run command, 'K' late-load, 'I' shell; LE u32. */
object LocalSuDaemon {

    const val SOCK_PATH = Contract.SOCKET

    private const val OP_CMD = 'C'.code
    private const val OP_LATE_LOAD = 'K'.code
    private const val OP_SHELL = 'I'.code

    private const val KSU_PROTO_VERSION = 1

    private fun open(): LocalSocket? = runCatching {
        LocalSocket().also {
            it.connect(LocalSocketAddress(SOCK_PATH, LocalSocketAddress.Namespace.FILESYSTEM))
        }
    }.getOrNull()

    fun isAvailable(): Boolean =
        execCommand("id")?.contains("uid=0") == true

    /** Run one command ('C'): [timeoutMillis] is the deadline of the WHOLE command; a per-read timeout
     *  would let streaming commands (logcat) run forever. */
    fun execCommand(command: String, timeoutMillis: Long = 20_000): String? {
        val socket = open() ?: return null
        try {
            val deadline = SystemClock.elapsedRealtime() + timeoutMillis
            val out = socket.outputStream
            val payload = command.toByteArray(Charsets.UTF_8)
            out.write(OP_CMD)
            writeIntLe(out, payload.size)
            out.write(payload)
            out.flush()
            val sb = StringBuilder()
            val buf = ByteArray(4096)
            var timedOut = false
            while (true) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) {
                    timedOut = true
                    break
                }
                socket.soTimeout = minOf(remaining, 1_000L).toInt()
                val n = try {
                    socket.inputStream.read(buf)
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                }
                if (n < 0) break
                if (n > 0) sb.append(String(buf, 0, n, Charsets.UTF_8))
            }
            return if (timedOut) TIMEOUT_MARKER else sb.toString().trim()
        } catch (_: IOException) {
            return null
        } finally {
            runCatching { socket.close() }
        }
    }

    fun lateLoad(
        timeoutMillis: Long = 60_000,
        onChunk: (String) -> Unit = {},
    ): Pair<String, Int>? {
        val socket = open() ?: return null
        try {
            val deadline = SystemClock.elapsedRealtime() + timeoutMillis
            val out = socket.outputStream
            out.write(OP_LATE_LOAD)
            out.write(KSU_PROTO_VERSION)
            out.flush()
            val input = socket.inputStream
            val sb = StringBuilder()
            while (true) {
                val cl = readIntLeDeadline(socket, input, deadline) ?: return null
                if (cl == 0) {
                    val st = readIntLeDeadline(socket, input, deadline) ?: return null
                    return sb.toString() to st
                }
                if (cl > (1 shl 20)) return null
                var left = cl
                val buf = ByteArray(4096)
                while (left > 0) {
                    val want = minOf(left, buf.size)
                    val got = readFullyDeadline(socket, input, buf, want, deadline) ?: return null
                    val text = String(buf, 0, got, Charsets.UTF_8)
                    sb.append(text)
                    onChunk(text)
                    left -= got
                }
            }
        } catch (_: IOException) {
            return null
        } finally {
            runCatching { socket.close() }
        }
    }

    fun spawn(): LocalSocket? = runCatching {
        val socket = open() ?: return@runCatching null
        try {
            socket.outputStream.write(OP_SHELL)
            socket.outputStream.flush()
            socket
        } catch (error: Throwable) {
            runCatching { socket.close() }
            throw error
        }
    }.getOrNull()

    private fun writeIntLe(out: OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }

    private fun readIntLeDeadline(socket: LocalSocket, input: InputStream, deadline: Long): Int? {
        val b = ByteArray(4)
        if (readFullyDeadline(socket, input, b, 4, deadline) != 4) return null
        return (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8) or
            ((b[2].toInt() and 0xFF) shl 16) or ((b[3].toInt() and 0xFF) shl 24)
    }

    private fun readFullyDeadline(
        socket: LocalSocket,
        input: InputStream,
        buf: ByteArray,
        len: Int,
        deadline: Long,
    ): Int? {
        var off = 0
        while (off < len) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) return null
            socket.soTimeout = minOf(remaining, 1_000L).toInt()
            val n = try {
                input.read(buf, off, len - off)
            } catch (_: java.net.SocketTimeoutException) {
                continue
            } catch (_: IOException) {
                return null
            }
            if (n < 0) return null
            off += n
        }
        return off
    }
}

class LocalSocketProcess(private val socket: LocalSocket) : Process() {
    @Volatile
    private var destroyed = false

    override fun getOutputStream(): OutputStream = socket.outputStream

    override fun getInputStream(): InputStream = socket.inputStream

    /** Empty stderr: it is merged into [getInputStream] at the PTY layer. MUST NOT return the same socket
     *  stream - two readers would split the byte stream (interleaving / lost lines). */
    override fun getErrorStream(): InputStream = EmptyInputStream

    override fun waitFor(): Int {
        while (!destroyed) {
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        return 0
    }

    override fun exitValue(): Int =
        if (destroyed) 0 else throw IllegalThreadStateException("process not exited")

    override fun destroy() {
        destroyed = true
        runCatching { socket.close() }
    }
}
