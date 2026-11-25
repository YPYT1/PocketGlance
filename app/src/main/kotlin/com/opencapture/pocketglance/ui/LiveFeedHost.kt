package com.opencapture.pocketglance.ui

import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.util.Log
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
    onFramePresented: (Long) -> Unit,
) {
    val heldSurface = remember { arrayOfNulls<Surface>(1) }
    val verticalState = rememberUpdatedState(cameraVertical)
    val attachState = rememberUpdatedState(onAttachSurface)
    val presentedState = rememberUpdatedState(onFramePresented)
    val phonePortrait =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val phonePortraitState = rememberUpdatedState(phonePortrait)

    DisposableEffect(Unit) {
        onDispose {
            attachState.value(null)
            heldSurface[0]?.release()
            heldSurface[0] = null
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
                                    heldSurface[0] = Surface(texture)
                                    attachState.value(heldSurface[0])
                                    Log.d("GlanceFeed", "surface attached ${width}x$height")
                                }
                                applyFeedTransform(view, verticalState.value, phonePortraitState.value)
                            }

                            override fun onSurfaceTextureSizeChanged(
                                texture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                applyFeedTransform(view, verticalState.value, phonePortraitState.value)
                            }

                            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                                attachState.value(null)
                                heldSurface[0]?.release()
                                heldSurface[0] = null
                                Log.d("GlanceFeed", "surface destroyed")
                                return true
                            }

                            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
                                presentedState.value(texture.timestamp)
                            }
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
    val (contentWidth, contentHeight) = MonitorLayout.contentSizeBeforeRotation(vw, vh, aspect, rotation != 0f)
    matrix.setScale(
        contentWidth / vw,
        contentHeight / vh,
        vw / 2f,
        vh / 2f,
    )
    if (rotation != 0f) {
        matrix.postRotate(rotation, vw / 2f, vh / 2f)
    }
    view.setTransform(matrix)
}
