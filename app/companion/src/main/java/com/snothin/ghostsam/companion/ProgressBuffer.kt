package com.snothin.ghostsam.companion

internal class ProgressBuffer {
    private val sb = StringBuilder()

    @Synchronized
    fun append(s: String) {
        sb.append(s)
    }

    @Synchronized
    fun snapshot(): String = sb.toString()

    @Synchronized
    fun clear() {
        sb.setLength(0)
    }
}
