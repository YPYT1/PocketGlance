package com.opencapture.pocketglance.ui

import android.graphics.Matrix
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.opencapture.opencine.media.FeedEffectsRenderPlan
import com.opencapture.opencine.runtime.InspectorPreviewPipeline
import com.opencapture.opencine.runtime.NativeGlBackend
import com.opencapture.opencine.runtime.NativeGlesFeedSession
import com.opencapture.opencine.runtime.ScopeSampleBus
import com.opencapture.pocketglance.monitor.MonitorLayout
import com.opencapture.pocketglance.monitor.PictureRotation
import com.opencapture.pocketglance.connection.FocusGesture
import com.opencapture.pocketglance.connection.MonitorFocus
import kotlin.math.roundToInt

/**
 * 直播画面宿主。Surface 一生一次。
 * 摄像姿态只改变显示矩阵，不重建解码 Surface。
 */
@Composable
fun LiveFeedHost(
    rotation: PictureRotation,
    blackSides: Boolean,
    foreground: Boolean,
    pictureWidth: Int,
    pictureHeight: Int,
    sourceWidth: Int,
    sourceHeight: Int,
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
    var waitingForPicture by remember { mutableStateOf(true) }
    var lastPreview by remember { mutableStateOf<Bitmap?>(null) }
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
            onFirstFrame = {
                waitingForPicture = false
                lastPreview = null
            },
            onFramePresented = { timestamp -> timestamp?.let { presentedState.value(it) } },
        )
    }
    LaunchedEffect(renderPlan) { renderer.updatePlan(renderPlan) }
    LaunchedEffect(sourceWidth, sourceHeight) { renderer.setSourceSize(sourceWidth, sourceHeight) }
    val aspect = pictureWidth.toFloat() / pictureHeight.coerceAtLeast(1)
    val blackMargins = blackSides || aspect == 1f
    val transformState = rememberUpdatedState(aspect to rotation)

    fun updateTransform(view: TextureView, sourceAspect: Float, turn: PictureRotation) {
        val (width, height) = MonitorLayout.pictureSize(view.width.toFloat(), view.height.toFloat(), sourceAspect, turn)
        if (width <= 0f || height <= 0f) return
        renderer.setPictureDisplaySize(width.roundToInt(), height.roundToInt())
        applyFeedTransform(view, sourceAspect, turn)
    }

    fun keepLastPicture(view: TextureView) {
        if (waitingForPicture || view.width <= 0 || view.height <= 0) return
        val scale = minOf(1f, 960f / maxOf(view.width, view.height))
        lastPreview = runCatching {
            view.getBitmap((view.width * scale).toInt().coerceAtLeast(1), (view.height * scale).toInt().coerceAtLeast(1))
        }.getOrNull()
    }

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
                .background(if (blackMargins) Color.Black else GlanceColors.ink),
    ) {
        if (foreground) AndroidView(
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
                                waitingForPicture = true
                                if (directOutput) {
                                    heldSurface[0] = Surface(texture)
                                    attachState.value(heldSurface[0])
                                } else {
                                    renderer.attachDisplay(texture, width, height)
                                }
                                val (sourceAspect, turn) = transformState.value
                                updateTransform(view, sourceAspect, turn)
                            }

                            override fun onSurfaceTextureSizeChanged(
                                texture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                if (!directOutput) renderer.resize(width, height)
                                val (sourceAspect, turn) = transformState.value
                                updateTransform(view, sourceAspect, turn)
                            }

                            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                                keepLastPicture(view)
                                waitingForPicture = true
                                detachSurface()
                                Log.d("GlanceFeed", "surface destroyed")
                                return true
                            }

                            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
                                if (directOutput) {
                                    waitingForPicture = false
                                    lastPreview = null
                                    presentedState.value(texture.timestamp)
                                }
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
                updateTransform(view, aspect, rotation)
                view.isClickable = false
                view.isFocusable = false
            },
            onRelease = { view ->
                view.surfaceTextureListener = null
                keepLastPicture(view)
                waitingForPicture = true
                detachSurface()
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (waitingForPicture) lastPreview?.let { bitmap ->
            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        if (blackMargins) {
            Canvas(Modifier.matchParentSize()) {
                val (width, height) = MonitorLayout.pictureSize(size.width, size.height, aspect, rotation)
                val visibleWidth = if (rotation.quarterTurn) height else width
                val visibleHeight = if (rotation.quarterTurn) width else height
                val insetX = ((size.width - visibleWidth) / 2f).coerceAtLeast(0f)
                val insetY = ((size.height - visibleHeight) / 2f).coerceAtLeast(0f)
                if (insetX <= 0f && insetY <= 0f) return@Canvas
                clipRect(insetX, insetY, size.width - insetX, size.height - insetY, ClipOp.Difference) {
                    drawRect(Color.Black)
                }
            }
        }
        if (foreground && !waitingForPicture && focus != null) MonitorFocusLayer(aspect, rotation, focus, onFocus)
    }
}

internal fun applyFeedTransform(
    view: TextureView,
    aspect: Float,
    rotation: PictureRotation,
) {
    val vw = view.width.toFloat()
    val vh = view.height.toFloat()
    if (vw <= 0f || vh <= 0f) return
    val matrix = Matrix()
    val (contentWidth, contentHeight) = MonitorLayout.pictureSize(vw, vh, aspect, rotation)
    matrix.setScale(
        contentWidth / vw,
        contentHeight / vh,
        vw / 2f,
        vh / 2f,
    )
    if (rotation != PictureRotation.Upright) {
        matrix.postRotate(rotation.degrees, vw / 2f, vh / 2f)
    }
    view.setTransform(matrix)
}
