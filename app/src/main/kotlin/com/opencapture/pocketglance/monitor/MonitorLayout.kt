package com.opencapture.pocketglance.monitor

import android.content.pm.ActivityInfo
import kotlin.math.max
import kotlin.math.min

/**
 * 监视页布局。
 *
 * 进监视页时按 Pocket 横/竖设一次 Activity，随后 [SCREEN_ORIENTATION_LOCKED] 锁死窗口，
 * 中途翻拍只用画面旋转适配——避免 relayout 打断 MediaCodec（日志里的假重连）。
 * 主页/相册固定竖屏。
 */
data class MonitorLayoutPlan(
    val contentAspect: Float,
    val requestedOrientation: Int,
)

object MonitorLayout {
    const val HOME_ORIENTATION: Int = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    const val LOCKED_ORIENTATION: Int = ActivityInfo.SCREEN_ORIENTATION_LOCKED

    fun plan(cameraVertical: Boolean): MonitorLayoutPlan {
        val contentAspect = if (cameraVertical) 9f / 16f else 16f / 9f
        return MonitorLayoutPlan(
            contentAspect = contentAspect,
            requestedOrientation = entryActivityOrientation(cameraVertical),
        )
    }

    /** 进入监视页时的一次性方向（随后应 LOCKED）。 */
    fun entryActivityOrientation(cameraVertical: Boolean): Int =
        if (cameraVertical) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

    @Deprecated("Use entryActivityOrientation; mid-session flips must not change Activity")
    fun forcedActivityOrientation(cameraVertical: Boolean): Int = entryActivityOrientation(cameraVertical)

    /**
     * 窗口方向与 Pocket 画面不一致时，画面需旋转的角度（顺时针）。
     * phonePortrait 与 cameraVertical 同为 true/false 时为 0。
     */
    fun contentRotationDegrees(phonePortrait: Boolean, cameraVertical: Boolean): Float =
        if (phonePortrait == cameraVertical) 0f else 90f

    /**
     * 旋转 90° 后铺满窗口的缩放（旋转后宽高对调）。
     */
    fun fillScaleAfterQuarterTurn(viewW: Float, viewH: Float): Float {
        if (viewW <= 0f || viewH <= 0f) return 1f
        return max(viewW, viewH) / min(viewW, viewH)
    }

    fun fittedSize(viewW: Float, viewH: Float, contentAspect: Float): Pair<Float, Float> {
        if (viewW <= 0f || viewH <= 0f || contentAspect <= 0f) return 0f to 0f
        val viewAspect = viewW / viewH
        return if (viewAspect > contentAspect) {
            val h = viewH
            val w = h * contentAspect
            w to h
        } else {
            val w = viewW
            val h = w / contentAspect
            w to h
        }
    }
}
