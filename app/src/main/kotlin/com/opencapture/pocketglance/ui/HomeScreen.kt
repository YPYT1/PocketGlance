package com.opencapture.pocketglance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opencapture.pocketglance.connection.ConnectionMode
import com.opencapture.pocketglance.connection.ConnectionPhase
import com.opencapture.pocketglance.connection.ConnectionState
import com.opencapture.pocketglance.connection.FoundCameraRow
import com.opencapture.pocketglance.connection.StartupPreference

@Composable
fun HomeScreen(
    connection: ConnectionState,
    startup: StartupPreference,
    permissionHint: String?,
    onStartup: (StartupPreference) -> Unit,
    onConnectWireless: () -> Unit,
    onConnectWired: () -> Unit,
    onPickCamera: (String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenMonitor: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GlanceColors.canvas)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pocket 一览", style = MaterialTheme.typography.displayLarge)
            Text(
                "连接相机 · 监视画面 · 浏览相册",
                style = MaterialTheme.typography.bodyLarge,
                color = GlanceColors.inkMuted,
            )
        }

        StatusPanel(
            connection = connection,
            permissionHint = permissionHint,
            onPickCamera = onPickCamera,
            onDisconnect = onDisconnect,
            onOpenMonitor = onOpenMonitor,
            onRequestPermission = onRequestPermission,
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("选择连接方式", style = MaterialTheme.typography.titleLarge)
            ConnectTile(
                title = "无线连接",
                subtitle = "蓝牙配对后加入相机热点",
                accent = GlanceColors.pear,
                onInk = true,
                onClick = onConnectWireless,
            )
            ConnectTile(
                title = "有线连接",
                subtitle = "USB 线直连（需支持附件模式）",
                accent = GlanceColors.azure,
                onInk = false,
                onClick = onConnectWired,
            )
        }

        StartupPanel(startup = startup, onStartup = onStartup)

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatusPanel(
    connection: ConnectionState,
    permissionHint: String?,
    onPickCamera: (String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenMonitor: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val show =
        permissionHint != null ||
            connection.phase != ConnectionPhase.Idle ||
            connection.message.isNotBlank()
    if (!show) return

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(GlanceColors.surface)
                .border(1.dp, GlanceColors.hairline, RoundedCornerShape(20.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (permissionHint != null) {
            Text(permissionHint, style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = onRequestPermission,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = GlanceColors.coral,
                        contentColor = GlanceColors.surface,
                    ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("授予附近设备权限")
            }
        }

        when (connection.phase) {
            ConnectionPhase.Connecting -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        color = GlanceColors.azure,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(connection.message, style = MaterialTheme.typography.bodyLarge)
                }
                FoundList(connection.foundCameras, onPickCamera)
                TextButton(onClick = onDisconnect) {
                    Text("取消", color = GlanceColors.coral)
                }
            }
            ConnectionPhase.Live -> {
                Text(connection.message.ifBlank { "已连接" }, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenMonitor,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = GlanceColors.pear,
                                contentColor = GlanceColors.ink,
                            ),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("进入监视") }
                    TextButton(onClick = onDisconnect) {
                        Text("断开", color = GlanceColors.coral)
                    }
                }
            }
            ConnectionPhase.Failed -> {
                Text(connection.message, style = MaterialTheme.typography.bodyLarge, color = GlanceColors.coral)
                TextButton(onClick = onDisconnect) {
                    Text("清除状态")
                }
            }
            ConnectionPhase.Idle -> Unit
        }
    }
}

@Composable
private fun FoundList(cameras: List<FoundCameraRow>, onPick: (String) -> Unit) {
    if (cameras.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("发现的相机", style = MaterialTheme.typography.labelLarge)
        cameras.forEach { cam ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlanceColors.canvas)
                        .clickable { onPick(cam.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(cam.title, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ConnectTile(
    title: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    onInk: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(accent)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = if (onInk) GlanceColors.ink else GlanceColors.surface,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onInk) GlanceColors.inkMuted else GlanceColors.surface.copy(alpha = 0.9f),
        )
    }
}

@Composable
private fun StartupPanel(startup: StartupPreference, onStartup: (StartupPreference) -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(GlanceColors.surface)
                .border(1.dp, GlanceColors.hairline, RoundedCornerShape(20.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("启动默认", style = MaterialTheme.typography.titleLarge)
        Text(
            "下次打开应用时，优先尝试哪一种连接",
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StartupOption(
                selected = startup == StartupPreference.Wireless,
                title = "每次用无线",
                detail = "打开后直接扫描并连接 Pocket 热点",
                onClick = { onStartup(StartupPreference.Wireless) },
            )
            StartupOption(
                selected = startup == StartupPreference.Wired,
                title = "每次用有线",
                detail = "打开后查找 USB 附件",
                onClick = { onStartup(StartupPreference.Wired) },
            )
            StartupOption(
                selected = startup == StartupPreference.Last,
                title = "记住上次",
                detail = "沿用你最近一次成功选用的方式",
                onClick = { onStartup(StartupPreference.Last) },
            )
        }
    }
}

@Composable
private fun StartupOption(
    selected: Boolean,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) GlanceColors.pear.copy(alpha = 0.45f) else GlanceColors.canvas)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) GlanceColors.pear else GlanceColors.hairline,
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (selected) GlanceColors.ink else GlanceColors.inkMuted, CircleShape)
                    .padding(4.dp)
                    .then(
                        if (selected) Modifier.clip(CircleShape).background(GlanceColors.ink)
                        else Modifier,
                    ),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
        }
    }
}
