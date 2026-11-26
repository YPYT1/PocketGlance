package com.opencapture.pocketglance.connection

import kotlin.math.abs

/**
 * UVC 网络摄像头选择的纯函数部分：不依赖 Android 类型，便于 JVM 单测。
 *
 * 「UVC」指 Pocket 4P 的「网络摄像头／UVC」模式：机身作为一个标准 USB Video
 * Class 设备对外，Android 通过 Camera HAL 将其暴露为 LENS_FACING_EXTERNAL 摄像头。
 */
data class UvcSize(val width: Int, val height: Int) {
    val area: Long get() = width.toLong() * height.coerceAtLeast(1)
    val aspect: Float get() = width.toFloat() / height.coerceAtLeast(1)
}

object UvcSelection {
    /** USB Video 接口类（class 0x0E）。这是判定「是 UVC 摄像头」的唯一接口信号。 */
    const val USB_CLASS_VIDEO = 0x0E

    /** 预览分辨率长边不超过 1920、短边不超过 1080；横竖方向都按此限制。 */
    const val MAX_LONG_SIDE = 1920
    const val MAX_SHORT_SIDE = 1080

    /** 一个 USB 接口的 class/subclass/protocol 快照，避免单测直接依赖 UsbInterface。 */
    data class UsbInterfaceSpec(
        val interfaceClass: Int,
        val interfaceSubclass: Int,
        val interfaceProtocol: Int,
    )

    fun isVideoInterface(interfaceClass: Int, interfaceSubclass: Int, interfaceProtocol: Int): Boolean =
        interfaceClass == USB_CLASS_VIDEO

    /** Pocket 复合设备里只要有一个 Video 接口，就认为它是 UVC 摄像头形态。 */
    fun hasVideoInterface(interfaces: Collection<UsbInterfaceSpec>): Boolean =
        interfaces.any { isVideoInterface(it.interfaceClass, it.interfaceSubclass, it.interfaceProtocol) }

    /**
     * 从 Camera2 报告的稳定输出尺寸里选一个预览分辨率：
     * - 长边最多 1920、短边最多 1080（把 4K 等大尺寸排除，避免无谓的带宽）；
     * - 优先横向或纵向 16:9；同宽高比内取面积最大者。
     * 尺寸来源是 `SCALER_STREAM_CONFIGURATION_MAP.getOutputSizes(SurfaceTexture)`，
     * 即 HAL 声明可用于预览的稳定集合，不包含 still-capture 尺寸。
     */
    fun selectResolution(
        sizes: Collection<UvcSize>,
        maxLongSide: Int = MAX_LONG_SIDE,
        maxShortSide: Int = MAX_SHORT_SIDE,
    ): UvcSize? {
        val capped = sizes.filter { size ->
            val longSide = maxOf(size.width, size.height)
            val shortSide = minOf(size.width, size.height)
            longSide in 1..maxLongSide && shortSide in 1..maxShortSide
        }
        if (capped.isEmpty()) return null
        val sixteenNine = capped.filter { isSixteenNine(it.width, it.height) }
        val pool = sixteenNine.ifEmpty { capped }
        // Preserve the camera's native orientation; the user has not requested rotation.
        val candidates = pool.filter { it.width >= it.height }.ifEmpty { pool }
        return candidates.maxWithOrNull(compareBy<UvcSize> { it.area }.thenBy { it.width })
    }

    /** 16:9 判定，带小容差以容忍个别厂商的近似尺寸。 */
    fun isSixteenNine(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        return abs(maxOf(width, height).toFloat() / minOf(width, height) - 16f / 9f) < 0.02f
    }
}
