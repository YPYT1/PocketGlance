package com.opencapture.pocketglance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opencapture.pocketglance.connection.StartupPreference

@Composable
fun SettingsScreen(
    startup: StartupPreference,
    onStartup: (StartupPreference) -> Unit,
    onBack: () -> Unit,
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("设置", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) {
                Text("完成", color = GlanceColors.azure)
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("默认连接方式", style = MaterialTheme.typography.titleLarge)
            Text(
                "下次打开应用时，优先尝试哪一种连接",
                style = MaterialTheme.typography.bodyMedium,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    detail = "打开后查找 USB 附件",
                    onClick = { onStartup(StartupPreference.Wired) },
                )
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
