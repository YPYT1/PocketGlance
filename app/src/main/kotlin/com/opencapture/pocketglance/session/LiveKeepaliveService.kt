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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.opencapture.pocketglance.MainActivity
import com.opencapture.pocketglance.GlanceApp
import com.opencapture.pocketglance.R

/**
 * Live 期间以前台服务保活，减轻 ColorOS 把无网 SoftAP 路由权限收走导致的
 * UDP `EPERM` / 画面卡住。
 */
class LiveKeepaliveService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            (application as GlanceApp).model.disconnect()
            return START_NOT_STICKY
        }
        startAsForeground()
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "$packageName:camera-session",
            )?.apply { setReferenceCounted(false); acquire() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
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
                .setContentText("正在保持 Pocket 无线会话")
                .setContentIntent(open)
                .addAction(0, "断开", PendingIntent.getService(
                    this, 1, Intent(this, LiveKeepaliveService::class.java).setAction(ACTION_DISCONNECT),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ))
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
        private const val ACTION_DISCONNECT = "com.opencapture.pocketglance.DISCONNECT_CAMERA"

        fun start(context: Context) {
            val intent = Intent(context, LiveKeepaliveService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveKeepaliveService::class.java))
        }
    }
}
