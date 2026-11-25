package com.opencapture.pocketglance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencapture.pocketglance.connection.ConnectionPhase
import com.opencapture.pocketglance.connection.ConnectionState
import com.opencapture.pocketglance.connection.PocketState
import com.opencapture.pocketglance.connection.MonitorFocusLock

@Composable
fun MonitorScreen(
    connection: ConnectionState,
    startingFeed: Boolean,
    onUnlockFocus: () -> Unit,
    onStopTracking: () -> Unit,
    activeLut: String?,
    onOpenLuts: () -> Unit,
    onGallery: () -> Unit,
    onSettings: () -> Unit,
    onRetryRecovery: () -> Unit,
    onDisconnect: () -> Unit,
    onHome: () -> Unit,
) {
    val chrome = connection.chrome
    val inkSoft = Color.White.copy(alpha = 0.78f)

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
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 顶栏不加整条黑底：文字直接叠在画面上；录制态单独小胶囊。
            MonitorTopBar(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                start = {
                    val summary =
                        listOf(
                            chrome.shootingModeLabel,
                            chrome.formatChip.takeIf { chrome.isPhoto || it != "—" },
                            chrome.colorChip,
                            chrome.evChip,
                            chrome.isoChip,
                            chrome.wbChip,
                        ).filterNot { it.isNullOrBlank() || it == "—" }
                            .joinToString("   ·   ")
                    Text(
                        summary,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                center = {
                    if (chrome.isRecording) {
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(GlanceColors.coral))
                            Text(MonitorChromeLabels.formatElapsed(chrome.recordElapsedSec), color = Color.White,
                                fontSize = 13.sp, maxLines = 1, softWrap = false)
                        }
                    }
                },
                end = {
                    TextButton(onClick = onDisconnect, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("断开连接", color = GlanceColors.pear, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
            )

            val showFocus = connection.focus.available && !startingFeed
            val focus = connection.focus
            if (showFocus && (focus.pending || focus.lock != null || focus.notice != null || focus.trackBox != null)) {
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
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

@Composable
private fun MonitorTopBar(
    modifier: Modifier,
    start: @Composable () -> Unit,
    center: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Box { start() }
            Box { center() }
            Box { end() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val recording = measurables[1].measure(childConstraints)
        val gap = 6.dp.roundToPx()
        // Reserve the same space on both sides of the timer, regardless of label widths.
        val sideWidth = ((width - recording.width) / 2 - gap).coerceAtLeast(0)
        val endButton = measurables[2].measure(
            childConstraints.copy(maxWidth = if (recording.width > 0) sideWidth else width),
        )
        val summary = measurables[0].measure(
            childConstraints.copy(maxWidth = if (recording.width > 0) sideWidth else
                (width - endButton.width - gap).coerceAtLeast(0)),
        )
        val height = maxOf(summary.height, recording.height, endButton.height, constraints.minHeight)
        layout(width, height) {
            summary.placeRelative(0, (height - summary.height) / 2)
            recording.placeRelative((width - recording.width) / 2, (height - recording.height) / 2)
            endButton.placeRelative(width - endButton.width, (height - endButton.height) / 2)
        }
    }
}
