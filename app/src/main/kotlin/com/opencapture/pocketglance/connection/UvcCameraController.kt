package com.opencapture.pocketglance.connection

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraDevice.StateCallback as DeviceStateCallback
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Surface
import com.opencapture.pocketglance.diagnostics.GlanceLog
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Camera2 外接 UVC 摄像头控制器。
 *
 * - 枚举 [CameraCharacteristics.LENS_FACING_EXTERNAL] 摄像头（OEM 可选：部分平板没有外部摄像头 HAL）。
 * - 从 [CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP] 读稳定输出尺寸，交给 [UvcSelection] 选分辨率。
 * - 打开后把预览帧直接输出到传入的 [Surface]（TextureView 的 SurfaceTexture），绕开 GPU 效果/LUT 管线。
 *
 * 回调都切到主线程（handler = main looper），便于 Transport 直接更新状态并安全注销广播接收器。
 */
internal class UvcCameraController(
    context: Context,
    private val onConfigured: () -> Unit,
    private val onDisconnected: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val handler = Handler(Looper.getMainLooper())

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var previewSize: Size? = null
    private var closed = false

    fun externalCameraIds(): List<String> =
        runCatching {
            cameraManager.cameraIdList.filter { id ->
                runCatching {
                    cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_EXTERNAL
                }.getOrDefault(false)
            }
        }.getOrDefault(emptyList())

    /** HAL 声明的可用于 SurfaceTexture（预览）的稳定尺寸集合。 */
    fun supportedSizes(cameraId: String): List<Size> =
        runCatching {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java)
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList())

    /** 打开摄像头；成功后若 Surface 已就位（重连场景）立即配置会话。 */
    suspend fun open(cameraId: String, size: Size): Boolean {
        if (closed) return false
        previewSize = size
        return try {
            val device = awaitOpen(cameraId)
            if (closed) {
                device.close()
                return false
            }
            cameraDevice = device
            GlanceLog.i("uvc", "camera opened id=$cameraId size=${size.width}x${size.height}")
            previewSurface?.let { configureSession(device, it) }
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            GlanceLog.e("uvc", "open camera failed", error)
            false
        }
    }

    /** 绑定／解绑输出 Surface。摄像头未打开时先缓存；打开后按需（重）建会话。 */
    fun attachSurface(surface: Surface?) {
        previewSurface = surface
        val device = cameraDevice
        when {
            surface == null -> closeSession()
            device != null -> configureSession(device, surface)
            else -> Unit
        }
    }

    private suspend fun awaitOpen(cameraId: String): CameraDevice =
        suspendCancellableCoroutine { continuation ->
            var openedDevice: CameraDevice? = null
            continuation.invokeOnCancellation { openedDevice?.close() }
            val callback =
                object : DeviceStateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        openedDevice = device
                        if (continuation.isActive) continuation.resume(device) else device.close()
                    }

                    override fun onDisconnected(device: CameraDevice) {
                        device.close()
                        onDisconnected()
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.failure(IOException("camera disconnected")))
                        }
                    }

                    override fun onError(device: CameraDevice, error: Int) {
                        device.close()
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.failure(IOException("camera open error $error")))
                        }
                    }
                }
            runCatching { cameraManager.openCamera(cameraId, callback, handler) }.onFailure {
                if (continuation.isActive) continuation.resumeWith(Result.failure(it))
            }
        }

    private fun configureSession(device: CameraDevice, surface: Surface) {
        if (closed || !surface.isValid) return
        closeSession()
        val builder =
            try {
                device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }
            } catch (error: Throwable) {
                GlanceLog.e("uvc", "create preview request failed", error)
                onError("无法创建摄像头预览请求")
                return
            }
        val sessionCallback =
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (closed || cameraDevice !== device) {
                        session.close()
                        return
                    }
                    captureSession = session
                    GlanceLog.i("uvc", "capture session configured size=${previewSize?.width}x${previewSize?.height}")
                    try {
                        session.setRepeatingRequest(builder.build(), null, handler)
                    } catch (error: Throwable) {
                        GlanceLog.e("uvc", "setRepeatingRequest failed", error)
                        onError("无法启动摄像头预览")
                        return
                    }
                    onConfigured()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    GlanceLog.w("uvc", "capture session configure failed")
                    onError("摄像头预览配置失败")
                }
            }
        try {
            device.createCaptureSession(listOf(surface), sessionCallback, handler)
        } catch (error: Throwable) {
            GlanceLog.e("uvc", "createCaptureSession failed", error)
            onError("无法建立摄像头预览会话")
        }
    }

    private fun closeSession() {
        captureSession?.let { runCatching { it.close() } }
        captureSession = null
    }

    fun close() {
        if (closed) return
        closed = true
        closeSession()
        previewSurface = null
        cameraDevice?.let { runCatching { it.close() } }
        cameraDevice = null
        previewSize = null
    }
}
