package com.opencapture.pocketglance.ui

import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.opencapture.pocketglance.monitor.MonitorLayout

/**
 * 直播画面宿主。Surface 一生一次。
 * 窗口方向与 Pocket 不一致时，用矩阵旋转+铺满，而不是改 Activity（改窗口会卡解码）。
 */
@Composable
fun LiveFeedHost(
    visible: Boolean,
    cameraVertical: Boolean,
    onAttachSurface: (Surface?) -> Unit,
) {
    val heldSurface = remember { arrayOfNulls<Surface>(1) }
    val verticalState = rememberUpdatedState(cameraVertical)
    val attachState = rememberUpdatedState(onAttachSurface)
    val phonePortrait =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    DisposableEffect(Unit) {
        onDispose {
            heldSurface[0]?.release()
            heldSurface[0] = null
            attachState.value(null)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(
                    if (visible) Modifier.background(GlanceColors.ink)
                    else Modifier,
                ),
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).also { view ->
                    view.isOpaque = false
                    view.surfaceTextureListener =
                        object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                texture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                if (heldSurface[0] == null) {
                                    val surface = Surface(texture)
                                    heldSurface[0] = surface
                                    attachState.value(surface)
                                }
                                applyFeedTransform(view, verticalState.value, phonePortrait)
                            }

                            override fun onSurfaceTextureSizeChanged(
                                texture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                applyFeedTransform(view, verticalState.value, phonePortrait)
                            }

                            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                                heldSurface[0]?.release()
                                heldSurface[0] = null
                                attachState.value(null)
                                return true
                            }

                            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                        }
                }
            },
            update = { view ->
                applyFeedTransform(view, cameraVertical, phonePortrait)
                view.alpha = if (visible) 1f else 0f
                view.isClickable = false
                view.isFocusable = false
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

internal fun applyFeedTransform(
    view: TextureView,
    cameraVertical: Boolean,
    phonePortrait: Boolean,
) {
    val vw = view.width.toFloat()
    val vh = view.height.toFloat()
    if (vw <= 0f || vh <= 0f) return
    val aspect = MonitorLayout.plan(cameraVertical).contentAspect
    val rotation = MonitorLayout.contentRotationDegrees(phonePortrait, cameraVertical)
    val matrix = Matrix()
    if (rotation == 0f) {
        val (cw, ch) = MonitorLayout.fittedSize(vw, vh, aspect)
        matrix.setScale(cw / vw, ch / vh, vw / 2f, vh / 2f)
    } else {
        // 先按「旋转后的视口」做 contain，再绕中心转 90° 并放大铺满。
        val (cw, ch) = MonitorLayout.fittedSize(vh, vw, aspect)
        val scaleX = cw / vh
        val scaleY = ch / vw
        val fill = MonitorLayout.fillScaleAfterQuarterTurn(vw, vh)
        matrix.postScale(scaleX * fill, scaleY * fill, vw / 2f, vh / 2f)
        matrix.postRotate(rotation, vw / 2f, vh / 2f)
    }
    view.setTransform(matrix)
}
