package com.snothin.ghostsam.data.device

import android.content.Context
import com.snothin.ghostsam.BuildConfig

/** Paste-ready device report for adaptation issues (#8 LAVA, #4 vivo, #9 Fold7): the maintainer
 *  needs codename / build / kernel / match evidence, and reporters never know the field names.
 *  Markdown table, no secrets, no paths. */
object DeviceReport {

    fun build(context: Context): String {
        val s = DeviceSnapshot.current()
        val match = DeviceMatcher.match(s)
        return buildString {
            appendLine("### Device report (GhostSam v${BuildConfig.VERSION_NAME}, build ${BuildConfig.VERSION_CODE})")
            appendLine()
            appendLine("| field | value |")
            appendLine("| --- | --- |")
            appendLine("| manufacturer | ${s.manufacturer} |")
            appendLine("| model | ${s.model} |")
            appendLine("| codename | `${s.device}` |")
            appendLine("| build | `${s.incremental}` |")
            appendLine("| fingerprint | `${s.fingerprint}` |")
            appendLine("| kernel | `${s.kernelRelease}` |")
            appendLine("| uname | `${s.kernelVersion}` |")
            appendLine("| abi | ${s.abi} |")
            appendLine("| One UI | ${DeviceSnapshot.readOneUiVersion().ifEmpty { "?" }} |")
            appendLine("| baseband | ${DeviceSnapshot.readBaseband().ifEmpty { "?" }} |")
            appendLine("| series match | ${match.id} (${match.kind}, kmiOk=${match.kmiOk}) |")
            appendLine("| match evidence | ${match.evidence()} |")
        }
    }
}
