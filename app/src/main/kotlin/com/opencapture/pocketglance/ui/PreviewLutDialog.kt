package com.opencapture.pocketglance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencapture.pocketglance.PreviewLutState

@Composable
internal fun PreviewLutDialog(
    state: PreviewLutState,
    available: Boolean,
    onImport: () -> Unit,
    onSelect: (PreviewLut?) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val groups = remember(state.entries, query) {
        state.entries.filter { it.name.contains(query.trim(), ignoreCase = true) || it.category?.contains(query.trim(), ignoreCase = true) == true }
            .groupBy { it.category ?: "自定义导入" }
    }
    val builtinCount = state.entries.count { it.category != null }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = GlanceColors.canvas,
        title = { Text(if (builtinCount > 0) "监视 LUT · 内置 $builtinCount 款" else "监视 LUT") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("只改变平板或手机上的预览，不影响相机录制。D-Log M 的 LUT 不一定适合 D-Log2。")
                if (!available) Text("当前设备的预览渲染不可用。", color = GlanceColors.coral)
                state.loading?.let { Text("正在读取「${it.name}」…", style = MaterialTheme.typography.bodyMedium) }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                StartupOption(state.selected == null, "原始直播", "关闭本地 LUT；显示相机送来的画面") { onSelect(null) }
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("搜索 LUT 或分类") })
                if (groups.isEmpty() && query.isNotBlank()) Text("没有匹配的 LUT。")
                LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.forEach { (category, entries) ->
                        item(key = "category:$category") { Text(category, style = MaterialTheme.typography.labelLarge) }
                        items(entries, key = { it.id }) { lut ->
                            StartupOption(state.selected?.id == lut.id, lut.name, if (lut.category == null) "自定义 3D .cube" else "内置 · 温影工坊") {
                                if (available) onSelect(lut)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onImport, enabled = !state.importing) {
            Text(if (state.importing) "正在读取…" else "导入 .cube")
        } },
        dismissButton = { TextButton(onClick = onClose) { Text("完成") } },
    )
}
