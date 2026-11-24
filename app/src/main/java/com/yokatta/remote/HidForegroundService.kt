package com.yokatta.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class HidForegroundService : Service(), BluetoothHidManager.Listener {

    companion object {
        const val CHANNEL_ID = "yokatta_remote_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_DISCONNECT = "com.yokatta.remote.ACTION_DISCONNECT"
    }

    inner class LocalBinder : Binder() {
        fun getService(): HidForegroundService = this@HidForegroundService
    }

    private val binder = LocalBinder()
    lateinit var hidManager: BluetoothHidManager
        private set

    var uiListener: BluetoothHidManager.Listener? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        hidManager = BluetoothHidManager(applicationContext)
        hidManager.listener = this
        startForegroundCompat("藍牙遙控服務待機中")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            hidManager.disconnect()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        hidManager.start()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        hidManager.stop()
    }

    private fun startForegroundCompat(statusText: String) {
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(statusText: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, HidForegroundService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPending = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("好咖機萬能遙控器")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_remote)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(0, "中斷並關閉", disconnectPending)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "好咖機藍牙連線維持",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持手機與好咖機的藍牙遙控連線不中斷"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    override fun onServiceReady(registered: Boolean) {
        if (registered && !hidManager.isConnected()) {
            updateNotification("藍牙待命中（開啟 App 即可連線）")
        }
        uiListener?.onServiceReady(registered)
    }

    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
        when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
                val name = device?.name ?: device?.address ?: "好咖機"
                updateNotification("已連線至 $name（背景維持中）")
            }
            BluetoothProfile.STATE_CONNECTING -> {
                updateNotification("正在連線至好咖機...")
            }
            else -> {
                updateNotification("未連線")
            }
        }
        uiListener?.onConnectionStateChanged(device, state)
    }

    override fun onError(message: String) {
        uiListener?.onError(message)
    }
}
