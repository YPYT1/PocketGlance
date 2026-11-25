package com.opencapture.pocketglance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.opencapture.pocketglance.PreviewLutState

@Composable
internal fun PreviewLutDialog(
    state: PreviewLutState,
    available: Boolean,
    colorLabel: String,
    onImport: () -> Unit,
    onSelect: (PreviewLut?) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = remember(state.entries) { state.entries.groupBy { it.category ?: "自定义导入" } }
    val search = query.trim()
    val showCategories = selectedCategory == null && search.isEmpty()
    val visibleGroups = remember(groups, selectedCategory, search) {
        groups.filterKeys { selectedCategory == null || it == selectedCategory }.mapValues { (category, entries) ->
            entries.filter { category.contains(search, ignoreCase = true) || it.name.contains(search, ignoreCase = true) }
        }.filterValues { it.isNotEmpty() }
    }
    val builtinCount = state.entries.count { it.category != null }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).imePadding()
                .widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(0.9f),
            color = GlanceColors.canvas,
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MonitorBackButton(onClose)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(selectedCategory?.let(::lutCategoryTitle) ?: "选择 LUT", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            listOfNotNull("内置 $builtinCount 款", colorLabel.takeUnless { it == "—" }, "仅影响预览").joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (selectedCategory != null || search.isNotEmpty()) {
                        TextButton(onClick = { selectedCategory = null; query = "" }) { Text("全部分类") }
                    }
                }
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(if (selectedCategory == null) "搜索 LUT 或分类" else "在此分类中搜索") })
                key(selectedCategory, showCategories) {
                    LazyVerticalGrid(
                        columns = if (showCategories) GridCells.Adaptive(140.dp) else GridCells.Fixed(1),
                        modifier = Modifier.weight(1f).then(if (showCategories) Modifier else Modifier.selectableGroup()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (state.loadingCatalog) item(span = { GridItemSpan(maxLineSpan) }) {
                            CircularProgressIndicator(Modifier.padding(12.dp))
                        }
                        if (state.catalogUnavailable) item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("未读取到内置 LUT，请重新安装完整版本。", color = GlanceColors.coral)
                        }
                        if (!available) item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("当前直播渲染无法应用 LUT。", color = GlanceColors.coral)
                        }
                        state.loading?.let { lut -> item(span = { GridItemSpan(maxLineSpan) }) { Text("正在读取「${lut.name}」…") } }
                        state.message?.let { message -> item(span = { GridItemSpan(maxLineSpan) }) { Text(message) } }
                        if (visibleGroups.isEmpty() && search.isNotEmpty()) item { Text("没有匹配的 LUT。") }
                        visibleGroups.forEach { (category, entries) ->
                            if (showCategories) {
                                item(key = "category:$category") {
                                    val applied = entries.any { it.id == state.selected?.id }
                                    Surface(
                                        color = if (applied) GlanceColors.pear.copy(alpha = 0.35f) else GlanceColors.surface,
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Column(Modifier.clickable(role = Role.Button) { selectedCategory = category }.padding(12.dp)) {
                                            Text(lutCategoryTitle(category), style = MaterialTheme.typography.titleSmall)
                                            Text("${entries.size} 款" + if (applied) " · 使用中" else "", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            } else {
                                if (selectedCategory == null) item(key = "heading:$category") {
                                    Text(lutCategoryTitle(category), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
                                }
                                items(entries, key = { it.id }) { lut ->
                                    val selected = state.selected?.id == lut.id
                                    Surface(color = if (selected) GlanceColors.pear.copy(alpha = 0.35f) else GlanceColors.canvas) {
                                        Row(
                                            Modifier.fillMaxWidth().selectable(selected, enabled = available, role = Role.RadioButton) { onSelect(lut) }
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(lut.name, Modifier.weight(1f))
                                            if (selected) Text("已应用", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onSelect(null) }, enabled = state.selected != null || state.loading != null) { Text("关闭 LUT") }
                    TextButton(onClick = onImport, enabled = !state.importing) { Text("导入 .cube") }
                }
            }
        }
    }
}

private val lutCategoryCount = Regex("[（(]\\d+款[）)]$")

private fun lutCategoryTitle(category: String): String = category.replace(lutCategoryCount, "").trim()
