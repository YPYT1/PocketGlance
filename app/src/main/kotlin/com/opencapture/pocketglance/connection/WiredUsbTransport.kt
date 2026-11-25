package com.opencapture.pocketglance.connection

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbAccessory
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.Surface
import com.opencapture.pocketglance.diagnostics.GlanceLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pocket 有线基础会话：OTG 主机（VID 0x2CA3 复合设备）或 AOA 附件。
 * 打开并持有连接／厂商 Bulk；直播码流尚未接入，但会话进入 Live 以便共用监视壳。
 */
class WiredUsbTransport(
    context: Context,
    private val seedDevice: UsbDevice? = null,
    private val seedAccessory: UsbAccessory? = null,
) : ConnectionTransport {
    override val mode: ConnectionMode = ConnectionMode.Wired

    private val appContext = context.applicationContext
    private val usb = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val _state = MutableStateFlow(ConnectionState(mode = mode))
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var deviceConnection: UsbDeviceConnection? = null
    private var claimedInterfaces: List<UsbInterface> = emptyList()
    private var boundDevice: UsbDevice? = null
    private var accessoryFd: ParcelFileDescriptor? = null
    private var boundAccessory: UsbAccessory? = null
    private var permissionReceiver: BroadcastReceiver? = null
    private var detachReceiver: BroadcastReceiver? = null
    private var closed = false

    override suspend fun connect() {
        if (closed) return
        GlanceLog.i("wired", "connect start seedDevice=${seedDevice?.deviceName} seedAccessory=${seedAccessory?.model}")
        logUsbInventory()
        emit(
            phase = ConnectionPhase.Connecting,
            message = "正在查找 USB 设备…",
            pocket = PocketState.Discovering,
        )
        registerDetachWatcher()

        val accessory = seedAccessory?.takeIf { isPocketAccessory(it) } ?: findAccessory()
        if (accessory != null) {
            GlanceLog.i("wired", "try accessory ${accessory.manufacturer} ${accessory.model}")
            if (!ensureAccessoryPermission(accessory)) {
                fail("未获得 USB 附件权限。请在系统弹窗中允许后重试。")
                return
            }
            if (openAccessorySession(accessory)) return
        }

        val device = seedDevice?.takeIf { isPocketDevice(it) } ?: findDevice()
        if (device != null) {
            GlanceLog.i("wired", "try host ${deviceLabel(device)}")
            if (!ensureDevicePermission(device)) {
                fail("未获得 USB 设备权限。请在系统弹窗中允许后重试。")
                return
            }
            if (openHostSession(device)) return
        }

        fail(
            "未检测到 Pocket USB。" +
                "请用 OTG 线接到本机（主机模式），或让相机以附件模式连接；" +
                "电脑上的「文件传输」挂载与手机监视不是同一条路径。",
        )
    }

    override suspend fun disconnect() {
        tearDown(IdleReason.User)
    }

    override fun attachSurface(surface: Surface?) = Unit

    private fun findAccessory(): UsbAccessory? =
        usb.accessoryList?.firstOrNull { isPocketAccessory(it) }

    private fun findDevice(): UsbDevice? =
        usb.deviceList?.values?.firstOrNull { isPocketDevice(it) }

    private fun openAccessorySession(accessory: UsbAccessory): Boolean {
        val fd = runCatching { usb.openAccessory(accessory) }.getOrNull()
        if (fd == null) {
            GlanceLog.e("wired", "openAccessory failed ${accessory.model}")
            fail("无法打开 USB 附件 ${accessory.model}。")
            return true
        }
        accessoryFd = fd
        boundAccessory = accessory
        GlanceLog.i("wired", "accessory session open fd=${fd.fd}")
        emit(
            phase = ConnectionPhase.Live,
            message = "有线附件会话已建立（${accessory.manufacturer} ${accessory.model}）。直播码流尚未接入。",
            pocket = PocketState.Monitoring,
            cameraTitle = "${accessory.manufacturer} ${accessory.model}",
        )
        return true
    }

    private fun openHostSession(device: UsbDevice): Boolean {
        val connection = runCatching { usb.openDevice(device) }.getOrNull()
        if (connection == null) {
            GlanceLog.e("wired", "openDevice failed ${deviceLabel(device)}")
            fail("无法打开 USB 设备 ${deviceLabel(device)}。")
            return true
        }
        val claimed = ArrayList<UsbInterface>()
        for (index in 0 until device.interfaceCount) {
            val intf = device.getInterface(index)
            GlanceLog.d(
                "wired",
                "iface[$index] class=${intf.interfaceClass} sub=${intf.interfaceSubclass} " +
                    "proto=${intf.interfaceProtocol} eps=${intf.endpointCount}",
            )
            for (ep in 0 until intf.endpointCount) {
                val endpoint = intf.getEndpoint(ep)
                GlanceLog.d(
                    "wired",
                    "  ep[$ep] addr=0x${endpoint.address.toString(16)} type=${endpoint.type} " +
                        "dir=${endpoint.direction} max=${endpoint.maxPacketSize}",
                )
            }
            // 只声明厂商 Bulk（Mac 上 IF 3–7：class 255 / sub 67），避开 MSC／RNDIS 系统占用。
            if (intf.interfaceClass != UsbConstants.USB_CLASS_VENDOR_SPEC) continue
            val ok = connection.claimInterface(intf, true)
            GlanceLog.i("wired", "claim vendor iface[$index] -> $ok")
            if (ok) claimed += intf
        }
        deviceConnection = connection
        claimedInterfaces = claimed
        boundDevice = device
        val title = deviceLabel(device)
        GlanceLog.i("wired", "host session open claimed=${claimed.size}/${device.interfaceCount} $title")
        emit(
            phase = ConnectionPhase.Live,
            message =
                "有线主机会话已建立（$title，厂商接口 ${claimed.size}/${device.interfaceCount}）。" +
                    "直播码流尚未接入。",
            pocket = PocketState.Monitoring,
            cameraTitle = title,
        )
        return true
    }

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
                    "pocket=${isPocketDevice(device)}",
            )
        }
    }

    private suspend fun ensureDevicePermission(device: UsbDevice): Boolean {
        if (usb.hasPermission(device)) return true
        return requestPermission(device = device, accessory = null)
    }

    private suspend fun ensureAccessoryPermission(accessory: UsbAccessory): Boolean {
        if (usb.hasPermission(accessory)) return true
        return requestPermission(device = null, accessory = accessory)
    }

    private suspend fun requestPermission(device: UsbDevice?, accessory: UsbAccessory?): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action != ACTION_USB_PERMISSION) return
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (!deferred.isCompleted) deferred.complete(granted)
                }
            }
        permissionReceiver = receiver
        registerReceiver(receiver, filter)
        val piFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi =
            PendingIntent.getBroadcast(
                appContext,
                0,
                Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName),
                piFlags,
            )
        emit(
            phase = ConnectionPhase.Connecting,
            message = "等待 USB 权限…",
            pocket = PocketState.Pairing,
        )
        when {
            device != null -> usb.requestPermission(device, pi)
            accessory != null -> usb.requestPermission(accessory, pi)
            else -> return false
        }
        val granted =
            withTimeoutOrNull(45_000) { deferred.await() } == true
        unregisterPermission()
        return granted && !closed
    }

    private fun registerDetachWatcher() {
        if (detachReceiver != null) return
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                            val device =
                                intent.parcelableExtraCompat<UsbDevice>(UsbManager.EXTRA_DEVICE)
                            if (device != null && boundDevice?.deviceId == device.deviceId) {
                                tearDown(IdleReason.Detached)
                            }
                        }
                        UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
                            val accessory =
                                intent.parcelableExtraCompat<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
                            if (accessory != null && boundAccessory != null &&
                                accessory.manufacturer == boundAccessory?.manufacturer &&
                                accessory.model == boundAccessory?.model
                            ) {
                                tearDown(IdleReason.Detached)
                            }
                        }
                    }
                }
            }
        detachReceiver = receiver
        val filter =
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
            }
        registerReceiver(receiver, filter)
    }

    private fun tearDown(reason: IdleReason) {
        closed = true
        unregisterPermission()
        detachReceiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        detachReceiver = null
        val connection = deviceConnection
        deviceConnection = null
        for (intf in claimedInterfaces) {
            runCatching { connection?.releaseInterface(intf) }
        }
        claimedInterfaces = emptyList()
        runCatching { connection?.close() }
        boundDevice = null
        runCatching { accessoryFd?.close() }
        accessoryFd = null
        boundAccessory = null
        _state.value =
            when (reason) {
                IdleReason.User -> ConnectionState(mode = mode, phase = ConnectionPhase.Idle)
                IdleReason.Detached ->
                    ConnectionState(
                        mode = mode,
                        phase = ConnectionPhase.Failed,
                        message = "USB 已断开。",
                        pocketState = PocketState.Failed,
                    )
            }
    }

    private fun fail(message: String) {
        GlanceLog.w("wired", "fail: $message")
        unregisterPermission()
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
        cameraTitle: String? = null,
    ) {
        _state.value =
            ConnectionState(
                mode = mode,
                phase = phase,
                message = message,
                pocketState = pocket,
                foundCameras =
                    cameraTitle?.let { listOf(FoundCameraRow(id = "wired", title = it)) }.orEmpty(),
            )
    }

    private fun unregisterPermission() {
        permissionReceiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        permissionReceiver = null
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
        const val ACTION_USB_PERMISSION = "com.opencapture.pocketglance.USB_PERMISSION"
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

        fun deviceLabel(device: UsbDevice): String {
            val name = device.productName?.takeIf { it.isNotBlank() }
                ?: device.deviceName?.takeIf { it.isNotBlank() }
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
