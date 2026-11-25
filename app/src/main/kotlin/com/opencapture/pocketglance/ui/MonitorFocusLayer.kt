package com.opencapture.pocketglance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.dp
import com.opencapture.pocketglance.connection.FocusGesture
import com.opencapture.pocketglance.connection.MonitorFocus
import com.opencapture.pocketglance.monitor.MonitorLayout
import com.opencapture.pocketglance.monitor.PictureRotation
import kotlin.math.abs

@Composable
internal fun MonitorFocusLayer(
    aspect: Float,
    rotation: PictureRotation,
    focus: MonitorFocus,
    onFocus: (FocusGesture, Float, Float) -> Unit,
) {
    val system = LocalViewConfiguration.current
    val gestures = remember(system) {
        object : ViewConfiguration by system {
            override val longPressTimeoutMillis = 1500L
        }
    }
    val callback = rememberUpdatedState(onFocus)
    CompositionLocalProvider(LocalViewConfiguration provides gestures) {
        Box(Modifier.fillMaxSize().pointerInput(aspect, rotation) {
            fun send(gesture: FocusGesture, position: Offset) {
                MonitorLayout.picturePoint(position.x, position.y, size.width.toFloat(), size.height.toFloat(), aspect, rotation)
                    ?.let { (x, y) -> callback.value(gesture, x, y) }
            }
            detectTapGestures(
                onTap = { send(FocusGesture.Tap, it) },
                onDoubleTap = { send(FocusGesture.Track, it) },
                onLongPress = { send(FocusGesture.Hold, it) },
            )
        }) {
            Canvas(Modifier.fillMaxSize()) {
                fun screen(x: Float, y: Float): Offset {
                    val (screenX, screenY) = MonitorLayout.screenPoint(x, y, size.width, size.height, aspect, rotation)
                    return Offset(screenX, screenY)
                }
                val trackBox = focus.trackBox
                if (trackBox != null) {
                    val start = screen(trackBox.x.toFloat(), trackBox.y.toFloat())
                    val end = screen((trackBox.x + trackBox.width).toFloat(), (trackBox.y + trackBox.height).toFloat())
                    drawRect(
                        if (focus.tracking) Color(0xFF72DB82) else GlanceColors.pear,
                        Offset(minOf(start.x, end.x), minOf(start.y, end.y)),
                        Size(abs(end.x - start.x), abs(end.y - start.y)),
                        style = Stroke(2.dp.toPx()),
                    )
                } else focus.point?.let { (x, y) ->
                    val center = screen(x, y)
                    val radius = 25.dp.toPx()
                    drawRect(GlanceColors.pear, center - Offset(radius, radius), Size(radius * 2, radius * 2), style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}
