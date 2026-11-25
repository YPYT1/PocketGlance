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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.opencapture.opencine.media.FeedEffectsRenderPlan
import com.opencapture.opencine.runtime.InspectorPreviewPipeline
import com.opencapture.opencine.runtime.NativeGlBackend
import com.opencapture.opencine.runtime.NativeGlesFeedSession
import com.opencapture.opencine.runtime.ScopeSampleBus
import com.opencapture.pocketglance.monitor.MonitorLayout
import com.opencapture.pocketglance.connection.FocusGesture
import com.opencapture.pocketglance.connection.MonitorFocus

/**
 * 直播画面宿主。Surface 一生一次。
 * 窗口方向与 Pocket 不一致时，用矩阵旋转+铺满，而不是改 Activity（改窗口会卡解码）。
 */
@Composable
fun LiveFeedHost(
    cameraVertical: Boolean,
    pictureWidth: Int,
    pictureHeight: Int,
    renderPlan: FeedEffectsRenderPlan,
    focus: MonitorFocus?,
    onFocus: (FocusGesture, Float, Float) -> Unit,
    onPreviewAvailable: (Boolean) -> Unit,
    onAttachSurface: (Surface?) -> Unit,
    onFramePresented: (Long) -> Unit,
) {
    val heldSurface = remember { arrayOfNulls<Surface>(1) }
    val attachState = rememberUpdatedState(onAttachSurface)
    val presentedState = rememberUpdatedState(onFramePresented)
    val availabilityState = rememberUpdatedState(onPreviewAvailable)
    val context = LocalContext.current.applicationContext
    var directOutput by remember { mutableStateOf(false) }
    val scopeBus = remember { ScopeSampleBus() }
    val preview = remember { InspectorPreviewPipeline(scopeBus, NativeGlBackend::create) }
    val renderer = remember {
        NativeGlesFeedSession(
            context, scopeBus, preview, NativeGlBackend::create,
            onDecoderSurface = {
                attachState.value(it)
                Log.d("GlanceFeed", "GPU decoder surface attached")
            },
            onGpuFailed = {
                attachState.value(null)
                directOutput = true
                availabilityState.value(false)
            },
            letterboxSource = false,
            notifySurfaceOnMain = true,
            onFramePresented = { timestamp -> timestamp?.let { presentedState.value(it) } },
        )
    }
    LaunchedEffect(renderPlan) { renderer.updatePlan(renderPlan) }
    LaunchedEffect(pictureWidth, pictureHeight) { renderer.setSourceSize(pictureWidth, pictureHeight) }
    val aspect = pictureWidth.toFloat() / pictureHeight.coerceAtLeast(1)
    val phonePortrait =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val rotation = MonitorLayout.contentRotationDegrees(phonePortrait, cameraVertical)
    val transformState = rememberUpdatedState(aspect to rotation)

    fun detachSurface() {
        attachState.value(null)
        renderer.detachDisplay()
        heldSurface[0]?.release()
        heldSurface[0] = null
    }

    DisposableEffect(Unit) {
        onDispose {
            detachSurface()
            renderer.close()
            preview.close()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GlanceColors.ink),
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
                                if (directOutput) {
                                    heldSurface[0] = Surface(texture)
                                    attachState.value(heldSurface[0])
                                } else {
                                    renderer.attachDisplay(texture, width, height)
                                }
                                val (sourceAspect, turn) = transformState.value
                                applyFeedTransform(view, sourceAspect, turn)
                            }

                            override fun onSurfaceTextureSizeChanged(
                                texture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                if (!directOutput) renderer.resize(width, height)
                                val (sourceAspect, turn) = transformState.value
                                applyFeedTransform(view, sourceAspect, turn)
                            }

                            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                                detachSurface()
                                Log.d("GlanceFeed", "surface destroyed")
                                return true
                            }

                            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
                                if (directOutput) presentedState.value(texture.timestamp)
                            }
                        }
                }
            },
            update = { view ->
                if (directOutput && heldSurface[0] == null) {
                    view.surfaceTexture?.let { texture ->
                        renderer.detachDisplay()
                        heldSurface[0] = Surface(texture)
                        attachState.value(heldSurface[0])
                    }
                }
                applyFeedTransform(view, aspect, rotation)
                view.isClickable = false
                view.isFocusable = false
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (focus != null) MonitorFocusLayer(aspect, rotation, focus, onFocus)
    }
}

internal fun applyFeedTransform(
    view: TextureView,
    aspect: Float,
    rotation: Float,
) {
    val vw = view.width.toFloat()
    val vh = view.height.toFloat()
    if (vw <= 0f || vh <= 0f) return
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
