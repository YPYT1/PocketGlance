package com.opencapture.pocketglance.monitor

import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints

internal data class MonitorDisplay(val landscape: Boolean, val rotation: Int) {
    val portraitFallbackDegrees: Float get() = if (rotation == Surface.ROTATION_90) -90f else 90f
}

@Composable
internal fun rememberMonitorDisplay(): MonitorDisplay {
    val view = LocalView.current
    val manager = LocalContext.current.getSystemService(DisplayManager::class.java)
    fun readDisplay(): MonitorDisplay {
        val display = view.display ?: manager?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return MonitorDisplay(false, Surface.ROTATION_0)
        val rotation = display.rotation
        val mode = display.mode
        val sideways = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
        return MonitorDisplay(if (sideways) mode.physicalHeight > mode.physicalWidth else mode.physicalWidth > mode.physicalHeight, rotation)
    }
    var state by remember(view) { mutableStateOf(readDisplay()) }
    DisposableEffect(view, manager) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (view.display?.displayId == displayId) state = readDisplay()
            }
        }
        manager?.registerDisplayListener(listener, null)
        state = readDisplay()
        onDispose { manager?.unregisterDisplayListener(listener) }
    }
    return state
}

internal fun Modifier.fullScreenPortraitFallback(degrees: Float): Modifier = layout { measurable, constraints ->
    if (degrees == 0f || !constraints.hasBoundedWidth || !constraints.hasBoundedHeight ||
        constraints.maxWidth <= constraints.maxHeight) {
        val content = measurable.measure(constraints)
        layout(content.width, content.height) { content.place(0, 0) }
    } else {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val content = measurable.measure(Constraints.fixed(height, width))
        layout(width, height) {
            content.placeWithLayer((width - height) / 2, (height - width) / 2) { rotationZ = degrees }
        }
    }
}
