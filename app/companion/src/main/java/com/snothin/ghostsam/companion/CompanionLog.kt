package com.snothin.ghostsam.companion

internal object CompanionLog {

    enum class Level { INFO, WARN, ERROR }

    data class Verdict(val word: String, val level: Level, val tail: String)

    fun verdict(result: String): Verdict {
        val x = result.lineSequence().firstOrNull { it.startsWith("[x]") }
        val w = if (x == null) {
            result.lineSequence().firstOrNull {
                it.startsWith("[!]") && (it.contains("code=") || it.contains("failed"))
            }
        } else {
            null
        }
        val tail = (x ?: w)?.let { ": $it" }.orEmpty()
        return when {
            x != null -> Verdict("fail", Level.ERROR, tail)
            w != null -> Verdict("warn", Level.WARN, tail)
            else -> Verdict("ok", Level.INFO, tail)
        }
    }

    fun levelOfLine(line: String): Level = when {
        line.startsWith("[x]") -> Level.ERROR
        line.startsWith("[!]") -> Level.WARN
        else -> Level.INFO
    }

    fun fmtDuration(ms: Long): String = if (ms < 10_000) "${ms}ms" else "${ms / 1000}s"
}
