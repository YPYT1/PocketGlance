package com.opencapture.pocketglance.connection

import android.content.Context
import android.hardware.usb.UsbManager
import android.view.Surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * USB AOA entry for Pocket wired monitor. Enumerates accessories and owns the
 * same [ConnectionState] surface as SoftAP. Live HEVC over AOA is not in-tree
 * yet; after attach we report a clear status and keep disconnect/switch working.
 */
class WiredUsbTransport(
    context: Context,
) : ConnectionTransport {
    override val mode: ConnectionMode = ConnectionMode.Wired

    private val appContext = context.applicationContext
    private val usb = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val _state = MutableStateFlow(ConnectionState(mode = mode))
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    override suspend fun connect() {
        _state.value =
            ConnectionState(mode = mode, phase = ConnectionPhase.Connecting, message = "正在查找 USB 附件…")
        delay(400)
        val accessories = usb.accessoryList?.toList().orEmpty()
        val dji =
            accessories.filter {
                val mfg = it.manufacturer.contains("DJI", ignoreCase = true)
                val model =
                    it.model.contains("Pocket", ignoreCase = true) ||
                        it.model.contains("Osmo", ignoreCase = true)
                mfg || model
            }
        if (dji.isEmpty() && accessories.isEmpty()) {
            _state.value =
                ConnectionState(
                    mode = mode,
                    phase = ConnectionPhase.Failed,
                    message = "未检测到 USB 附件。请用转接线连接 Pocket，或改用无线连接。",
                )
            return
        }
        // 附件已出现：有线 AOA 出图尚未接入，不假装已直播。
        _state.value =
            ConnectionState(
                mode = mode,
                phase = ConnectionPhase.Failed,
                message =
                    if (dji.isNotEmpty() || accessories.isNotEmpty()) {
                        "已检测到 USB 附件，但有线直播尚未接入解码。可断开后改用无线。"
                    } else {
                        "有线连接未就绪。"
                    },
            )
    }

    override suspend fun disconnect() {
        _state.value = ConnectionState(mode = mode, phase = ConnectionPhase.Idle)
    }

    override fun attachSurface(surface: Surface?) = Unit

    fun hasAccessoryAttached(): Boolean = !usb.accessoryList.isNullOrEmpty()
}
