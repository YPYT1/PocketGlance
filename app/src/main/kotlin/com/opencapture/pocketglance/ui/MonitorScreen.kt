package com.opencapture.pocketglance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.opencapture.pocketglance.connection.ConnectionPhase
import com.opencapture.pocketglance.connection.ConnectionState

/** 监视页只画控件；画面由根上的 [LiveFeedHost] 常驻提供。方向由根 Activity 在 Live 期间锁定。 */
@Composable
fun MonitorScreen(
    connection: ConnectionState,
    onGallery: () -> Unit,
    onSettings: () -> Unit,
    onDisconnect: () -> Unit,
    onHome: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (connection.phase != ConnectionPhase.Live) {
            Text(
                connection.message.ifBlank { "等待画面…" },
                color = GlanceColors.canvas,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            Text(
                GlanceCopy.mode(connection.mode) +
                    if (connection.cameraVertical) " · 竖拍" else " · 横拍",
                color = GlanceColors.canvas,
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlanceColors.azure.copy(alpha = 0.85f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            TextButton(onClick = onDisconnect, modifier = Modifier.align(Alignment.CenterEnd)) {
                Text("断开", color = GlanceColors.coral)
            }
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            TextButton(onClick = onHome, modifier = Modifier.align(Alignment.CenterStart)) {
                Text("连接", color = GlanceColors.canvas)
            }
            TextButton(onClick = onSettings, modifier = Modifier.align(Alignment.Center)) {
                Text("设置", color = GlanceColors.canvas)
            }
            TextButton(onClick = onGallery, modifier = Modifier.align(Alignment.CenterEnd)) {
                Text("相册", color = GlanceColors.pear)
            }
        }
    }
}
