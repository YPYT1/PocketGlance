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
import androidx.compose.ui.unit.dp
import com.opencapture.pocketglance.connection.ConnectionPhase
import com.opencapture.pocketglance.connection.ConnectionState
import com.opencapture.pocketglance.connection.FoundCameraRow
import com.opencapture.pocketglance.connection.StartupPreference

@Composable
fun HomeScreen(
    connection: ConnectionState,
    startup: StartupPreference,
    permissionHint: String?,
    onConnectWireless: () -> Unit,
    onConnectWired: () -> Unit,
    onPickCamera: (String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenMonitor: () -> Unit,
    onOpenSettings: () -> Unit,
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Pocket 监视器", style = MaterialTheme.typography.displayLarge)
                Text(
                    "连接相机 · 实时监视",
                    style = MaterialTheme.typography.bodyLarge,
                    color = GlanceColors.inkMuted,
                )
            }
            TextButton(onClick = onOpenSettings) {
                Text("设置", color = GlanceColors.azure)
            }
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
            Text("现在连接", style = MaterialTheme.typography.titleLarge)
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

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlanceColors.surface)
                    .border(1.dp, GlanceColors.hairline, RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("默认连接方式", style = MaterialTheme.typography.labelLarge)
                Text(
                    GlanceCopy.startup(startup) + " · 在设置中更改",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

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
