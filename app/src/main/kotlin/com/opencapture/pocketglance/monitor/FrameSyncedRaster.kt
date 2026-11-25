package com.opencapture.pocketglance.monitor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PictureRaster(val width: Int, val height: Int)

internal data class RasterState(val source: PictureRaster, val presented: PictureRaster? = null) {
    val display: PictureRaster get() = presented ?: source
}

/** Prepare the next decoder raster without resizing the picture already on screen. */
internal class FrameSyncedRaster(width: Int, height: Int, startedAtNs: Long) {
    private val _state = MutableStateFlow(RasterState(PictureRaster(width, height)))
    val state = _state.asStateFlow()

    private var sourceObservedAtNs = startedAtNs
    private var lastPresentedFrameNs = startedAtNs

    @Synchronized
    fun observe(width: Int, height: Int, observedAtNs: Long) {
        val current = _state.value
        if (width <= 0 || height <= 0 || observedAtNs < sourceObservedAtNs ||
            (width == current.source.width && height == current.source.height)
        ) return
        sourceObservedAtNs = observedAtNs
        _state.value = current.copy(source = PictureRaster(width, height))
    }

    @Synchronized
    fun present(frameNs: Long): PictureRaster? {
        // A redraw may hand us the same old buffer; each actual decoder buffer has a nanoTime stamp.
        if (frameNs <= 0L || frameNs <= lastPresentedFrameNs) return null
        lastPresentedFrameNs = frameNs
        val current = _state.value
        if (frameNs < sourceObservedAtNs || current.presented == current.source) return null
        _state.value = current.copy(presented = current.source)
        return current.source
    }
}
