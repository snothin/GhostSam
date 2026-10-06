package com.snothin.ghostsam.data.channel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/** Anti-pileup wrapper for blocking calls: the whole call (incl. uninterruptible libadb open) runs
 *  in a detached coroutine waited on for [waitMs]; orphans are capped by [maxInflight]. */
internal class StallGuard(
    private val scope: CoroutineScope,
    private val waitMs: Long,
    private val maxInflight: Int,
    private val onStalled: (() -> Unit)? = null,
) {
    private val lock = Any()
    private val inflight = mutableListOf<Deferred<String>>()

    suspend fun guarded(block: suspend () -> String): String {
        val job = synchronized(lock) {
            inflight.removeAll { it.isCompleted }
            if (inflight.size >= maxInflight) {
                null
            } else {
                scope.async(Dispatchers.IO) { runCatching { block() }.getOrDefault("") }
                    .also { inflight += it }
            }
        }
        if (job == null) {
            onStalled?.invoke()
            return ""
        }
        val value = withTimeoutOrNull(waitMs) { job.await() }
        if (value == null) onStalled?.invoke()
        return value ?: ""
    }
}
