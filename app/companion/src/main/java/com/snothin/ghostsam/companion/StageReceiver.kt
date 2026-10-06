package com.snothin.ghostsam.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import android.util.Log

class StageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            System.loadLibrary("gsdfr")
        } catch (t: Throwable) {
            Log.e(TAG, "loadLibrary(gsdfr) failed", t)
            return
        }

        val controller = controllerBinder()
        val extras = Bundle()
        extras.putBinder(EXTRA_CONTROLLER, controller)
        extras.putString(EXTRA_HOP_NONCE, intent.getStringExtra(EXTRA_HOP_NONCE).orEmpty())
        val back = Intent()
        back.setPackage(EVIL_PKG)
        back.action = EVIL_ACTION
        back.putExtras(extras)
        context.sendBroadcast(back)
        Log.i(TAG, "controller sent")
    }

    private fun controllerBinder(): Binder {
        return object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                try {
                    val out = when (code) {
                        CTRL_PROBE -> GsdfrNative.probe()
                        CTRL_HOOK_CHECK -> GsdfrNative.hookCheck()
                        CTRL_HOOK_ARM -> GsdfrNative.hookArm(
                            data.readString().orEmpty(),
                            data.createByteArray() ?: ByteArray(0),
                        )
                        CTRL_HOOK_TRIGGER -> GsdfrNative.hookTrigger()
                        CTRL_HOOK_FINISH -> GsdfrNative.hookFinish(data.readInt() != 0)
                        CTRL_HOOK_PROGRESS -> GsdfrNative.progress(data.readInt())
                        else -> return false
                    }
                    reply?.writeString(out)
                    return true
                } catch (t: Throwable) {
                    Log.e(TAG, "ctrl code $code threw ${t.javaClass.simpleName}: ${t.message}", t)
                    return false
                }
            }
        }
    }

    companion object {
        const val TAG = "GhostSamCompanion"
        const val EVIL_PKG = "com.snothin.ghostsam.companion"
        const val EVIL_ACTION = "com.snothin.ghostsam.companion.EVIL"
        const val EXTRA_CONTROLLER = "controller"

        const val EXTRA_HOP_NONCE = "hop_nonce"

        const val CTRL_PROBE = 1
        const val CTRL_HOOK_CHECK = 4
        const val CTRL_HOOK_ARM = 5
        const val CTRL_HOOK_TRIGGER = 6
        const val CTRL_HOOK_FINISH = 7
        const val CTRL_HOOK_PROGRESS = 8
    }
}
