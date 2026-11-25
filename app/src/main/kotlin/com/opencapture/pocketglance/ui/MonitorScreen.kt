package com.opencapture.pocketglance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencapture.pocketglance.connection.ConnectionPhase
import com.opencapture.pocketglance.connection.ConnectionState
import kotlinx.coroutines.delay

/** 监视页只画控件；画面由根上的 [LiveFeedHost] 常驻提供。方向由根 Activity 在 Live 期间锁定。 */
@Composable
fun MonitorScreen(
    connection: ConnectionState,
    onGallery: () -> Unit,
    onSettings: () -> Unit,
    onDisconnect: () -> Unit,
    onHome: () -> Unit,
) {
    val chrome = connection.chrome
    val inkSoft = Color.White.copy(alpha = 0.78f)
    val inkFaint = Color.White.copy(alpha = 0.45f)
    var settling by remember(connection.phase) { mutableStateOf(connection.phase == ConnectionPhase.Live) }
    LaunchedEffect(connection.phase) {
        if (connection.phase == ConnectionPhase.Live) {
            settling = true
            // Pocket 首帧会 format poke（4K↔1080），约数秒后才稳。
            delay(2800)
            settling = false
        } else {
            settling = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (connection.phase != ConnectionPhase.Live) {
            Text(
                connection.message.ifBlank { "等待画面…" },
                color = GlanceColors.canvas,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (settling) {
            Text(
                "正在稳定画面…",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Row(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.28f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (chrome.isRecording) {
                    Box(
                        modifier =
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(GlanceColors.coral),
                    )
                    Text(
                        MonitorChromeLabels.formatElapsed(chrome.recordElapsedSec),
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                    MonitorChromeLabels.formatRemaining(chrome.recordRemainingSec)?.let { rem ->
                        Text(rem, color = inkFaint, fontSize = 12.sp)
                    }
                    Text("·", color = inkFaint, fontSize = 12.sp)
                }
                Text(chrome.shootingModeLabel, color = Color.White, fontSize = 13.sp)
                if (chrome.formatChip != "—") {
                    Text("·", color = inkFaint, fontSize = 12.sp)
                    Text(chrome.formatChip, color = inkSoft, fontSize = 12.sp)
                }
                if (chrome.colorChip != "—") {
                    Text("·", color = inkFaint, fontSize = 12.sp)
                    Text(chrome.colorChip, color = inkSoft, fontSize = 12.sp)
                }
                Text("·", color = inkFaint, fontSize = 12.sp)
                Text(
                    GlanceCopy.mode(connection.mode),
                    color = inkFaint,
                    fontSize = 12.sp,
                )
                Text(
                    if (connection.cameraVertical) "竖" else "横",
                    color = inkFaint,
                    fontSize = 12.sp,
                )
            }
            TextButton(onClick = onDisconnect) {
                Text("断开", color = GlanceColors.coral)
            }
        }

        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onHome) {
                Text("连接", color = Color.White.copy(alpha = 0.9f))
            }
            TextButton(onClick = onSettings) {
                Text("设置", color = Color.White.copy(alpha = 0.75f))
            }
            TextButton(onClick = onGallery) {
                Text("相册", color = GlanceColors.pear)
            }
        }
    }
}
