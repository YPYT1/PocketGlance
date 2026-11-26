package com.opencapture.pocketglance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencapture.pocketglance.diagnostics.GlanceLog
import kotlinx.coroutines.delay

@Composable
fun LogViewerScreen(
    backLabel: String,
    onBack: () -> Unit,
) {
    var lines by remember { mutableStateOf(GlanceLog.recentLines()) }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_200)
            lines = GlanceLog.recentLines()
        }
    }
    LaunchedEffect(lines.size, autoScroll) {
        if (autoScroll && lines.isNotEmpty()) {
            listState.scrollToItem(lines.lastIndex)
        }
    }

    Box(Modifier.fillMaxSize().background(GlanceColors.canvas), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MonitorBackButton(onBack, label = backLabel)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("诊断日志", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "共 ${lines.size} 行 · 自动刷新",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = {
                    autoScroll = !autoScroll
                }) {
                    Text(
                        if (autoScroll) "暂停滚底" else "滚到底部",
                        color = GlanceColors.azure,
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlanceColors.ink)
                        .padding(12.dp),
            ) {
                if (lines.isEmpty()) {
                    Text(
                        "暂无日志",
                        color = GlanceColors.canvas.copy(alpha = 0.55f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(lines, key = { index, line -> "$index:$line" }) { _, line ->
                            Text(
                                line,
                                color = GlanceColors.canvas.copy(alpha = 0.88f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
