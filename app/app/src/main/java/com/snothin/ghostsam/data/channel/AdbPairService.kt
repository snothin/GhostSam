package com.snothin.ghostsam.data.channel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.snothin.ghostsam.R
import com.snothin.ghostsam.data.exploit.RunState
import com.snothin.ghostsam.data.prefs.SettingsPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AdbPairService : Service() {

    companion object {
        const val ACTION_PAIR_CODE = "com.snothin.ghostsam.PAIR_CODE"
        const val ACTION_ADB_CONNECTED = "com.snothin.ghostsam.ADB_CONNECTED"
        const val ACTION_ADB_FAILED = "com.snothin.ghostsam.ADB_FAILED"
        const val EXTRA_ERROR = "extra_error"

        const val EXTRA_SILENT = "extra_silent"
        private const val KEY_CODE = "pair_code"
        private const val TAG = "AdbPairService"
        private const val CHANNEL_ID = "adb_pair"

        private const val CHANNEL_ID_QUIET = "adb_pair_connecting"
        private const val NOTIF_ID = 0xADB

        private const val ARM_WAIT_MS = 150_000L

        fun start(context: Context) {
            val component = context.startForegroundService(Intent(context, AdbPairService::class.java))
            // Manifest guard: startForegroundService returns null (no exception) when the service is missing
            // from the manifest; log explicitly so it cannot fail silently.
            if (component == null) {
                Log.e(TAG, "startForegroundService returned null — AdbPairService missing from the manifest?")
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var autoConnectRunning = false

    private val pairCodeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_PAIR_CODE) return
            val code = RemoteInput.getResultsFromIntent(intent)?.getString(KEY_CODE) ?: return
            handlePairCode(code)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        registerReceiver(
            pairCodeReceiver,
            IntentFilter(ACTION_PAIR_CODE),
            Context.RECEIVER_NOT_EXPORTED,
        )
        startForeground(NOTIF_ID, buildConnectingNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            if (autoConnectRunning) return@launch
            autoConnectRunning = true
            try {
                val silent = AdbController.reconnectLast()
                Log.i(TAG, "auto-connect: silentReconnect=$silent")
                if (silent) {
                    finishConnected(silent = true)
                    return@launch
                }
                val conn = AdbController.discoverConnectPort(this@AdbPairService)
                val host = conn?.first
                val port = conn?.second ?: 5555
                val connected = !host.isNullOrBlank() && AdbController.connect(host, port)
                Log.i(TAG, "auto-connect: mdns host=$host port=$port connect=$connected")
                if (connected) {
                    finishConnected(silent = true)
                    return@launch
                }
                openWirelessDebuggingSettings()
                val needsCode = !AdbController.hasPairedBefore() || AdbController.lastConnectNeedsPairing
                Log.i(
                    TAG,
                    "auto-connect failed: needsCode=$needsCode " +
                        "pairedBefore=${AdbController.hasPairedBefore()} " +
                        "lastNeedsPairing=${AdbController.lastConnectNeedsPairing}",
                )
                // All failure paths offer the code-input entry (the old "already paired => no input" split was a dead end).
                notify(
                    getString(
                        if (needsCode) R.string.adb_pair_notif_text else R.string.adb_pair_notif_retry,
                    ),
                    withInput = true,
                )
            } finally {
                autoConnectRunning = false
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun finishConnected(silent: Boolean) {
        cleanupNotification()
        sendBroadcast(
            Intent(ACTION_ADB_CONNECTED).setPackage(packageName).putExtra(EXTRA_SILENT, silent),
        )
        bringAppToFront()
        armTcpIfEnabled()
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pairCodeReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handlePairCode(code: String) {
        scope.launch {
            notify(getString(R.string.adb_pair_notif_pairing), withInput = false)
            // The pairing port is only advertised while the settings page is open; rediscover each round.
            val found = AdbController.discoverPairingPort(this@AdbPairService)
            val conn = AdbController.discoverConnectPort(this@AdbPairService)
            val host = found?.first ?: conn?.first
            val pairPort = found?.second ?: 0
            val connectPort = conn?.second ?: 5555
            if (host.isNullOrBlank() || pairPort <= 0) {
                fail(getString(R.string.adb_pair_not_found))
                return@launch
            }
            if (!AdbController.pair(host, pairPort, code)) {
                fail(getString(R.string.adb_pair_failed, getString(R.string.adb_pair_title)))
                return@launch
            }
            if (!AdbController.connect(host, connectPort)) {
                fail(getString(R.string.adb_pair_failed, getString(R.string.adb_pair_title)))
                return@launch
            }
            finishConnected(silent = false)
        }
    }

    private fun fail(message: String) {
        stopForeground(STOP_FOREGROUND_DETACH)
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildTextNotification(message, ongoing = false))
        sendBroadcast(
            Intent(ACTION_ADB_FAILED).setPackage(packageName).putExtra(EXTRA_ERROR, message),
        )
        stopSelf()
    }

    private fun cleanupNotification() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }

    private fun openWirelessDebuggingSettings() {
        val intent = Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    private fun bringAppToFront() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        if (intent != null) {
            runCatching { startActivity(intent) }
        }
    }

    private suspend fun armTcpIfEnabled() {
        if (!SettingsPrefs.tcpPersist(this)) return
        if (AdbController.isArmed()) return
        // Never switch TCP while the exploit runs; the adbd restart would kill the payload session.
        if (RunState.exploitActive) {
            Log.w(TAG, "skip TCP arm: exploit is running")
            return
        }
        val job = scope.async { AdbController.armTcp() }
        val error = withTimeoutOrNull(ARM_WAIT_MS) { job.await() }
        Log.w(
            TAG,
            when {
                !job.isCompleted -> "TCP arm timed out (open hung?); re-arm from settings"
                error == null -> "TCP mode enabled (no-WiFi reconnect)"
                else -> "TCP enable failed: $error"
            },
        )
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.adb_pair_notif_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID_QUIET,
                getString(R.string.adb_pair_notif_channel_quiet),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun notify(text: String, withInput: Boolean, ongoing: Boolean = true) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = if (withInput) buildPairNotification(text) else buildTextNotification(text, ongoing)
        manager.notify(NOTIF_ID, notification)
    }

    private fun buildPairNotification(text: String = getString(R.string.adb_pair_notif_text)): Notification {
        val remoteInput = RemoteInput.Builder(KEY_CODE)
            .setLabel(getString(R.string.adb_pair_code_label))
            .build()
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_PAIR_CODE).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val action = NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_monochrome,
            getString(R.string.adb_pair_notif_input),
            pendingIntent,
        ).addRemoteInput(remoteInput).build()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.adb_pair_notif_title))
            .setContentText(text)
            .setOngoing(true)
            .addAction(action)
            .build()
    }

    private fun buildConnectingNotification(): Notification =
        buildTextNotification(
            getString(R.string.adb_pair_notif_connecting),
            channelId = CHANNEL_ID_QUIET,
        )

    private fun buildTextNotification(
        text: String,
        ongoing: Boolean = true,
        channelId: String = CHANNEL_ID,
    ): Notification {
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.adb_pair_notif_title))
            .setContentText(text)
            .setOngoing(ongoing)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setContentIntent(
                PendingIntent.getActivity(
                    this, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        return builder.build()
    }
}
