package com.opencapture.pocketglance.session

import com.opencapture.opencine.dji.DjiNative

/**
 * 加载与 OpenPocketCine 相同的 Swift JNI（`libOpenPocketCineAndroid.so`）。
 * 不调用则 [DjiNative.isAvailable] 恒为 false，无线扫描无法启动。
 */
object SwiftCoreBootstrap {
    private const val LIBRARY = "OpenPocketCineAndroid"

    val isAvailable: Boolean by lazy { DjiNative.load(LIBRARY) }
}
