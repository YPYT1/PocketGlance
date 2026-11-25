package com.opencapture.pocketglance.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.opencapture.pocketglance.MainActivity
import com.opencapture.pocketglance.R

/**
 * Live 期间以前台服务保活，减轻 ColorOS 把无网 SoftAP 路由权限收走导致的
 * UDP `EPERM` / 画面卡住。
 */
class LiveKeepaliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        return START_STICKY
    }

    private fun startAsForeground() {
        ensureChannel()
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification: Notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("正在监视 Pocket")
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "实时监视",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "保持与 Pocket 的无线连接" }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "pocketglance.live"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, LiveKeepaliveService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveKeepaliveService::class.java))
        }
    }
}
