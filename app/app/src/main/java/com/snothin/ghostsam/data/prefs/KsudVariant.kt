package com.snothin.ghostsam.data.prefs

/** KSU kernel-side variant (standalone route only): the chain re-delivers ksud every run; an
 *  already loaded variant is not replaced - switching needs a reboot. Effective value resolves
 *  at a single point (SettingsPrefs.effectiveKsudVariant). Key: ksud_variant. */
enum class KsudVariant {
    Default,
    Next;

    val label: String
        get() = when (this) {
            Default -> "KernelSU"
            Next -> "KernelSU-Next"
        }

    companion object {
        fun parse(raw: String?): KsudVariant =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Default
    }
}
