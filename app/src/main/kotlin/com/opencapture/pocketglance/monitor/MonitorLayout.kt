package com.opencapture.pocketglance.monitor

import android.content.pm.ActivityInfo
import kotlin.math.max
import kotlin.math.min

/**
 * 监视页布局。
 *
 * 直播时窗口跟随手机传感器，Pocket 码流方向只影响画面变换。
 * 主页/相册固定竖屏。
 */
data class MonitorLayoutPlan(
    val contentAspect: Float,
    val requestedOrientation: Int,
)

object MonitorLayout {
    const val HOME_ORIENTATION: Int = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    const val SENSOR_ORIENTATION: Int = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

    fun plan(cameraVertical: Boolean): MonitorLayoutPlan {
        val contentAspect = if (cameraVertical) 9f / 16f else 16f / 9f
        return MonitorLayoutPlan(
            contentAspect = contentAspect,
            requestedOrientation = entryActivityOrientation(cameraVertical),
        )
    }

    /** 按源画面推导的方向，不用于直播中的窗口切换。 */
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

    fun contentSizeBeforeRotation(
        viewWidth: Float,
        viewHeight: Float,
        sourceAspect: Float,
        quarterTurn: Boolean,
    ): Pair<Float, Float> {
        if (viewWidth <= 0f || viewHeight <= 0f || sourceAspect <= 0f) return 0f to 0f
        val targetWidth = if (quarterTurn) viewHeight else viewWidth
        val targetHeight = if (quarterTurn) viewWidth else viewHeight
        val width = max(targetWidth, targetHeight * sourceAspect)
        return width to width / sourceAspect
    }
}
