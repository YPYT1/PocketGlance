package com.opencapture.pocketglance.ui

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.opencapture.pocketglance.PreviewLutState

private const val FILTER_FAVORITES = "__favorites__"
private const val FILTER_ALL = "__all__"
private const val FILTER_IMPORTED = "__imported__"

private data class LutFilterItem(val id: String, val label: String, val count: Int)

@Composable
internal fun PreviewLutDialog(
    state: PreviewLutState,
    available: Boolean,
    colorLabel: String,
    onImport: () -> Unit,
    onSelect: (PreviewLut?) -> Unit,
    onToggleFavorite: (PreviewLut) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    val configuration = LocalConfiguration.current
    val tablet = configuration.smallestScreenWidthDp >= 600
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val packs =
        remember(state.entries) {
            state.entries
                .mapNotNull { it.category }
                .map(::lutCategoryLabel)
                .filter { it.isNotBlank() && !isLutDateOrBatchCategory(it) }
                .distinct()
                .sorted()
        }
    val tagFilters =
        remember(state.entries) {
            LutTagFilters.filter { tag -> state.entries.any { tag in it.tags } }
        }
    val railFilters =
        remember(state.entries, state.favoriteIds, packs, tagFilters) {
            buildList {
                add(LutFilterItem(FILTER_ALL, "全部", state.entries.size))
                add(
                    LutFilterItem(
                        FILTER_FAVORITES,
                        "收藏",
                        state.entries.count { it.id in state.favoriteIds },
                    ),
                )
                tagFilters.forEach { tag ->
                    add(LutFilterItem(tag, tag, state.entries.count { tag in it.tags }))
                }
                // 合集名与短标签重复时不再占一行（「人像」标签 +「人像合集」合集可并存）。
                packs.forEach { pack ->
                    if (pack in tagFilters) return@forEach
                    add(
                        LutFilterItem(
                            pack,
                            pack,
                            state.entries.count { lutCategoryLabel(it.category.orEmpty()) == pack },
                        ),
                    )
                }
                val imported = state.entries.count { !it.isBuiltIn }
                if (imported > 0) add(LutFilterItem(FILTER_IMPORTED, "导入", imported))
            }
        }
    val search = query.trim()
    val filtered =
        remember(state.entries, state.favoriteIds, filter, search) {
            state.entries.filter { lut ->
                val matchesFilter =
                    when (filter) {
                        FILTER_ALL -> true
                        FILTER_FAVORITES -> lut.id in state.favoriteIds
                        FILTER_IMPORTED -> !lut.isBuiltIn
                        else ->
                            filter == lutCategoryLabel(lut.category.orEmpty()) || filter in lut.tags
                    }
                val matchesSearch =
                    search.isEmpty() ||
                        lut.name.contains(search, ignoreCase = true) ||
                        (lut.category?.contains(search, ignoreCase = true) == true) ||
                        lut.tags.any { it.contains(search, ignoreCase = true) }
                matchesFilter && matchesSearch
            }
        }
    val sections =
        remember(filtered, filter, packs) {
            if (filter == FILTER_ALL || filter in packs) {
                filtered
                    .groupBy { lut ->
                        lut.category?.let(::lutCategoryLabel)?.takeIf { it.isNotBlank() && !isLutDateOrBatchCategory(it) }
                            ?: lut.tags.firstOrNull { it != "Pocket4" }
                            ?: lut.tags.firstOrNull()
                            ?: "其他"
                    }
                    .toList()
            } else {
                listOf("" to filtered)
            }
        }

    val builtinCount = state.entries.count { it.isBuiltIn }
    val subtitle =
        listOfNotNull(
            "内置 $builtinCount",
            colorLabel.takeUnless { it == "—" },
            "仅预览",
        ).joinToString(" · ")

    val dialogMaxWidth =
        when {
            tablet -> 980.dp
            landscape -> 900.dp
            else -> 640.dp
        }
    val railWidth =
        when {
            tablet -> 168.dp
            landscape -> 140.dp
            else -> 112.dp
        }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier =
                Modifier
                    .padding(if (tablet) 10.dp else 4.dp)
                    .imePadding()
                    .widthIn(max = dialogMaxWidth)
                    .fillMaxWidth(if (tablet || landscape) 0.96f else 0.98f)
                    .fillMaxHeight(if (tablet) 0.92f else 0.95f),
            color = GlanceColors.canvas,
            shape = RoundedCornerShape(if (tablet) 18.dp else 14.dp),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                LutTopBar(
                    compact = !tablet,
                    title = "LUT",
                    subtitle = subtitle,
                    query = query,
                    onQueryChange = { query = it },
                    onClose = onClose,
                )
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = GlanceColors.hairline)

                Row(Modifier.weight(1f).fillMaxWidth()) {
                    LutFilterRail(
                        filters = railFilters,
                        selected = filter,
                        onSelect = { filter = it },
                        modifier = Modifier.width(railWidth).fillMaxHeight(),
                    )
                    VerticalDivider(color = GlanceColors.hairline)
                    LutListPane(
                        state = state,
                        available = available,
                        sections = sections,
                        emptyFavorites = filter == FILTER_FAVORITES,
                        onSelect = onSelect,
                        onToggleFavorite = onToggleFavorite,
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp),
                    )
                }

                HorizontalDivider(color = GlanceColors.hairline)
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { onSelect(null) },
                        enabled = state.selected != null || state.loading != null,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text("关闭 LUT", fontSize = 13.sp) }
                    TextButton(
                        onClick = onImport,
                        enabled = !state.importing,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text(if (state.importing) "导入中…" else "导入 .cube", fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun LutTopBar(
    compact: Boolean,
    title: String,
    subtitle: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                maxLines = 1,
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LutSearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.widthIn(min = if (compact) 96.dp else 160.dp, max = if (compact) 160.dp else 240.dp),
        )
        TextButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Text("完成", color = GlanceColors.pear, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LutSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(color = GlanceColors.ink, fontSize = 13.sp),
        cursorBrush = SolidColor(GlanceColors.azure),
        modifier = modifier.height(34.dp),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .border(1.dp, GlanceColors.ink.copy(alpha = 0.22f), shape)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text("搜索", color = GlanceColors.inkMuted, fontSize = 12.sp, maxLines = 1)
                }
                inner()
            }
        },
    )
}

