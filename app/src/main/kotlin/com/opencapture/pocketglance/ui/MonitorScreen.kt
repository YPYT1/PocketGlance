package com.opencapture.pocketglance.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.opencapture.pocketglance.connection.ConnectionMode
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
    val configuration = LocalConfiguration.current
    val portrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val phone = configuration.smallestScreenWidthDp < 600

    val primaryChips =
        remember(chrome.shootingModeLabel, chrome.formatChip, chrome.colorChip, chrome.isPhoto) {
            listOfNotNull(
                chrome.shootingModeLabel.takeUnless { it.isBlank() || it == "—" },
                chrome.formatChip.takeIf { (chrome.isPhoto || it != "—") && it.isNotBlank() },
                chrome.colorChip.takeUnless { it.isBlank() || it == "—" },
            )
        }
    val secondaryChips =
        remember(chrome.evChip, chrome.isoChip, chrome.wbChip) {
            listOf(chrome.evChip, chrome.isoChip, chrome.wbChip)
                .filter { it.isNotBlank() && it != "—" }
        }
    // 芯片两排仅竖屏；录制时长位置按手机／平板分开（见下方顶栏）。
    val splitChrome = portrait

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

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    // 手机横屏：系统手势区 + Material 标准 16dp，避免「断开连接」贴右边。
                    .then(
                        if (phone && !portrait) {
                            Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End))
                        } else {
                            Modifier
                        },
                    )
                    .padding(
                        start = 10.dp,
                        end = if (phone && !portrait) 16.dp else 10.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 手机竖屏：录制贴在首行芯片（D-Log2）右侧空位；手机横屏：芯片后、断开前偏左；平板保持一行靠右。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    phone && portrait -> {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ChromeChipLine(primaryChips, Modifier.weight(1f))
                                if (chrome.isRecording) {
                                    RecordingElapsedPill(elapsedSec = chrome.recordElapsedSec)
                                }
                            }
                            if (secondaryChips.isNotEmpty()) ChromeChipLine(secondaryChips)
                        }
                    }
                    phone && !portrait -> {
                        // 芯片占满左侧并省略；录制时长贴在「断开」左侧约 50dp，互不重叠。
                        ChromeChipLine(primaryChips + secondaryChips, Modifier.weight(1f))
                        if (chrome.isRecording) {
                            RecordingElapsedPill(
                                elapsedSec = chrome.recordElapsedSec,
                                modifier = Modifier.padding(end = 50.dp),
                            )
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (splitChrome) {
                                ChromeChipLine(primaryChips)
                                if (secondaryChips.isNotEmpty()) ChromeChipLine(secondaryChips)
                            } else {
                                ChromeChipLine(primaryChips + secondaryChips)
                            }
                        }
                        if (chrome.isRecording) {
                            RecordingElapsedPill(elapsedSec = chrome.recordElapsedSec)
                        }
                    }
                }
                TextButton(
                    onClick = onDisconnect,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp),
                ) {
                    Text(
                        "断开连接",
                        color = GlanceColors.pear,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

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

        // 拍照倒计时叠在最上层，避开底栏；手机略上移、字号略小。
        if (chrome.photoTimerRemainingSeconds > 0) {
            Text(
                chrome.photoTimerRemainingSeconds.toString(),
                color = Color.White,
                fontSize = if (phone) 64.sp else 78.sp,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(bottom = if (phone) 48.dp else 0.dp)
                        .zIndex(2f)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 30.dp, vertical = 8.dp),
            )
        }

        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
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
            if (connection.mode == ConnectionMode.Wireless) {
                TextButton(onClick = onGallery, enabled = !chrome.isRecording) {
                    Text("相册", color = GlanceColors.pear)
                }
            }
        }
    }
}

@Composable
private fun ChromeChipLine(chips: List<String>, modifier: Modifier = Modifier) {
    if (chips.isEmpty()) {
        Spacer(modifier.height(0.dp))
        return
    }
    Text(
        chips.joinToString("   ·   "),
        modifier = modifier,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RecordingElapsedPill(elapsedSec: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(GlanceColors.coral))
        Text(
            MonitorChromeLabels.formatElapsed(elapsedSec),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}
