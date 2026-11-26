package com.opencapture.pocketglance.connection

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import com.opencapture.pocketglance.diagnostics.GlanceLog
import com.opencapture.pocketglance.monitor.PictureRotation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 有线「网络摄像头／UVC」会话：Pocket 4P 切到 UVC 模式后，机身作为标准 USB Video
 * Class 设备，Android 经 Camera HAL 暴露为外部摄像头（LENS_FACING_EXTERNAL）。
 *
 * 本 Transport **不**声明厂商 Bulk、不碰 MSC/RNDIS、不发送任何 USB 模式切换控制传输。
 * 它只做三件事：
 * 1. 只识别带 Video 接口（class 0x0E）的 Pocket 设备；
 * 2. 枚举外部摄像头并选一个稳定预览分辨率；
 * 3. 用 Camera2 打开并把预览帧直接输出到监视器 TextureView 的 Surface。
 *
 * 之前的「基础会话」（打开厂商 Bulk／AOA 附件后直接进 Live、但无画面）是假预览，
 * 已移除。直播码流仍不经过 DJI 私有 Bulk 协议。
 */
class WiredUsbTransport(
    context: Context,
    private val seedDevice: UsbDevice? = null,
    @Suppress("unused") private val seedAccessory: UsbAccessory? = null,
) : ConnectionTransport {
    override val mode: ConnectionMode = ConnectionMode.Wired

    private val appContext = context.applicationContext
    private val usb = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val _state = MutableStateFlow(ConnectionState(mode = mode))
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var controller: UvcCameraController? = null
    private var pendingSurface: Surface? = null
    private var boundDevice: UsbDevice? = null
    private var detachReceiver: BroadcastReceiver? = null
    private var closed = false
    private var pictureWidth = 1280
    private var pictureHeight = 720

    override suspend fun connect() {
        if (closed) return
        GlanceLog.i("wired", "connect start seedDevice=${seedDevice?.deviceName} seedAccessory=${seedAccessory?.model}")
        logUsbInventory()
        emit(
            phase = ConnectionPhase.Connecting,
            message = "正在查找 USB 摄像头…",
            pocket = PocketState.Discovering,
        )
        registerDetachWatcher()

        val device = seedDevice?.takeIf { isPocketDevice(it) } ?: findPocketDevice()
        if (device != null) {
            // Watch detach for session cleanup; Camera HAL retains ownership of the video interface.
            boundDevice = device
            GlanceLog.i("wired", "Pocket USB descriptor visible=${isPocketUvcDevice(device)} ${deviceLabel(device)}")
        } else {
            GlanceLog.i("wired", "no app-visible Pocket USB descriptor; continuing with Camera2 external discovery")
        }
        // Camera2 owns the UVC interface on supported devices. Do not claim an interface even
        // when UsbManager also exposes a UsbDevice descriptor for that same Pocket.

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            fail("缺少摄像头权限，无法预览外接摄像头。请授予相机权限后重试。")
            return
        }

        val created = UvcCameraController(
            appContext,
            onConfigured = ::onUvcConfigured,
            onDisconnected = ::onUvcDisconnected,
            onError = ::fail,
        )
        controller = created

        var externalIds = created.externalCameraIds()
        if (externalIds.isEmpty()) {
            emit(
                phase = ConnectionPhase.Connecting,
                message = "请在 Pocket 上选择「网络摄像头」(2秒)，正在等待外接摄像头…",
                pocket = PocketState.Discovering,
            )
            val found = withTimeoutOrNull(4_000L) {
                while (!closed && externalIds.isEmpty()) {
                    delay(200)
                    externalIds = created.externalCameraIds()
                }
                externalIds.isNotEmpty()
            } == true
            if (closed) return
            if (!found) {
                fail(
                    "未检测到可用的 Pocket 网络摄像头。请确认选择了「网络摄像头」而不是「文件传输」或「仅充电」，" +
                        "并确认平板支持 USB UVC 外接相机。",
                )
                return
            }
        }
        val uvcDevice = device
        if (uvcDevice != null) {
            boundDevice = uvcDevice
            logInterfaces(uvcDevice)
            GlanceLog.i("wired", "uvc device ${deviceLabel(uvcDevice)}")
        } else {
            GlanceLog.i("wired", "Camera2 external camera found without an app-visible USB descriptor")
        }
        emit(
            phase = ConnectionPhase.Connecting,
            message = "已发现外接摄像头，正在配置预览…",
            pocket = PocketState.OpeningLink,
        )
        GlanceLog.i("wired", "external cameras=${externalIds.size} ids=$externalIds")

        // 无公开 API 把 Camera2 摄像头 id 映射回 USB 设备；单台 Pocket 场景取首个外部摄像头即可。
        val cameraId = externalIds.first()
        val sizes = created.supportedSizes(cameraId).map { UvcSize(it.width, it.height) }
        val selected = UvcSelection.selectResolution(sizes)
        if (selected == null) {
            fail("外接摄像头未提供可用的预览分辨率。")
            return
        }
        pictureWidth = selected.width
        pictureHeight = selected.height
        GlanceLog.i(
            "wired",
            "external camera=$cameraId reportedSizes=${sizes.size} selected=${selected.width}x${selected.height}",
        )
        emit(
            phase = ConnectionPhase.Connecting,
            message = "正在配置外接摄像头 ${selected.width}×${selected.height}…",
            pocket = PocketState.OpeningLink,
        )

        // 预览帧已确定画幅；监视器据此提前准备 TextureView 变换。
        pendingSurface?.let(created::attachSurface)
        val opened = created.open(cameraId, Size(selected.width, selected.height))
        if (!opened) {
            if (!closed) fail("无法打开外接摄像头。")
            return
        }
        // Live 在 onConfigured（Camera2 会话配置完成）时切换，不在此处提前声称已出图。
    }

    override suspend fun disconnect() {
        tearDown(IdleReason.User)
    }

    override fun attachSurface(surface: Surface?) {
        pendingSurface = surface
        controller?.attachSurface(surface)
    }

    override fun notePresented(sourceTimestampNs: Long) {
        if (_state.value.phase != ConnectionPhase.Live || _state.value.pictureReady) return
        _state.value = _state.value.copy(pictureReady = true)
    }

    private fun onUvcConfigured() {
        if (closed) return
        GlanceLog.i("wired", "uvc session configured → Live ${pictureWidth}x$pictureHeight")
        _state.value =
            ConnectionState(
                mode = mode,
                phase = ConnectionPhase.Live,
                message = "有线 UVC 摄像头已连接",
                cameraVertical = pictureWidth < pictureHeight,
                cameraPoseRotation = PictureRotation.Upright,
                pictureWidth = pictureWidth,
                pictureHeight = pictureHeight,
                sourceWidth = pictureWidth,
                sourceHeight = pictureHeight,
                pictureReady = false,
                pocketState = PocketState.Monitoring,
                foundCameras = listOf(FoundCameraRow(id = "wired-uvc", title = "Pocket UVC 摄像头")),
            )
    }

    private fun onUvcDisconnected() {
        GlanceLog.i("wired", "uvc camera disconnected")
        tearDown(IdleReason.Detached)
    }

    private fun findPocketDevice(): UsbDevice? =
        usb.deviceList?.values?.firstOrNull { isPocketDevice(it) }

    private fun logUsbInventory() {
        val accessories = usb.accessoryList?.toList().orEmpty()
        GlanceLog.i("wired", "accessory count=${accessories.size}")
        accessories.forEach {
            GlanceLog.i("wired", "  accessory ${it.manufacturer} / ${it.model} / ${it.version}")
        }
        val devices = usb.deviceList?.values.orEmpty()
        GlanceLog.i("wired", "device count=${devices.size}")
        devices.forEach { device ->
            GlanceLog.i(
                "wired",
                "  device ${deviceLabel(device)} ifaces=${device.interfaceCount} " +
                    "pocket=${isPocketDevice(device)} uvc=${hasVideoInterface(device)}",
            )
        }
    }

    private fun logInterfaces(device: UsbDevice) {
        for (index in 0 until device.interfaceCount) {
            val intf = device.getInterface(index)
            GlanceLog.d(
                "wired",
                "iface[$index] class=0x${intf.interfaceClass.toString(16)} sub=0x${intf.interfaceSubclass.toString(16)} " +
                    "proto=0x${intf.interfaceProtocol.toString(16)} eps=${intf.endpointCount}",
            )
        }
    }

    private fun registerDetachWatcher() {
        if (detachReceiver != null) return
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
                    val device = intent.parcelableExtraCompat<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device != null && boundDevice?.deviceId == device.deviceId) {
                        tearDown(IdleReason.Detached)
                    }
                }
            }
        detachReceiver = receiver
        registerReceiver(receiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED))
    }

    private fun tearDown(reason: IdleReason) {
        if (closed) return
        closed = true
        controller?.close()
        controller = null
        pendingSurface = null
        detachReceiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        detachReceiver = null
        boundDevice = null
        _state.value =
            when (reason) {
                IdleReason.User -> ConnectionState(mode = mode, phase = ConnectionPhase.Idle)
                IdleReason.Detached ->
                    ConnectionState(
                        mode = mode,
                        phase = ConnectionPhase.Failed,
                        message = "USB 摄像头已断开。",
                        pocketState = PocketState.Failed,
                    )
            }
    }

    private fun fail(message: String) {
        GlanceLog.w("wired", "fail: $message")
        controller?.close()
        controller = null
        detachReceiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        detachReceiver = null
        boundDevice = null
        _state.value =
            ConnectionState(
                mode = mode,
                phase = ConnectionPhase.Failed,
                message = message,
                pocketState = PocketState.Failed,
            )
    }

    private fun emit(
        phase: ConnectionPhase,
        message: String,
        pocket: PocketState,
    ) {
        _state.value =
            ConnectionState(
                mode = mode,
                phase = phase,
                message = message,
                cameraVertical = false,
                cameraPoseRotation = PictureRotation.Upright,
                pictureWidth = pictureWidth,
                pictureHeight = pictureHeight,
                sourceWidth = pictureWidth,
                sourceHeight = pictureHeight,
                pocketState = pocket,
            )
    }

    private fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(receiver, filter)
        }
    }

    private enum class IdleReason { User, Detached }

    companion object {
        /** DJI vendor id observed on Pocket 4P host composite. */
        const val DJI_VENDOR_ID = 0x2CA3

        fun isPocketDevice(device: UsbDevice): Boolean {
            if (device.vendorId == DJI_VENDOR_ID) return true
            val blob =
                listOfNotNull(device.manufacturerName, device.productName, device.deviceName)
                    .joinToString(" ")
            return blob.contains("DJI", ignoreCase = true) ||
                blob.contains("Pocket", ignoreCase = true) ||
                blob.contains("Osmo", ignoreCase = true)
        }

        fun isPocketAccessory(accessory: UsbAccessory): Boolean {
            val blob = listOfNotNull(accessory.manufacturer, accessory.model, accessory.description).joinToString(" ")
            return blob.contains("DJI", ignoreCase = true) ||
                blob.contains("Pocket", ignoreCase = true) ||
                blob.contains("Osmo", ignoreCase = true)
        }

        /** 只识别带 UVC Video 接口（class 0x0E）的 Pocket 设备，用于 attach 触发的 UVC 尝试。 */
        fun isPocketUvcDevice(device: UsbDevice): Boolean =
            isPocketDevice(device) && hasVideoInterface(device)

        fun hasVideoInterface(device: UsbDevice): Boolean =
            UvcSelection.hasVideoInterface(
                (0 until device.interfaceCount).map { index ->
                    val intf = device.getInterface(index)
                    UvcSelection.UsbInterfaceSpec(
                        interfaceClass = intf.interfaceClass,
                        interfaceSubclass = intf.interfaceSubclass,
                        interfaceProtocol = intf.interfaceProtocol,
                    )
                },
            )

        fun deviceLabel(device: UsbDevice): String {
            val name = device.productName?.takeIf { it.isNotBlank() }
                ?: device.deviceName.takeIf { it.isNotBlank() }
                ?: "DJI ${device.deviceId}"
            return "$name (VID=${device.vendorId.toString(16)} PID=${device.productId.toString(16)})"
        }
    }
}

@Suppress("DEPRECATION")
private inline fun <reified T : Any> Intent.parcelableExtraCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, T::class.java)
    } else {
        getParcelableExtra(key) as? T
    }
