package com.opencapture.pocketglance.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
    val configuration = LocalConfiguration.current
    val tablet = configuration.smallestScreenWidthDp >= 600
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // 手机横屏：左右分栏；竖屏／平板：上方工具 + 下方网格。
    val sideBySide = !tablet && landscape

    val groups = remember(state.entries) { state.entries.groupBy { it.category ?: "自定义导入" } }
    val search = query.trim()
    val showCategories = selectedCategory == null && search.isEmpty()
    val visibleGroups =
        remember(groups, selectedCategory, search) {
            groups
                .filterKeys { selectedCategory == null || it == selectedCategory }
                .mapValues { (category, entries) ->
                    entries.filter {
                        category.contains(search, ignoreCase = true) ||
                            it.name.contains(search, ignoreCase = true)
                    }
                }
                .filterValues { it.isNotEmpty() }
        }
    val builtinCount = state.entries.count { it.category != null }
    val subtitle =
        listOfNotNull("内置 $builtinCount 款", colorLabel.takeUnless { it == "—" }, "仅影响预览")
            .joinToString(" · ")

    val dialogMaxWidth = if (tablet) 720.dp else if (sideBySide) 720.dp else 560.dp
    val dialogHeightFraction = when {
        tablet -> 0.88f
        sideBySide -> 0.94f
        else -> 0.92f
    }
    val outerPad = if (tablet) PaddingValues(horizontal = 16.dp, vertical = 12.dp) else PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    val innerPad = if (tablet) PaddingValues(horizontal = 16.dp, vertical = 12.dp) else PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    val sectionGap = if (tablet) 8.dp else 4.dp

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier =
                Modifier
                    .padding(outerPad)
                    .imePadding()
                    .widthIn(max = dialogMaxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight(dialogHeightFraction),
            color = GlanceColors.canvas,
            shape = RoundedCornerShape(if (tablet) 24.dp else 16.dp),
        ) {
            Column(Modifier.padding(innerPad), verticalArrangement = Arrangement.spacedBy(sectionGap)) {
                LutDialogHeader(
                    compact = !tablet,
                    title = selectedCategory?.let(::lutCategoryTitle) ?: "选择 LUT",
                    subtitle = subtitle,
                    showAllCategories = selectedCategory != null || search.isNotEmpty(),
                    onBack = onClose,
                    onShowAll = {
                        selectedCategory = null
                        query = ""
                    },
                )
                OutlinedTextField(
                    query,
                    { query = it },
                    modifier = Modifier.fillMaxWidth().heightIn(max = if (tablet) 56.dp else 48.dp),
                    singleLine = true,
                    textStyle = if (tablet) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                    placeholder = {
                        Text(if (selectedCategory == null) "搜索 LUT 或分类" else "在此分类中搜索")
                    },
                )

                if (sideBySide) {
                    val railGroups =
                        remember(groups, search) {
                            if (search.isEmpty()) {
                                groups
                            } else {
                                groups
                                    .mapValues { (category, entries) ->
                                        entries.filter {
                                            category.contains(search, ignoreCase = true) ||
                                                it.name.contains(search, ignoreCase = true)
                                        }
                                    }
                                    .filterValues { it.isNotEmpty() }
                            }
                        }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        LutCategoryRail(
                            groups = railGroups,
                            selectedCategory = selectedCategory,
                            selectedLutId = state.selected?.id,
                            modifier = Modifier.width(168.dp).fillMaxHeight(),
                            onSelectCategory = { selectedCategory = it },
                        )
                        VerticalDivider()
                        // 右侧只展示 LUT 条目；未选分类时提示点左侧。
                        LutContentPane(
                            state = state,
                            available = available,
                            showCategories = false,
                            visibleGroups = if (showCategories) emptyMap() else visibleGroups,
                            categoryColumns = GridCells.Fixed(1),
                            emptyHint = if (showCategories) "在左侧选择分类，或搜索 LUT。" else null,
                            onSelectCategory = { selectedCategory = it },
                            onSelect = onSelect,
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp),
                        )
                    }
                } else {
                    LutContentPane(
                        state = state,
                        available = available,
                        showCategories = showCategories,
                        visibleGroups = visibleGroups,
                        categoryColumns =
                            if (tablet) GridCells.Adaptive(150.dp) else GridCells.Fixed(2),
                        emptyHint = null,
                        onSelectCategory = { selectedCategory = it },
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }

                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = { onSelect(null) },
                        enabled = state.selected != null || state.loading != null,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text("关闭 LUT") }
                    TextButton(
                        onClick = onImport,
                        enabled = !state.importing,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text("导入 .cube") }
                }
            }
        }
    }
}

