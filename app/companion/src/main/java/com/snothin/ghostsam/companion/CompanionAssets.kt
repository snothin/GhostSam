package com.snothin.ghostsam.companion

import android.content.Context
import android.content.pm.PackageManager
import android.system.Os
import java.io.File
import java.util.zip.ZipFile

object CompanionAssets {
    const val DIR = "/data/system/ghostsam"

    fun lkmPath(kmi: String): String = "$DIR/dfr_lkm-$kmi.ko"

    fun currentKmi(): String? {
        val procVersion = runCatching { File("/proc/version").readText() }.getOrDefault("")
        parseKmi(procVersion)?.let { return it }
        val release = runCatching { File("/proc/sys/kernel/osrelease").readText() }
            .getOrDefault("")
        parseKmi(release)?.let { return it }
        parseKmi(System.getProperty("os.version").orEmpty())?.let { return it }
        val direct = runCatching { GsdfrNative.kmi().trim() }.getOrDefault("")
        if (direct.isNotEmpty())
            return direct
        return runCatching { kmiFromText(GsdfrNative.probe()) }.getOrNull()
    }

    fun kmiFromText(text: String): String? =
        Regex("""kmi=([A-Za-z0-9._-]+)""").find(text)?.groupValues?.get(1)
            ?.takeIf { it.isNotEmpty() && it != "unknown" }

    internal fun parseKmi(release: String): String? {
        val ver = Regex("""(\d+)\.(\d+)""").find(release) ?: return null
        val gen = Regex("""android(\d+)""").find(release) ?: return null
        val kmi = "android${gen.groupValues[1]}-${ver.groupValues[1]}.${ver.groupValues[2]}"
        return kmi.takeIf { it.length in 8..48 }
    }

    data class KsudSource(val apk: String?, val entry: String?)

    fun stageAll(
        context: Context,
        kmiOverride: String? = null,
        ksudSource: KsudSource? = null,
        debug: Boolean = false,
    ): String {
        val sb = StringBuilder()
        val dir = File(DIR)
        if (!dir.isDirectory && !dir.mkdirs()) {
            return "[x] $DIR not creatable\n"
        }

        sb.append(stageKsud(context, ksudSource, debug))

        val kmi = kmiOverride ?: currentKmi()
        if (kmi == null) {
            sb.append("[!] kmi unknown; dfr_lkm not staged code=kmi_unknown\n")
            return sb.toString()
        }
        sb.append("kmi=$kmi\n")
        val ko = File(dir, "dfr_lkm-$kmi.ko")
        sb.append(stageAsset(context, "dfr/dfr_lkm-$kmi.ko", ko, 0b110_100_100))
        return sb.toString()
    }

    private fun stageKsud(context: Context, source: KsudSource?, debug: Boolean): String {
        val dst = File(DIR, "ksud")
        val entry = source?.entry?.takeIf { it.isNotBlank() } ?: DEFAULT_KSUD_ENTRY
        val tried = ArrayList<String>()
        fun dbg(srcDesc: String) = if (debug) "[d] ksud src=$srcDesc entry=$entry\n" else ""

        source?.apk?.takeIf { it.isNotBlank() }?.let { apk ->
            tried += "param"
            extractKsud(apk, entry, dst)?.let { return dbg("apk:$apk") + it }
        }
        runCatching {
            context.packageManager.getApplicationInfo(
                MAIN_PKG,
                PackageManager.ApplicationInfoFlags.of(0),
            )
        }.getOrNull()?.sourceDir?.takeIf { it.isNotBlank() }?.let { apk ->
            tried += "pm"
            extractKsud(apk, entry, dst)?.let { return dbg("pm:$apk") + it }
        }
        return "[x] ksud extract failed (tried: ${tried.joinToString("/")}); update main app and retry code=ksud_missing\n"
    }

    private fun extractKsud(apk: String, entry: String, dst: File): String? = try {
        ZipFile(apk).use { zip ->
            val e = zip.getEntry(entry) ?: error("entry not found: $entry")
            zip.getInputStream(e).use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            }
        }
        Os.chmod(dst.absolutePath, 0b111_000_000)
        if (dst.length() > 0) {
            "[+] staged ${dst.absolutePath} (${dst.length()} B; src=apk:$entry)\n"
        } else {
            dst.delete()
            null
        }
    } catch (t: Throwable) {
        dst.delete()
        null
    }

    private const val MAIN_PKG = "com.snothin.ghostsam"
    private const val DEFAULT_KSUD_ENTRY = "assets/payloads/ksud/ksud"

    private fun stageAsset(context: Context, asset: String, dst: File, mode: Int): String = try {
        context.assets.open(asset).use { input ->
            dst.outputStream().use { output -> input.copyTo(output) }
        }
        Os.chmod(dst.absolutePath, mode)
        "[+] staged ${dst.absolutePath} (${dst.length()} B)\n"
    } catch (t: Throwable) {
        "[!] stage $asset failed: ${t.javaClass.simpleName}: ${t.message}\n"
    }
}
