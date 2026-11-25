package com.opencapture.pocketglance.ui

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.opencapture.pocketglance.session.LiveKeepaliveService

/**
 * 监视直播时屏幕常亮，并通过前台服务保活 SoftAP。
 * Wi‑Fi `WIFI_MODE_FULL_LOW_LATENCY` 已由 PocketCameraSession 在进 SoftAP 时持有。
 */
@Composable
fun LivePerformanceGate(live: Boolean, immersive: Boolean) {
    val activity = LocalActivity.current ?: return
    val appContext = activity.applicationContext
    DisposableEffect(activity, live) {
        val window = activity.window
        if (live) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            runCatching { LiveKeepaliveService.start(appContext) }
        }
        onDispose {
            if (live) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                runCatching { LiveKeepaliveService.stop(appContext) }
            }
        }
    }
    DisposableEffect(activity, immersive) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val previousBehavior = controller.systemBarsBehavior
        if (immersive) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (immersive) {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = previousBehavior
            }
        }
    }
}