@Composable
private fun LutDialogHeader(
    compact: Boolean,
    title: String,
    subtitle: String,
    showAllCategories: Boolean,
    onBack: () -> Unit,
    onShowAll: () -> Unit,
) {
    if (compact) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                Text("返回", color = GlanceColors.azure, maxLines = 1)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showAllCategories) {
                TextButton(onClick = onShowAll, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Text("全部", maxLines = 1)
                }
            }
        }
    } else {
        MonitorBackButton(onBack)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            if (showAllCategories) {
                TextButton(onClick = onShowAll) { Text("全部分类") }
            }
        }
    }
}

@Composable
private fun LutCategoryRail(
    groups: Map<String, List<PreviewLut>>,
    selectedCategory: String?,
    selectedLutId: String?,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(groups.entries.toList(), key = { it.key }) { (category, entries) ->
            val applied = entries.any { it.id == selectedLutId }
            val active = category == selectedCategory
            Surface(
                color =
                    when {
                        active -> GlanceColors.pear.copy(alpha = 0.45f)
                        applied -> GlanceColors.pear.copy(alpha = 0.22f)
                        else -> GlanceColors.surface
                    },
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onSelectCategory(category) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        lutCategoryTitle(category),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${entries.size} 款" + if (applied) " · 使用中" else "",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun LutContentPane(
    state: PreviewLutState,
    available: Boolean,
    showCategories: Boolean,
    visibleGroups: Map<String, List<PreviewLut>>,
    categoryColumns: GridCells,
    emptyHint: String?,
    onSelectCategory: (String) -> Unit,
    onSelect: (PreviewLut?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = if (showCategories) categoryColumns else GridCells.Fixed(1),
        modifier = modifier.then(if (showCategories) Modifier else Modifier.selectableGroup()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 4.dp),
    ) {
        if (state.loadingCatalog) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                CircularProgressIndicator(Modifier.padding(12.dp))
            }
        }
        if (state.catalogUnavailable) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("未读取到内置 LUT，请重新安装完整版本。", color = GlanceColors.coral)
            }
        }
        if (!available) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("当前直播渲染无法应用 LUT。", color = GlanceColors.coral)
            }
        }
        state.loading?.let { lut ->
            item(span = { GridItemSpan(maxLineSpan) }) { Text("正在读取「${lut.name}」…") }
        }
        state.message?.let { message ->
            item(span = { GridItemSpan(maxLineSpan) }) { Text(message) }
        }
        if (visibleGroups.isEmpty() && emptyHint != null && !state.loadingCatalog) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(emptyHint, color = GlanceColors.inkMuted)
            }
        } else if (visibleGroups.isEmpty() && state.entries.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Text("没有匹配的 LUT。") }
        }
        visibleGroups.forEach { (category, entries) ->
            if (showCategories) {
                item(key = "category:$category") {
                    val applied = entries.any { it.id == state.selected?.id }
                    Surface(
                        color = if (applied) GlanceColors.pear.copy(alpha = 0.35f) else GlanceColors.surface,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { onSelectCategory(category) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                lutCategoryTitle(category),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${entries.size} 款" + if (applied) " · 使用中" else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            } else {
                if (visibleGroups.size > 1) {
                    item(key = "heading:$category", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            lutCategoryTitle(category),
                            Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                items(entries, key = { it.id }) { lut ->
                    val selected = state.selected?.id == lut.id
                    Surface(color = if (selected) GlanceColors.pear.copy(alpha = 0.35f) else GlanceColors.canvas) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected, enabled = available, role = Role.RadioButton) { onSelect(lut) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(lut.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (selected) Text("已应用", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private val lutCategoryCount = Regex("[（(]\\d+款[）)]$")

private fun lutCategoryTitle(category: String): String = category.replace(lutCategoryCount, "").trim()
