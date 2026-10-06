package com.snothin.ghostsam.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import com.snothin.ghostsam.data.channel.ExecutionMode
import com.snothin.ghostsam.data.exploit.PayloadScheme

object SettingsPrefs {
    private const val PREFS_NAME = "settings"

    private const val LOCAL_PAYLOAD = "local_payload"
    private const val UPTIME_GATE_ENABLED = "uptime_gate_enabled"
    private const val UPTIME_GATE_SECONDS = "uptime_gate_seconds"
    private const val USE_SHIZUKU = "use_shizuku"
    private const val EXECUTION_MODE = "execution_mode"
    private const val PAYLOAD_SCHEME = "payload_scheme"
    private const val KSUD_VARIANT = "ksud_variant"
    private const val PIN_PROBE = "pin_probe"
    private const val SKIP_KSU = "skip_ksu"
    private const val TCP_PERSIST = "tcp_persist"
    private const val TCP_PORT = "tcp_port"
    private const val ADB_PAIRED = "adb_paired"

    val localPayloadFlow = MutableStateFlow(false)

    val executionModeFlow = MutableStateFlow(ExecutionMode.Adb)

    val tcpPersistFlow = MutableStateFlow(false)

    val payloadSchemeFlow = MutableStateFlow(PayloadScheme.Default)

    val ksudVariantFlow = MutableStateFlow(KsudVariant.Default)

    val pinProbeFlow = MutableStateFlow(false)

    const val DEFAULT_UPTIME_GATE_SECONDS = 60
    const val UPTIME_GATE_MIN_SECONDS = 60
    const val UPTIME_GATE_MAX_SECONDS = 600

    fun localPayload(context: Context): Boolean =
        prefs(context).getBoolean(LOCAL_PAYLOAD, false)

    fun setLocalPayload(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(LOCAL_PAYLOAD, value).apply()
        localPayloadFlow.value = value
    }

    fun uptimeGateEnabled(context: Context): Boolean =
        prefs(context).getBoolean(UPTIME_GATE_ENABLED, true)

    fun setUptimeGateEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(UPTIME_GATE_ENABLED, value).apply()
    }

/** Execution mode (default Adb). Migration: without `execution_mode` fall back to legacy
 *  `use_shizuku`; "local" was removed (falls back to Adb). */
    fun executionMode(context: Context): ExecutionMode {
        val stored = prefs(context)
        stored.getString(EXECUTION_MODE, null)?.let { return ExecutionMode.parse(it) }
        return if (stored.getBoolean(USE_SHIZUKU, false)) ExecutionMode.Shizuku else ExecutionMode.Adb
    }

    fun setExecutionMode(context: Context, value: ExecutionMode) {
        prefs(context).edit()
            .putString(EXECUTION_MODE, value.name)
            .putBoolean(USE_SHIZUKU, value == ExecutionMode.Shizuku)
            .apply()
        executionModeFlow.value = value
    }

    fun payloadScheme(context: Context): PayloadScheme =
        PayloadScheme.parse(prefs(context).getString(PAYLOAD_SCHEME, null))

    fun setPayloadScheme(context: Context, value: PayloadScheme) {
        prefs(context).edit().putString(PAYLOAD_SCHEME, value.name).apply()
        payloadSchemeFlow.value = value
    }

    fun ksudVariant(context: Context): KsudVariant =
        KsudVariant.parse(prefs(context).getString(KSUD_VARIANT, null))

    fun setKsudVariant(context: Context, value: KsudVariant) {
        prefs(context).edit().putString(KSUD_VARIANT, value.name).apply()
        ksudVariantFlow.value = value
    }

    fun pinProbe(context: Context): Boolean =
        prefs(context).getBoolean(PIN_PROBE, false)

    fun setPinProbe(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(PIN_PROBE, value).apply()
        pinProbeFlow.value = value
    }

/** Effective KSU variant (single resolution point): only DfrStandalone may switch; other schemes
 *  lock Default (the stored value is never rewritten). */
    fun effectiveKsudVariant(context: Context): KsudVariant =
        if (payloadScheme(context) == PayloadScheme.DfrStandalone) ksudVariant(context) else KsudVariant.Default

    fun tcpPersist(context: Context): Boolean =
        prefs(context).getBoolean(TCP_PERSIST, false)

    fun setTcpPersist(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(TCP_PERSIST, value).apply()
        tcpPersistFlow.value = value
    }

    const val TCP_PORT_DEFAULT = 5555
    const val TCP_PORT_MIN = 1024
    const val TCP_PORT_MAX = 65535

    fun tcpPort(context: Context): Int =
        prefs(context).getInt(TCP_PORT, TCP_PORT_DEFAULT).coerceIn(TCP_PORT_MIN, TCP_PORT_MAX)

    fun setTcpPort(context: Context, value: Int) {
        prefs(context).edit().putInt(TCP_PORT, value.coerceIn(TCP_PORT_MIN, TCP_PORT_MAX)).apply()
    }

    fun adbPaired(context: Context): Boolean =
        prefs(context).getBoolean(ADB_PAIRED, false)

    fun setAdbPaired(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(ADB_PAIRED, value).apply()
    }

    /** Skip-KernelSU toggle (no-ksu mode): under DFR it is neither displayed nor used (the basic
     *  chain is always no-ksu); the stored value only represents the Default route's choice. */
    fun skipKsu(context: Context): Boolean =
        prefs(context).getBoolean(SKIP_KSU, false)

    fun setSkipKsu(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(SKIP_KSU, value).apply()
    }

    fun uptimeGateSeconds(context: Context): Int =
        prefs(context).getInt(UPTIME_GATE_SECONDS, DEFAULT_UPTIME_GATE_SECONDS)
            .coerceIn(UPTIME_GATE_MIN_SECONDS, UPTIME_GATE_MAX_SECONDS)

    fun setUptimeGateSeconds(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(UPTIME_GATE_SECONDS, value.coerceIn(UPTIME_GATE_MIN_SECONDS, UPTIME_GATE_MAX_SECONDS))
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
