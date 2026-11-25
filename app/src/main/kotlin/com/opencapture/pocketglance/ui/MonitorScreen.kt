package com.opencapture.pocketglance.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencapture.pocketglance.connection.ConnectionPhase
import com.opencapture.pocketglance.connection.ConnectionState
import com.opencapture.pocketglance.connection.PocketState

@Composable
fun MonitorScreen(
    connection: ConnectionState,
    onGallery: () -> Unit,
    onSettings: () -> Unit,
    onRetryRecovery: () -> Unit,
    onDisconnect: () -> Unit,
    onHome: () -> Unit,
) {
    val chrome = connection.chrome
    val inkSoft = Color.White.copy(alpha = 0.78f)
    val inkFaint = Color.White.copy(alpha = 0.45f)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showLandscapeControls by remember(landscape) { mutableStateOf(false) }
    val showControls = !landscape || showLandscapeControls || connection.pocketState == PocketState.NeedsRetry
    Box(modifier = Modifier.fillMaxSize()) {
        if (landscape) {
            Box(
                Modifier.fillMaxSize().clickable(
                    onClickLabel = if (showLandscapeControls) "隐藏监视控件" else "显示监视控件",
                    onClick = { showLandscapeControls = !showLandscapeControls },
                ),
            )
        }
        if (connection.pocketState == PocketState.Recovering || connection.pocketState == PocketState.NeedsRetry) {
            Column(
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.7f)).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(connection.message, color = Color.White)
                if (connection.pocketState == PocketState.NeedsRetry) {
                    TextButton(onClick = onRetryRecovery) { Text("重试连接", color = GlanceColors.pear) }
                }
            }
        } else if (connection.phase != ConnectionPhase.Live) {
            Text(
                connection.message.ifBlank { "等待画面…" },
                color = GlanceColors.canvas,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (!connection.pictureReady) {
            Text(
                "正在等待首帧…",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (!showControls) return@Box
        Row(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
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
