package com.snothin.ghostsam.data.device

/** Kernel-window classification (DirtyFrag reachability): android14-6.1 is facade-dead since v6.1.72
 *  (<6.1.72 / 5.10 / >=6.6 reachable). Unknown = parse failure = fail-open (log only). */
internal enum class KernelWindow(val label: String) {
    Legacy("legacy"),

    Dead("dead"),

    Predicted("predicted"),

    Modern("modern"),

    Unknown("unknown"),
}

internal object KernelWindowGate {
    private const val FACADE_PATCH = 72

    private val VERSION = Regex("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?")

    fun classify(release: String): KernelWindow {
        val m = VERSION.find(release.trim()) ?: return KernelWindow.Unknown
        val maj = m.groupValues[1].toIntOrNull() ?: return KernelWindow.Unknown
        val min = m.groupValues[2].toIntOrNull() ?: return KernelWindow.Unknown
        val patch = m.groupValues[3].toIntOrNull() ?: 0
        return when {
            maj == 6 && min == 1 ->
                if (patch < FACADE_PATCH) KernelWindow.Legacy else KernelWindow.Dead
            maj == 5 && min == 10 -> KernelWindow.Legacy
            maj == 5 && min == 15 -> KernelWindow.Predicted
            maj > 6 || (maj == 6 && min >= 6) -> KernelWindow.Modern
            else -> KernelWindow.Unknown
        }
    }
}
