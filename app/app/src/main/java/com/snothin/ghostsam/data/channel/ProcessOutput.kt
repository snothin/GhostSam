package com.snothin.ghostsam.data.channel

import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal object EmptyInputStream : InputStream() {
    override fun read(): Int = -1
}

/** Timeout sentinel, produced and consumed app-side (shared by all channels). */
internal const val TIMEOUT_MARKER = "(timeout)"

internal const val NO_OUTPUT_MARKER = "(exit, no output)"

/** Channel/exec error prefix. A command's own "Error" output (am/pm etc.) is NOT a channel error. */
internal const val CHANNEL_ERROR_PREFIX = "ERROR"

internal fun isChannelFailure(output: String): Boolean =
    output.startsWith(CHANNEL_ERROR_PREFIX) || output == TIMEOUT_MARKER

internal fun channelError(error: Throwable): String =
    "$CHANNEL_ERROR_PREFIX: ${error.message}"

private const val MAX_STDOUT_BYTES = 1024 * 1024

private const val MAX_STDERR_BYTES = 256 * 1024

internal const val TRUNCATED_MARKER = "[output truncated]"

/** Collect process output: two threads drain stdout/stderr with caps, then one UTF-8 decode;
 *  completion = stdout EOF (join with timeout); timeout destroys the process and returns
 *  [TIMEOUT_MARKER]; empty output returns [NO_OUTPUT_MARKER]; IO thread only. */
internal fun collectProcessOutput(process: Process, timeoutMillis: Long): String {
    val outBuf = ByteArrayOutputStream()
    val errBuf = ByteArrayOutputStream()
    val outThread = Thread({
        runCatching { drainBounded(process.inputStream, outBuf, MAX_STDOUT_BYTES) }
    }, "cmd-stdout").apply { isDaemon = true }
    val errThread = Thread({
        runCatching { drainBounded(process.errorStream, errBuf, MAX_STDERR_BYTES) }
    }, "cmd-stderr").apply { isDaemon = true }
    outThread.start()
    errThread.start()

    outThread.join(timeoutMillis)
    if (outThread.isAlive) {
        process.destroy()
        outThread.join(500)
        errThread.join(500)
        return TIMEOUT_MARKER
    }
    errThread.join(500)
    if (errThread.isAlive) {
        process.destroy()
        errThread.join(500)
    }

    val outTruncated = outBuf.size() >= MAX_STDOUT_BYTES
    val out = outBuf.toByteArray().toString(Charsets.UTF_8).trim()
    val err = errBuf.toByteArray().toString(Charsets.UTF_8).trim()
    return buildString {
        if (out.isNotEmpty()) {
            append(out)
            if (outTruncated) append('\n').append(TRUNCATED_MARKER)
        }
        if (err.isNotEmpty()) {
            if (isNotEmpty()) append("\n")
            append("[stderr]\n").append(err)
        }
        if (isEmpty()) append(NO_OUTPUT_MARKER)
    }
}

private fun drainBounded(input: InputStream, sink: ByteArrayOutputStream, limit: Int) {
    val buf = ByteArray(0x2000)
    while (true) {
        val n = input.read(buf)
        if (n <= 0) break
        if (sink.size() < limit) sink.write(buf, 0, n)
    }
}

internal suspend fun collectProcessOutputCancellable(process: Process, timeoutMillis: Long): String =
    suspendCancellableCoroutine { cont ->
        Thread({
            val result = collectProcessOutput(process, timeoutMillis)
            if (cont.isActive) cont.resume(result)
        }, "cmd-collect").apply {
            isDaemon = true
            start()
        }
        cont.invokeOnCancellation { runCatching { process.destroy() } }
    }
