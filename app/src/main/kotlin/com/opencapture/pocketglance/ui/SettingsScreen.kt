package com.opencapture.pocketglance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opencapture.pocketglance.connection.StartupPreference
import com.opencapture.pocketglance.monitor.MonitorOrientation

@Composable
fun SettingsScreen(
    startup: StartupPreference,
    onStartup: (StartupPreference) -> Unit,
    monitorOrientation: MonitorOrientation,
    onMonitorOrientation: (MonitorOrientation) -> Unit,
    backToMonitor: Boolean,
    onBack: () -> Unit,
    onExportLogs: () -> Unit,
    exportNote: String? = null,
) {
    Box(Modifier.fillMaxSize().background(GlanceColors.canvas), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 760.dp).fillMaxWidth()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            MonitorBackButton(onBack, label = if (backToMonitor) "返回监视界面" else "返回连接首页")
            Text("设置", style = MaterialTheme.typography.headlineMedium)
            SettingsContent(
                startup = startup,
                onStartup = onStartup,
                monitorOrientation = monitorOrientation,
                onMonitorOrientation = onMonitorOrientation,
                onExportLogs = onExportLogs,
                exportNote = exportNote,
            )
        }
    }
}

/** 设置正文：设置页与平板首页右侧共用。 */
@Composable
fun SettingsContent(
    startup: StartupPreference,
    onStartup: (StartupPreference) -> Unit,
    monitorOrientation: MonitorOrientation,
    onMonitorOrientation: (MonitorOrientation) -> Unit,
    onExportLogs: () -> Unit,
    exportNote: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("竖屏显示", style = MaterialTheme.typography.titleLarge)
            StartupOption(
                selected = monitorOrientation == MonitorOrientation.LandscapeLocked,
                title = "锁定横屏",
                detail = "在横屏中完整显示竖画面，两侧使用纯黑背景",
                onClick = { onMonitorOrientation(MonitorOrientation.LandscapeLocked) },
            )
            StartupOption(
                selected = monitorOrientation == MonitorOrientation.AutoRotate,
                title = "开放旋转",
                detail = "跟随 Pocket：横画面切回横屏，竖画面切换为正向竖屏",
                onClick = { onMonitorOrientation(MonitorOrientation.AutoRotate) },
            )
        }

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
            Text("默认连接方式", style = MaterialTheme.typography.titleLarge)
            Text(
                "下次打开应用时，优先尝试哪一种连接",
                style = MaterialTheme.typography.bodyMedium,
            )
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StartupOption(
                    selected = startup == StartupPreference.Last,
                    title = "上次连接",
                    detail = "沿用你最近一次成功选用的方式",
                    onClick = { onStartup(StartupPreference.Last) },
                )
                StartupOption(
                    selected = startup == StartupPreference.Wireless,
                    title = "无线连接",
                    detail = "打开后直接扫描并连接 Pocket 热点",
                    onClick = { onStartup(StartupPreference.Wireless) },
                )
                StartupOption(
                    selected = startup == StartupPreference.Wired,
                    title = "有线连接",
                    detail = "打开后查找 USB 主机／附件会话",
                    onClick = { onStartup(StartupPreference.Wired) },
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlanceColors.surface)
                    .border(1.dp, GlanceColors.hairline, RoundedCornerShape(20.dp))
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("诊断日志", style = MaterialTheme.typography.titleLarge)
            Text(
                "有线／无线连接过程会写入应用内日志。测试完把平板连回电脑后可用 adb 拉取，也可在此分享。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GlanceColors.azure.copy(alpha = 0.18f))
                        .border(1.dp, GlanceColors.azure, RoundedCornerShape(14.dp))
                        .clickable(onClick = onExportLogs)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text("导出／分享诊断日志", style = MaterialTheme.typography.bodyLarge)
            }
            if (!exportNote.isNullOrBlank()) {
                Text(exportNote, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun StartupOption(
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
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
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
