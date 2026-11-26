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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opencapture.pocketglance.BuildConfig
import com.opencapture.pocketglance.connection.StartupPreference
import com.opencapture.pocketglance.monitor.MonitorOrientation
import com.opencapture.pocketglance.update.AppUpdate
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    startup: StartupPreference,
    onStartup: (StartupPreference) -> Unit,
    monitorOrientation: MonitorOrientation,
    onMonitorOrientation: (MonitorOrientation) -> Unit,
    backToMonitor: Boolean,
    onBack: () -> Unit,
    onViewLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onExportLogs: () -> Unit,
    onSyncLogsForComputer: () -> Unit,
    exportNote: String? = null,
    computerHint: String? = null,
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
                showDiagnostics = true,
                showAbout = true,
                onViewLogs = onViewLogs,
                onClearLogs = onClearLogs,
                onExportLogs = onExportLogs,
                onSyncLogsForComputer = onSyncLogsForComputer,
                exportNote = exportNote,
                computerHint = computerHint,
            )
        }
    }
}

/**
 * 设置正文。
 * 平板首页右侧只嵌显示／连接偏好；诊断日志仅在独立设置页展示。
 */
@Composable
fun SettingsContent(
    startup: StartupPreference,
    onStartup: (StartupPreference) -> Unit,
    monitorOrientation: MonitorOrientation,
    onMonitorOrientation: (MonitorOrientation) -> Unit,
    showDiagnostics: Boolean = false,
    showAbout: Boolean = false,
    onViewLogs: () -> Unit = {},
    onClearLogs: () -> Unit = {},
    onExportLogs: () -> Unit = {},
    onSyncLogsForComputer: () -> Unit = {},
    exportNote: String? = null,
    computerHint: String? = null,
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

        if (showDiagnostics) {
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
                    "连接与监视过程会自动写入应用内日志。出问题后可在此查看、导出，或把设备连到电脑用 adb 直接读取。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LogActionRow(title = "查看日志", detail = "在应用内浏览最近记录", onClick = onViewLogs)
                LogActionRow(title = "清除日志", detail = "清空缓冲与本地文件", onClick = onClearLogs)
                LogActionRow(title = "导出／分享", detail = "打包后通过系统分享发出", onClick = onExportLogs)
                LogActionRow(
                    title = "同步供电脑读取",
                    detail = "写入 USB／adb 可拉取的外部目录",
                    onClick = onSyncLogsForComputer,
                )
                if (!exportNote.isNullOrBlank()) {
                    Text(exportNote, style = MaterialTheme.typography.bodyMedium)
                }
                if (!computerHint.isNullOrBlank()) {
                    Text(computerHint, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (showAbout) {
            AboutAndUpdateSection()
        }
    }
}

@Composable
private fun AboutAndUpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
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
        Text("关于", style = MaterialTheme.typography.titleLarge)
        Text(AppUpdate.displayVersion(), style = MaterialTheme.typography.bodyLarge)
        Text(
            "包名 ${BuildConfig.APPLICATION_ID}\n发布后可从 GitHub Release 在线检查并安装更新。",
            style = MaterialTheme.typography.bodyMedium,
        )
        LogActionRow(
            title = if (busy) "正在检查…" else "检查更新",
            detail = "读取 GitHub 最新 Release，有新版本则下载安装",
            onClick = {
                if (busy) return@LogActionRow
                busy = true
                note = null
                scope.launch {
                    when (val result = AppUpdate.check()) {
                        is AppUpdate.CheckResult.UpToDate -> {
                            note = "已是最新：${result.current}"
                            busy = false
                        }
                        is AppUpdate.CheckResult.Failed -> {
                            note = result.message
                            busy = false
                        }
                        is AppUpdate.CheckResult.Available -> {
                            note = "发现 ${result.release.tag}，正在下载…"
                            runCatching {
                                if (!AppUpdate.canRequestInstall(context)) {
                                    note = "请先允许「安装未知应用」，然后再点检查更新"
                                    AppUpdate.openUnknownSourcesSettings(context)
                                    return@launch
                                }
                                val apk = AppUpdate.downloadApk(context, result.release)
                                note = "已下载 ${result.release.tag}，正在打开安装…"
                                AppUpdate.installApk(context, apk)
                            }.onFailure {
                                note = it.message ?: "下载或安装失败"
                            }
                            busy = false
                        }
                    }
                }
            },
        )
        if (!note.isNullOrBlank()) {
            Text(note!!, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LogActionRow(title: String, detail: String, onClick: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(GlanceColors.azure.copy(alpha = 0.12f))
                .border(1.dp, GlanceColors.azure.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(detail, style = MaterialTheme.typography.bodyMedium)
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
