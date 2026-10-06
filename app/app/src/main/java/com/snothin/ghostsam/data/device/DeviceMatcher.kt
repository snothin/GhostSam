package com.snothin.ghostsam.data.device

/** Device -> series matcher (the app's only coarse match). Dual keys: Build.DEVICE then Build.MODEL;
 *  both hit but different series = fail-closed reject. KMI guard compares the series KMI with uname -r. */
internal enum class SeriesMatchKind { EXACT, CODENAME, MODEL, CONFLICT, NONE }

internal data class SeriesMatch(
    val series: SeriesTable.Series?,
    val kind: SeriesMatchKind,
    val codename: String,
    val model: String,
    val codenameSeries: SeriesTable.Series?,
    val modelSeries: SeriesTable.Series?,
    val kernelMajorMinor: String,
    val kmiOk: Boolean,
) {
    val id: String get() = series?.id ?: "?"

    fun evidence(): String = buildString {
        append("codename=").append(codename.ifBlank { "?" })
        append('→').append(codenameSeries?.id ?: "?")
        append(" · model=").append(model.ifBlank { "?" })
        append('→').append(modelSeries?.id ?: "?")
        append(" · kernel ").append(kernelMajorMinor.ifBlank { "?" })
        when (kind) {
            SeriesMatchKind.EXACT -> append(" ✓")
            SeriesMatchKind.CODENAME -> append(" (codename only)")
            SeriesMatchKind.MODEL -> append(" (model only, codename unmatched)")
            SeriesMatchKind.CONFLICT -> append(" ⚠ keys conflict")
            SeriesMatchKind.NONE -> append(" ✗ unmatched")
        }
        if (series != null && !kmiOk) append(" · ⚠ KMI mismatch (expected ").append(series.kmi).append(')')
    }
}

internal object DeviceMatcher {

    fun match(snapshot: DeviceSnapshot): SeriesMatch {
        val codename = snapshot.device.trim()
        val model = snapshot.model.trim()
        val byCode = SeriesTable.BY_CODENAME[codename]
        val byModel = SeriesTable.BY_MODEL[model]
        val kind = when {
            byCode != null && byModel != null -> if (byCode.id == byModel.id) SeriesMatchKind.EXACT else SeriesMatchKind.CONFLICT
            byCode != null -> SeriesMatchKind.CODENAME
            byModel != null -> SeriesMatchKind.MODEL
            else -> SeriesMatchKind.NONE
        }
        val series = byCode ?: byModel
        val kernel = kernelMajorMinor(snapshot.kernelVersion)
        val kmiOk = series == null || kernel.isBlank() || kernel == expectedMajorMinor(series.kmi)
        return SeriesMatch(
            series = series,
            kind = kind,
            codename = codename,
            model = model,
            codenameSeries = byCode,
            modelSeries = byModel,
            kernelMajorMinor = kernel,
            kmiOk = kmiOk,
        )
    }

    fun isBuildListed(series: SeriesTable.Series, build: String): Boolean =
        build.isNotBlank() && build in series.builds

    private val RE_KERNEL_RELEASE = Regex("^(\\d+)\\.(\\d+)")
    private val RE_KMI_VERSION = Regex("(\\d+\\.\\d+)")

    fun kernelMajorMinor(kernelVersion: String): String {
        val release = kernelVersion.trim().substringBefore(' ')
        val m = RE_KERNEL_RELEASE.find(release) ?: return ""
        return "${m.groupValues[1]}.${m.groupValues[2]}"
    }

    fun expectedMajorMinor(kmi: String): String =
        RE_KMI_VERSION.find(kmi)?.groupValues?.get(1) ?: kmi
}
