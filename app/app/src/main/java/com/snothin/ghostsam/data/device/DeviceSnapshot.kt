package com.snothin.ghostsam.data.device

import android.os.Build
import android.system.Os

data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val device: String,
    val incremental: String,
    val kernelRelease: String,
    val kernelVersion: String,
    val fingerprint: String,
    val abi: String,
) {
    companion object {
        fun current(): DeviceSnapshot {
            val uname = Os.uname()
            return DeviceSnapshot(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                device = Build.DEVICE,
                incremental = Build.VERSION.INCREMENTAL,
                kernelRelease = uname.release,
                kernelVersion = listOf(uname.release, uname.version, uname.machine)
                    .filter(String::isNotBlank)
                    .joinToString(" "),
                fingerprint = Build.FINGERPRINT,
                abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            )
        }

        /** Firmware version (shown as "baseband"): ro.boot.bootloader is the clean single value
         *  (gsm.version.baseband duplicates on dual-SIM). */
        fun readBaseband(): String = runCatching {
            val bootloader = readProp("ro.boot.bootloader")
            if (bootloader.isNotEmpty()) return@runCatching bootloader
            readProp("gsm.version.baseband").substringBefore(',').trim()
        }.getOrDefault("")

        /** One UI version ("9.0"; "" on non-Samsung). Source: ro.build.version.oneui integer encoding
         *  (40100 -> 4.1, 90000 -> 9.0). */
        fun readOneUiVersion(): String = runCatching {
            val raw = readProp("ro.build.version.oneui").toLongOrNull()
                ?: return@runCatching ""
            if (raw < 10000) return@runCatching ""
            val major = raw / 10000
            val minorField = (raw / 100) % 100
            val minor: Long
            val patch: Long
            if (minorField >= 10) {
                minor = minorField / 10
                patch = minorField % 10
            } else {
                minor = minorField
                patch = (raw / 10) % 10
            }
            buildString {
                append(major).append('.').append(minor)
                if (patch > 0) append('.').append(patch)
            }
        }.getOrDefault("")

        private fun readProp(key: String): String = runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            process.inputStream.bufferedReader().use { it.readText() }.trim().also {
                process.waitFor()
                if (process.isAlive) process.destroy()
            }
        }.getOrDefault("")
    }
}
