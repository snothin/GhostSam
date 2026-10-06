package com.snothin.ghostsam.data.prefs

import android.content.Context
import android.content.SharedPreferences

/** Persist (companion) state record: last trigger / host info; no event callback yet - stores
 *  trigger-accepted + host facts from bind. */
object PersistPrefs {
    private const val PREFS_NAME = "persist_state"

    private const val LAST_TRIGGER_AT = "last_trigger_at"
    private const val LAST_TRIGGER_RESULT = "last_trigger_result"
    private const val LAST_RUN_AT = "last_run_at"
    private const val LAST_RUN_SUMMARY = "last_run_summary"
    private const val HOST_UID = "host_uid"
    private const val HOST_PID = "host_pid"
    private const val HOST_PROC = "host_proc"
    private const val HOST_STARTS = "host_starts"
    private const val INFO_AT = "info_at"

    const val RESULT_OK = "ok"

    data class Host(val uid: Int, val pid: Int, val proc: String, val starts: Int, val at: Long)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun lastTriggerAt(context: Context): Long =
        prefs(context).getLong(LAST_TRIGGER_AT, 0L)

    fun lastTriggerResult(context: Context): String? =
        prefs(context).getString(LAST_TRIGGER_RESULT, null)

    fun setLastTrigger(context: Context, at: Long, result: String) {
        prefs(context).edit()
            .putLong(LAST_TRIGGER_AT, at)
            .putString(LAST_TRIGGER_RESULT, result)
            .apply()
    }

    fun lastRunAt(context: Context): Long = prefs(context).getLong(LAST_RUN_AT, 0L)

    fun lastRunSummary(context: Context): String? =
        prefs(context).getString(LAST_RUN_SUMMARY, null)

    fun setLastRun(context: Context, at: Long, summary: String) {
        prefs(context).edit()
            .putLong(LAST_RUN_AT, at)
            .putString(LAST_RUN_SUMMARY, summary)
            .apply()
    }

    fun host(context: Context): Host? {
        val p = prefs(context)
        if (!p.contains(HOST_PID)) return null
        return Host(
            uid = p.getInt(HOST_UID, -1),
            pid = p.getInt(HOST_PID, -1),
            proc = p.getString(HOST_PROC, "").orEmpty(),
            starts = p.getInt(HOST_STARTS, 0),
            at = p.getLong(INFO_AT, 0L),
        )
    }

    fun setHost(context: Context, uid: Int, pid: Int, proc: String, starts: Int, at: Long) {
        prefs(context).edit()
            .putInt(HOST_UID, uid)
            .putInt(HOST_PID, pid)
            .putString(HOST_PROC, proc)
            .putInt(HOST_STARTS, starts)
            .putLong(INFO_AT, at)
            .apply()
    }
}
