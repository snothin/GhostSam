package com.snothin.ghostsam.data.channel

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** su_daemon channel (temp-root socket): local direct connect, usable in permissive sessions
 *  (no-ksu or late-load residue); unavailable after enforcing / success. */
object SuDaemonChannel {

    private const val CACHE_TTL_OK_MILLIS = Long.MAX_VALUE

    private const val CACHE_TTL_FAIL_MILLIS = 10_000L

    @Volatile
    private var cachedAvailable: Boolean? = null

    @Volatile
    private var cachedAtMillis = 0L

    private val probeLock = Any()

    fun invalidateCache() {
        cachedAvailable = null
    }

    fun isAvailable(): Boolean {
        if (isFresh()) return cachedAvailable == true
        synchronized(probeLock) {
            if (isFresh()) return cachedAvailable == true
            val result = runCatching { LocalSuDaemon.isAvailable() }.getOrDefault(false)
            cachedAvailable = result
            cachedAtMillis = SystemClock.elapsedRealtime()
            return result
        }
    }

    private fun isFresh(): Boolean {
        val cached = cachedAvailable ?: return false
        val ttl = if (cached) CACHE_TTL_OK_MILLIS else CACHE_TTL_FAIL_MILLIS
        return SystemClock.elapsedRealtime() - cachedAtMillis < ttl
    }

    suspend fun execPrivileged(command: String, timeoutMillis: Long = 20_000): String? =
        withContext(Dispatchers.IO) {
            if (!isAvailable()) return@withContext null
            val out = LocalSuDaemon.execCommand(command, timeoutMillis)
            if (out == null) {
                invalidateCache()
            }
            out
        }

    suspend fun execLateLoad(
        timeoutMillis: Long = 60_000,
        onChunk: (String) -> Unit = {},
    ): String? = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext null
        val result = LocalSuDaemon.lateLoad(timeoutMillis, onChunk)
        if (result == null) {
            invalidateCache()
            null
        } else {
            result.first.trim()
        }
    }

    fun spawnShell(): Process? = runCatching {
        if (!isAvailable()) return null
        val socket = LocalSuDaemon.spawn() ?: return null
        LocalSocketProcess(socket)
    }.getOrNull()
}
