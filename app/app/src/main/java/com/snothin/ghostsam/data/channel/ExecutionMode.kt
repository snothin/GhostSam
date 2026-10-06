package com.snothin.ghostsam.data.channel

/** How the payload runs on-device: Adb = built-in wireless-debug bridge (default), Shizuku = Shizuku channel;
 *  both act as shell (uid 2000). Persisted under `execution_mode`. */
enum class ExecutionMode {
    Adb,
    Shizuku;

    companion object {
        fun parse(raw: String?): ExecutionMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Adb
    }
}