@Composable
private fun LutFilterRail(
    filters: List<LutFilterItem>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(end = 2.dp),
    ) {
        items(filters, key = { it.id }) { item ->
            val active = item.id == selected
            Surface(
                color = if (active) GlanceColors.pear.copy(alpha = 0.5f) else GlanceColors.surface,
                shape = RoundedCornerShape(10.dp),
                border =
                    BorderStroke(
                        1.dp,
                        if (active) GlanceColors.pear else GlanceColors.ink.copy(alpha = 0.1f),
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onSelect(item.id) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                ) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = GlanceColors.ink,
                    )
                    Text(
                        "${item.count}",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlanceColors.inkMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun LutListPane(
    state: PreviewLutState,
    available: Boolean,
    sections: List<Pair<String, List<PreviewLut>>>,
    emptyFavorites: Boolean,
    onSelect: (PreviewLut?) -> Unit,
    onToggleFavorite: (PreviewLut) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.loadingCatalog -> {
            Box(modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GlanceColors.azure, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        }
        state.catalogUnavailable && state.entries.isEmpty() -> {
            Box(modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                Text("未读取到内置 LUT，请重新安装完整版本。", color = GlanceColors.coral)
            }
        }
        else -> {
            Column(modifier) {
                if (!available) {
                    Text(
                        "当前直播渲染无法应用 LUT。",
                        color = GlanceColors.coral,
                        modifier = Modifier.padding(vertical = 6.dp),
                        fontSize = 13.sp,
                    )
                }
                state.loading?.let {
                    Text("正在读取「${it.name}」…", Modifier.padding(vertical = 4.dp), fontSize = 13.sp)
                }
                state.message?.let {
                    Text(it, Modifier.padding(vertical = 4.dp), fontSize = 13.sp, color = GlanceColors.inkMuted)
                }
                val empty = sections.all { it.second.isEmpty() }
                if (empty) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (emptyFavorites) "还没有收藏。点右侧星标即可收藏。" else "没有匹配的 LUT。",
                            color = GlanceColors.inkMuted,
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 2.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        sections.forEach { (heading, entries) ->
                            if (entries.isEmpty()) return@forEach
                            if (heading.isNotBlank() && sections.count { it.second.isNotEmpty() } > 1) {
                                item(key = "h:$heading") {
                                    Text(
                                        heading,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = GlanceColors.inkMuted,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp, start = 2.dp),
                                    )
                                }
                            }
                            items(entries, key = { it.id }) { lut ->
                                LutRow(
                                    lut = lut,
                                    selected = state.selected?.id == lut.id,
                                    favorite = lut.id in state.favoriteIds,
                                    enabled = available,
                                    onSelect = { onSelect(lut) },
                                    onToggleFavorite = { onToggleFavorite(lut) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LutRow(
    lut: PreviewLut,
    selected: Boolean,
    favorite: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        color = if (selected) GlanceColors.pear.copy(alpha = 0.28f) else GlanceColors.surface,
        shape = shape,
        border =
            BorderStroke(
                1.dp,
                if (selected) GlanceColors.pear else GlanceColors.ink.copy(alpha = 0.12f),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    lut.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = GlanceColors.ink,
                )
                val meta =
                    buildList {
                        lut.category?.let(::add)
                        addAll(lut.tags.take(3))
                    }.distinct().joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = GlanceColors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Text(
                    "已应用",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlanceColors.ink,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            Text(
                if (favorite) "★" else "☆",
                modifier =
                    Modifier
                        .clickable(role = Role.Button, onClick = onToggleFavorite)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                color = if (favorite) GlanceColors.pear else GlanceColors.inkMuted,
                fontSize = 18.sp,
            )
        }
    }
}
