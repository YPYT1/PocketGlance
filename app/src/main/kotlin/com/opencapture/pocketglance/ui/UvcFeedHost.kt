package com.opencapture.pocketglance.ui

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.opencapture.pocketglance.monitor.PictureRotation

/**
 * 有线 UVC 预览宿主：Camera2 直出的 SurfaceTexture → TextureView。
 *
 * 与无线 [LiveFeedHost] 的关键区别：这里**不经过** `InspectorPreviewPipeline` /
 * `NativeGlesFeedSession` 等 GPU 效果与 LUT 管线，预览帧由 Camera2 直接写入
 * Surface，TextureView 只负责显示与画幅变换。因此 UVC 路径不做 LUT、对焦框等
 * 监视后处理（相关入口由 connection.chrome.supportsPreviewLut=false 关闭）。
 */
@Composable
fun UvcFeedHost(
    pictureWidth: Int,
    pictureHeight: Int,
    foreground: Boolean,
    onAttachSurface: (Surface?) -> Unit,
    onFramePresented: (Long) -> Unit,
) {
    val heldSurface = remember { arrayOfNulls<Surface>(1) }
    val attachState = rememberUpdatedState(onAttachSurface)
    val presentedState = rememberUpdatedState(onFramePresented)
    val aspectState = rememberUpdatedState(pictureWidth.toFloat() / pictureHeight.coerceAtLeast(1))
    val sizeState = rememberUpdatedState(pictureWidth to pictureHeight)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (foreground) {
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
                                    val (w, h) = sizeState.value
                                    if (w > 0 && h > 0) texture.setDefaultBufferSize(w, h)
                                    heldSurface[0] = Surface(texture)
                                    attachState.value(heldSurface[0])
                                    applyFeedTransform(view, aspectState.value, PictureRotation.Upright)
                                }

                                override fun onSurfaceTextureSizeChanged(
                                    texture: SurfaceTexture,
                                    width: Int,
                                    height: Int,
                                ) {
                                    applyFeedTransform(view, aspectState.value, PictureRotation.Upright)
                                }

                                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                                    attachState.value(null)
                                    heldSurface[0]?.release()
                                    heldSurface[0] = null
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
                                    presentedState.value(texture.timestamp)
                                }
                            }
                    }
                },
                update = { view ->
                    val (w, h) = sizeState.value
                    if (w > 0 && h > 0 && heldSurface[0] == null) {
                        view.surfaceTexture?.setDefaultBufferSize(w, h)
                    }
                    applyFeedTransform(view, aspectState.value, PictureRotation.Upright)
                    view.isClickable = false
                    view.isFocusable = false
                },
                onRelease = { view ->
                    view.surfaceTextureListener = null
                    attachState.value(null)
                    heldSurface[0]?.release()
                    heldSurface[0] = null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
