package com.snothin.ghostsam.companion

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Parcel
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CompanionService : Service() {

    private val worker = Executors.newSingleThreadExecutor()

    private val progressWorker = Executors.newSingleThreadExecutor()

    private val persistLog = ProgressBuffer()

    @Volatile
    private var controller: IBinder? = null

    @Volatile
    private var hopNonce: String? = null

    private val controllerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val nonce = intent.getStringExtra(StageReceiver.EXTRA_HOP_NONCE)
            if (nonce == null || nonce != hopNonce) {
                Log.w(TAG, "controller rejected (nonce mismatch)")
                return
            }
            controller = intent.extras?.getBinder(StageReceiver.EXTRA_CONTROLLER)
            Log.i(TAG, "controller received")
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(controllerReceiver, IntentFilter(StageReceiver.EVIL_ACTION), Context.RECEIVER_EXPORTED)
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        starts += 1
        lastStartAt = System.currentTimeMillis()
        Log.i(TAG, "alive uid=${Process.myUid()} pid=${Process.myPid()} starts=$starts")
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(controllerReceiver) }
        worker.shutdownNow()
        progressWorker.shutdownNow()
        Log.i(TAG, "destroyed uid=${Process.myUid()} pid=${Process.myPid()}")
        super.onDestroy()
    }

    private fun dispatch(cmd: String, ksud: CompanionAssets.KsudSource? = null, debug: Boolean = false): String = when (cmd) {
        "persist" -> persistChain(ksud, debug)
        "hook-trigger" -> c1Call("hook-trigger") { controllerCall(StageReceiver.CTRL_HOOK_TRIGGER) }
        "uninstall-self" -> uninstallSelf()
        "probe" -> GsdfrNative.probe()
        "hook-check" -> c1Call("hook-check") { controllerCall(StageReceiver.CTRL_HOOK_CHECK) }
        "hop" -> hopWithNonce()
        "stage" -> CompanionAssets.stageAll(this, ksudSource = ksud, debug = debug)
        else -> "[x] unknown cmd: $cmd"
    }

    private fun note(sb: StringBuilder, s: String) {
        sb.append(s)
        persistLog.append(s)
    }
    private fun dbg(sb: StringBuilder, s: String, debug: Boolean) {
        if (debug) note(sb, "[d] $s\n")
    }
    private fun logCmdSummary(cmd: String, result: String, ms: Long) {
        val v = CompanionLog.verdict(result)
        Log.println(v.level.toLogLevel(), TAG, "cmd=$cmd ${v.word} ${CompanionLog.fmtDuration(ms)}${v.tail}")
    }
    private fun CompanionLog.Level.toLogLevel(): Int = when (this) {
        CompanionLog.Level.ERROR -> Log.ERROR
        CompanionLog.Level.WARN -> Log.WARN
        CompanionLog.Level.INFO -> Log.INFO
    }
    private fun liveController(): IBinder? =
        controller?.takeIf { runCatching { it.pingBinder() }.getOrDefault(false) }

    private fun hopWithNonce(): String {
        val nonce = UUID.randomUUID().toString()
        hopNonce = nonce
        return StageHop.hop(this, nonce)
    }

    private fun ensureController(sb: StringBuilder): IBinder? {
        liveController()?.let {
            note(sb, "[*] controller=present (reuse)\n")
            return it
        }
        if (controller != null) {
            controller = null
            note(sb, "[i] controller dead; re-hop\n")
        }
        note(sb, hopWithNonce())
        val c = waitForController(CONTROLLER_WAIT_MS)
        if (c == null) {
            note(sb, "[!] controller not received within ${CONTROLLER_WAIT_MS}ms code=controller_missing\n")
            return null
        }
        note(sb, "[+] controller present\n")
        return c
    }

    private fun c1Call(label: String, call: () -> String): String {
        val sb = StringBuilder()
        ensureController(sb) ?: return sb.toString()
        sb.append("[$label]\n").append(call())
        return sb.toString()
    }

    private fun readKo(kmi: String): ByteArray? = runCatching {
        File(CompanionAssets.lkmPath(kmi)).readBytes()
    }.getOrNull()

    private fun armStep(sb: StringBuilder, debug: Boolean, kmiOverride: String? = null): Boolean {
        val kmi = kmiOverride ?: resolveKmi(sb)
        if (kmi == null) {
            note(sb, "[x] kmi unknown code=kmi_unknown\n")
            return false
        }
        val ko = readKo(kmi)
        if (ko == null || ko.isEmpty()) {
            note(sb, "[x] ko asset missing (run stage first): ${CompanionAssets.lkmPath(kmi)} code=ko_missing\n")
            return false
        }
        note(sb, "[*] kmi=$kmi ko=${ko.size}B\n")
        note(sb, "[hook-arm]\n")
        val t = SystemClock.elapsedRealtime()
        val out = controllerCall(StageReceiver.CTRL_HOOK_ARM) {
            writeString(kmi)
            writeByteArray(ko)
        }
        dbg(sb, "hook-arm took ${SystemClock.elapsedRealtime() - t}ms", debug)
        sb.append(out)
        if (out.contains("status=ok")) return true
        note(sb, "[x] arm failed (page-cache rolled back) code=arm_failed\n")
        return false
    }

    private fun resolveKmi(sb: StringBuilder): String? {
        CompanionAssets.kmiFromText(controllerCall(StageReceiver.CTRL_PROBE))
            ?.let { return it }
        return CompanionAssets.currentKmi()?.also {
            note(sb, "[*] kmi via local fallback: $it\n")
        }
    }

    private fun persistChain(ksud: CompanionAssets.KsudSource? = null, debug: Boolean = false): String {
        val sb = StringBuilder()
        persistLog.clear()
        ensureController(sb) ?: return sb.toString()
        controllerCall(StageReceiver.CTRL_HOOK_PROGRESS) { writeInt(0) }
        val kmi = resolveKmi(sb)
        if (debug) note(sb, "[d] debug=on\n")
        note(sb, "[*] stage assets\n")
        val tStage = SystemClock.elapsedRealtime()
        note(sb, CompanionAssets.stageAll(this, kmi, ksud, debug))
        dbg(sb, "stage took ${SystemClock.elapsedRealtime() - tStage}ms", debug)
        note(sb, "[hook-check]\n")
        val tCheck = SystemClock.elapsedRealtime()
        val chk = controllerCall(StageReceiver.CTRL_HOOK_CHECK)
        dbg(sb, "hook-check took ${SystemClock.elapsedRealtime() - tCheck}ms", debug)
        sb.append(chk)
        if (!chk.contains("status=ok")) {
            note(sb, "[x] check failed: no page-cache writes code=check_failed\n")
            return sb.toString()
        }
        if (!armStep(sb, debug, kmi)) return sb.toString()
        note(sb, "[hook-trigger]\n")
        val tTrig = SystemClock.elapsedRealtime()
        val trig = controllerCall(StageReceiver.CTRL_HOOK_TRIGGER)
        dbg(sb, "hook-trigger took ${SystemClock.elapsedRealtime() - tTrig}ms", debug)
        sb.append(trig)
        val trigOk = trig.contains("status=ok")
        if (!trigOk) {
            val noteText = trig.lineSequence().firstOrNull { it.startsWith("note=") }
                ?.removePrefix("note=")?.trim().orEmpty()
            note(sb, "[x] trigger not completed: " + noteText.ifEmpty { "unknown (see output above)" } + " code=trigger_failed\n")
        }
        note(sb, "[hook-finish]\n")
        sb.append(controllerCall(StageReceiver.CTRL_HOOK_FINISH) { writeInt(0) })
        if (trigOk && !trig.contains("kernelsu=up")) {
            note(sb, "[i] ksud dispatched (bind ok); KernelSU comes up in ~10s\n")
        }
        return sb.toString()
    }

    private fun uninstallSelf(): String {
        val pkg = packageName
        val sched = Executors.newSingleThreadScheduledExecutor()
        sched.schedule(
            {
                runCatching {
                    val p = ProcessBuilder("/system/bin/pm", "uninstall", "--user", "0", pkg)
                        .redirectErrorStream(true)
                        .start()
                    val rc = p.waitFor()
                    if (rc == 0) {
                        val cleaned = runCatching { File("/data/system/ghostsam").deleteRecursively() }
                            .getOrDefault(false)
                        Log.i(TAG, "uninstall-self rc=0 cleaned=$cleaned")
                    } else {
                        Log.w(TAG, "uninstall-self rc=$rc")
                    }
                }.onFailure { Log.w(TAG, "uninstall-self failed: ${it.javaClass.simpleName}: ${it.message}") }
            },
            1_000L,
            TimeUnit.MILLISECONDS,
        )
        sched.shutdown()
        return "uninstall=scheduled\nnote=component uninstalls in ~1s (app confirms via package state)\n"
    }

    private fun waitForController(timeoutMs: Long): IBinder? {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            controller?.let { return it }
            Thread.sleep(50)
        }
        return controller
    }

    private fun controllerCall(code: Int, data: Parcel.() -> Unit = {}): String {
        val c = controller ?: return "[x] no controller\n"
        val d = Parcel.obtain()
        val r = Parcel.obtain()
        return try {
            d.apply(data)
            c.transact(code, d, r, 0)
            r.readString().orEmpty()
        } catch (t: Throwable) {
            "[x] transact($code): ${t.javaClass.simpleName}: ${t.message}\n"
        } finally {
            d.recycle()
            r.recycle()
        }
    }

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_GET_INFO -> runCatching {
                    val reply = Message.obtain(null, MSG_INFO)
                    reply.data = Bundle().apply {
                        putInt(KEY_UID, Process.myUid())
                        putInt(KEY_PID, Process.myPid())
                        putString(KEY_PROC, applicationInfo.processName)
                        putInt(KEY_STARTS, starts)
                        putLong(KEY_LAST_START_AT, lastStartAt)
                    }
                    msg.replyTo?.send(reply)
                }

                MSG_CMD -> {
                    val cmd = msg.data?.getString(KEY_CMD).orEmpty()
                    val ksud = CompanionAssets.KsudSource(
                        msg.data?.getString(KEY_APK),
                        msg.data?.getString(KEY_ENTRY),
                    )
                    val debug = msg.data?.getString(KEY_DEBUG) == "1"
                    val replyTo = msg.replyTo
                    worker.execute {
                        val t0 = SystemClock.elapsedRealtime()
                        val result = runCatching { dispatch(cmd, ksud, debug) }
                            .getOrElse { "[x] ${it.javaClass.simpleName}: ${it.message}" }
                        logCmdSummary(cmd, result, SystemClock.elapsedRealtime() - t0)
                        if (debug) result.lineSequence().forEach {
                            if (it.isNotBlank()) Log.println(CompanionLog.levelOfLine(it).toLogLevel(), TAG, "  $it")
                        }
                        val reply = Message.obtain(null, MSG_RESULT)
                        reply.data = Bundle().apply { putString(KEY_RESULT, result) }
                        runCatching { replyTo?.send(reply) }
                    }
                }

                MSG_PROGRESS -> {
                    val replyTo = msg.replyTo
                    progressWorker.execute {
                        val native = liveController()?.let {
                            runCatching {
                                controllerCall(StageReceiver.CTRL_HOOK_PROGRESS) { writeInt(1) }
                            }.getOrNull()
                        }.orEmpty()
                        val companion = persistLog.snapshot()
                        val reply = Message.obtain(null, MSG_PROGRESS_DATA)
                        reply.data = Bundle().apply {
                            putString(KEY_NATIVE, native)
                            putString(KEY_COMPANION, companion)
                        }
                        runCatching { replyTo?.send(reply) }
                    }
                }
            }
        }
    })

    private companion object {
        const val TAG = "GhostSamCompanion"

        const val MSG_GET_INFO = 1
        const val MSG_INFO = 2
        const val MSG_CMD = 3
        const val MSG_RESULT = 4
        const val MSG_PROGRESS = 5
        const val MSG_PROGRESS_DATA = 6
        const val KEY_CMD = "cmd"
        const val KEY_RESULT = "result"
        const val KEY_UID = "uid"
        const val KEY_NATIVE = "native"
        const val KEY_COMPANION = "companion"
        const val KEY_APK = "apk"
        const val KEY_ENTRY = "entry"
        const val KEY_DEBUG = "dbg"

        const val CONTROLLER_WAIT_MS = 5000L
        const val KEY_PID = "pid"
        const val KEY_PROC = "proc"
        const val KEY_STARTS = "starts"
        const val KEY_LAST_START_AT = "last_start_at"

        var starts = 0
        var lastStartAt = 0L
    }
}
