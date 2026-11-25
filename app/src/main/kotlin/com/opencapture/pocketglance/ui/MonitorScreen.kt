package com.opencapture.pocketglance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencapture.pocketglance.connection.ConnectionPhase
import com.opencapture.pocketglance.connection.ConnectionState
import com.opencapture.pocketglance.connection.PocketState
import com.opencapture.pocketglance.connection.MonitorFocusLock
import com.opencapture.pocketglance.monitor.PictureRotation

@Composable
fun MonitorScreen(
    connection: ConnectionState,
    picturePortrait: Boolean,
    rotationOverride: PictureRotation?,
    onRotationChange: (PictureRotation?) -> Unit,
    startingFeed: Boolean,
    onUnlockFocus: () -> Unit,
    onStopTracking: () -> Unit,
    activeLut: String?,
    raster: String?,
    onOpenLuts: () -> Unit,
    onGallery: () -> Unit,
    onSettings: () -> Unit,
    onRetryRecovery: () -> Unit,
    onHome: () -> Unit,
) {
    val chrome = connection.chrome
    val inkSoft = Color.White.copy(alpha = 0.78f)
    var showRotation by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().displayCutoutPadding()) {
        if (connection.phase == ConnectionPhase.Live && connection.pictureReady && startingFeed && connection.pocketState == PocketState.Monitoring) {
            Box(Modifier.fillMaxSize().background(GlanceColors.ink), contentAlignment = Alignment.Center) {
                Text("正在稳定直播画面…", color = inkSoft, fontSize = 14.sp)
            }
        }
        if (connection.pocketState == PocketState.Recovering || connection.pocketState == PocketState.NeedsRetry ||
            connection.pocketState == PocketState.TakenOver) {
            Column(
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.7f)).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(connection.message, color = Color.White)
                if (connection.pocketState == PocketState.NeedsRetry || connection.pocketState == PocketState.TakenOver) {
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

        if (chrome.photoTimerRemainingSeconds > 0) {
            Text(
                chrome.photoTimerRemainingSeconds.toString(),
                color = Color.White,
                fontSize = 78.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 30.dp, vertical = 8.dp),
            )
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val summary = if (chrome.isPhoto) chrome.shootingModeLabel else
                    listOf(chrome.shootingModeLabel, chrome.formatChip, chrome.colorChip).filterNot { it == "—" }.joinToString("  ")
                Text(summary, Modifier.weight(1f, fill = false), color = Color.White, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (chrome.isRecording) {
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(GlanceColors.coral))
                        Text(MonitorChromeLabels.formatElapsed(chrome.recordElapsedSec), color = Color.White, fontSize = 13.sp, maxLines = 1)
                    }
                }
                if (connection.pictureReady) {
                    Box {
                        TextButton(onClick = { showRotation = true }) {
                            Text(if (picturePortrait) "竖向" else "横向", color = GlanceColors.pear, fontSize = 12.sp)
                        }
                        DropdownMenu(expanded = showRotation, onDismissRequest = { showRotation = false }) {
                            (listOf<PictureRotation?>(null) + PictureRotation.entries).forEach { rotation ->
                                val label = rotation?.label ?: "自动识别"
                                DropdownMenuItem(
                                    text = { Text(if (rotation == rotationOverride) "$label（当前）" else label) },
                                    onClick = {
                                        onRotationChange(rotation)
                                        showRotation = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (chrome.isPhoto) {
                Text(chrome.formatChip, color = inkSoft, fontSize = 12.sp)
            }
            raster?.let { Text("直播 $it", color = inkSoft, fontSize = 12.sp) }
            if (connection.focus.available && !startingFeed) {
                val focus = connection.focus
                when {
                    focus.pending -> Text("正在调整对焦…", color = inkSoft, fontSize = 12.sp)
                    focus.lock != null -> TextButton(onClick = onUnlockFocus) {
                        Text(if (focus.lock == MonitorFocusLock.Native) "对焦已锁定 · 点此解锁" else "单次对焦保持 · 点此解除", color = GlanceColors.pear)
                    }
                    focus.notice != null -> Text(focus.notice, color = GlanceColors.pear, fontSize = 12.sp)
                    focus.trackBox != null -> TextButton(onClick = onStopTracking) {
                        Text(if (focus.tracking) "正在追踪 · 停止" else "寻找目标 · 取消", color = GlanceColors.pear)
                    }
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onHome) {
                Text("首页", color = Color.White.copy(alpha = 0.9f))
            }
            if (chrome.supportsPreviewLut && connection.phase == ConnectionPhase.Live) {
                TextButton(onClick = onOpenLuts) {
                    Text(activeLut ?: "LUT", color = GlanceColors.pear, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 92.dp))
                }
            }
            TextButton(onClick = onSettings) { Text("设置", color = Color.White.copy(alpha = 0.9f)) }
            TextButton(onClick = onGallery, enabled = !chrome.isRecording) {
                Text("相册", color = GlanceColors.pear)
            }
        }
    }
}
