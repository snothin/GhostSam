package com.snothin.ghostsam.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.snothin.ghostsam.data.prefs.ExploitPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Companion client: [state] install state / uid / version; [trigger] same-signature startService
 *  straight to the system component (no root); [fetchInfo] binder host info (uid/pid/process).
 *  Trigger success only means startService was accepted - evidence via fetchInfo / GhostSamCompanion log. */
object CompanionClient {
    const val COMPANION_PKG = "com.snothin.ghostsam.companion"
    private const val SERVICE_CLASS = "$COMPANION_PKG.CompanionService"

    private const val MSG_GET_INFO = 1
    private const val MSG_INFO = 2
    private const val MSG_CMD = 3
    private const val MSG_RESULT = 4
    private const val MSG_PROGRESS = 5
    private const val MSG_PROGRESS_DATA = 6
    private const val KEY_CMD = "cmd"
    private const val KEY_RESULT = "result"
    private const val KEY_UID = "uid"
    private const val KEY_PID = "pid"
    private const val KEY_PROC = "proc"
    private const val KEY_STARTS = "starts"
    private const val KEY_LAST_START_AT = "last_start_at"
    private const val KEY_NATIVE = "native"
    private const val KEY_COMPANION = "companion"
    private const val KEY_APK = "apk"
    private const val KEY_ENTRY = "entry"
    private const val KEY_DEBUG = "dbg"

    data class State(val installed: Boolean, val uid: Int?, val versionName: String?, val versionCode: Long? = null)

    data class Info(
        val uid: Int,
        val pid: Int,
        val proc: String,
        val starts: Int,
        val lastStartAt: Long,
    )

    data class Progress(val companion: String, val native: String)

    fun state(context: Context): State = try {
        val info = context.packageManager.getPackageInfo(
            COMPANION_PKG,
            PackageManager.PackageInfoFlags.of(0),
        )
        State(true, info.applicationInfo?.uid, info.versionName, info.longVersionCode)
    } catch (_: PackageManager.NameNotFoundException) {
        State(false, null, null)
    }

    const val KSUD_ASSET_ENTRY = "assets/payloads/ksud/ksud"

    fun triggerExtras(context: Context): Map<String, String> = mapOf(
        KEY_APK to context.applicationInfo.sourceDir,
        KEY_ENTRY to KSUD_ASSET_ENTRY,
        KEY_DEBUG to if (ExploitPrefs.debugEnabled(context)) "1" else "0",
    )

    fun trigger(context: Context): String? = try {
        context.startService(Intent().setComponent(ComponentName(COMPANION_PKG, SERVICE_CLASS)))
        null
    } catch (t: Throwable) {
        "${t.javaClass.simpleName}: ${t.message.orEmpty()}"
    }

    suspend fun fetchInfo(context: Context, timeoutMs: Long = 1500L): Info? =
        withContext(Dispatchers.IO) {
            val latch = CountDownLatch(1)
            var result: Info? = null
            val reply = Messenger(object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    if (msg.what == MSG_INFO) {
                        val d = msg.data
                        result = Info(
                            d.getInt(KEY_UID),
                            d.getInt(KEY_PID),
                            d.getString(KEY_PROC).orEmpty(),
                            d.getInt(KEY_STARTS),
                            d.getLong(KEY_LAST_START_AT),
                        )
                    }
                    latch.countDown()
                }
            })
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (binder == null) {
                        latch.countDown()
                        return
                    }
                    runCatching {
                        Messenger(binder).send(
                            Message.obtain(null, MSG_GET_INFO).apply { replyTo = reply },
                        )
                    }.onFailure { latch.countDown() }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    latch.countDown()
                }
            }
            val bound = runCatching {
                context.bindService(
                    Intent().setComponent(ComponentName(COMPANION_PKG, SERVICE_CLASS)),
                    conn,
                    0,
                )
            }.getOrDefault(false)
            try {
                if (!bound) return@withContext null
                if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) null else result
            } finally {
                runCatching { context.unbindService(conn) }
            }
        }

    suspend fun progress(context: Context, timeoutMs: Long = 2_000L): Progress? =
        withContext(Dispatchers.IO) {
            val latch = CountDownLatch(1)
            var result: Progress? = null
            val reply = Messenger(object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    if (msg.what == MSG_PROGRESS_DATA) {
                        val d = msg.data
                        result = Progress(
                            d.getString(KEY_COMPANION).orEmpty(),
                            d.getString(KEY_NATIVE).orEmpty(),
                        )
                    }
                    latch.countDown()
                }
            })
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (binder == null) {
                        latch.countDown()
                        return
                    }
                    runCatching {
                        Messenger(binder).send(
                            Message.obtain(null, MSG_PROGRESS).apply { replyTo = reply },
                        )
                    }.onFailure { latch.countDown() }
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }
            val bound = runCatching {
                context.bindService(
                    Intent().setComponent(ComponentName(COMPANION_PKG, SERVICE_CLASS)),
                    conn,
                    Context.BIND_AUTO_CREATE,
                )
            }.getOrDefault(false)
            try {
                if (!bound) return@withContext null
                if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) null else result
            } finally {
                runCatching { context.unbindService(conn) }
            }
        }

    suspend fun request(
        context: Context,
        cmd: String,
        timeoutMs: Long = 15000L,
        extras: Map<String, String> = emptyMap(),
    ): String? =
        withContext(Dispatchers.IO) {
            val latch = CountDownLatch(1)
            var result: String? = null
            val reply = Messenger(object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    if (msg.what == MSG_RESULT)
                        result = msg.data?.getString(KEY_RESULT)
                    latch.countDown()
                }
            })
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (binder == null) {
                        latch.countDown()
                        return
                    }
                    runCatching {
                        Messenger(binder).send(
                            Message.obtain(null, MSG_CMD).apply {
                                data = android.os.Bundle().apply {
                                    putString(KEY_CMD, cmd)
                                    extras.forEach { (k, v) -> putString(k, v) }
                                }
                                replyTo = reply
                            },
                        )
                    }.onFailure { latch.countDown() }
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }
            val bound = runCatching {
                context.bindService(
                    Intent().setComponent(ComponentName(COMPANION_PKG, SERVICE_CLASS)),
                    conn,
                    Context.BIND_AUTO_CREATE,
                )
            }.getOrDefault(false)
            try {
                if (!bound) return@withContext null
                if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) null else result
            } finally {
                runCatching { context.unbindService(conn) }
            }
        }
}
