package com.opencapture.pocketglance.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * 监视直播时榨干前台：常亮 + 尽量锁最高刷新率。
 * Wi‑Fi `WIFI_MODE_FULL_LOW_LATENCY` 已由 PocketCameraSession 在进 SoftAP 时持有。
 */
@Composable
fun LivePerformanceGate(active: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(active) {
        val window = activity.window
        val previousModeId = window.attributes.preferredDisplayModeId
        if (active) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val peak =
                activity.display?.supportedModes
                    ?.maxByOrNull { it.refreshRate }
            if (peak != null) {
                val attrs = window.attributes
                attrs.preferredDisplayModeId = peak.modeId
                window.attributes = attrs
            }
        }
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val attrs = window.attributes
            attrs.preferredDisplayModeId = previousModeId
            window.attributes = attrs
        }
    }
}
