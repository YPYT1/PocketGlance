package com.opencapture.pocketglance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opencapture.pocketglance.gallery.*
import java.time.LocalDate
import kotlinx.coroutines.delay

private enum class GalleryLayout(val label: String) { Grid("九宫格"), Waterfall("瀑布流"), Large("大图") }

@Composable
internal fun GalleryScreen(
    state: GalleryState,
    tags: Map<String, Set<String>>,
    onUpdateTag: (Set<String>, String, Boolean) -> Unit,
    onDelete: (List<GalleryItem>) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
) {
    var storage by rememberSaveable { mutableStateOf(GalleryStorage.Internal) }
    var filter by rememberSaveable { mutableStateOf<MediaKind?>(null) }
    var layout by rememberSaveable { mutableStateOf(GalleryLayout.Grid) }
    var date by rememberSaveable { mutableStateOf("全部日期") }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var oldestFirst by rememberSaveable { mutableStateOf(false) }
    var selecting by remember(state.cameraId) { mutableStateOf(false) }
    var selection by remember(state.cameraId) { mutableStateOf(emptySet<String>()) }
    var previewId by remember(state.cameraId) { mutableStateOf<String?>(null) }
    var deleteRequest by remember(state.cameraId) { mutableStateOf(emptyList<GalleryItem>()) }
    var tagRequest by remember(state.cameraId) { mutableStateOf(emptyList<GalleryItem>()) }
    val cameraId = state.cameraId
    val thumbnailCache = remember(cameraId) { GalleryThumbnails() }
    val storageItems = remember(state.items, storage) { state.items.filter { it.storage == storage } }
    fun itemTags(item: GalleryItem): Set<String> = cameraId?.let { tags[galleryTagKey(it, item.id)] }.orEmpty()
    val availableTags = remember(storageItems, tags, cameraId) { storageItems.flatMap { itemTags(it) }.distinct().sorted() }
    val months = remember(storageItems) { storageItems.mapNotNull { it.captureDate?.toString()?.take(7) }.distinct().sortedDescending() }
    val today = LocalDate.now()
    val visible = remember(storageItems, filter, date, tag, tags, oldestFirst, cameraId, today) {
        storageItems.filter { item ->
            val recorded = item.captureDate
            (filter == null || item.kind == filter) && (tag == null || tag in itemTags(item)) && when (date) {
                "全部日期" -> true
                "今天" -> recorded == today
                "最近 7 天" -> recorded != null && recorded in today.minusDays(6)..today
                "最近 30 天" -> recorded != null && recorded in today.minusDays(29)..today
                else -> recorded?.toString()?.startsWith(date) == true
            }
        }.let { if (oldestFirst) it.asReversed() else it }
    }
    val selectedItems = visible.filter { it.id in selection }
    val dates = remember(visible) { visible.groupBy(GalleryItem::captureDate) }
    val itemCountWithHeaders = visible.size + dates.size
    val busy = state.deleting || state.loading
    fun clearSelection() { selecting = false; selection = emptySet() }
    fun toggle(id: String) { selection = if (id in selection) selection - id else selection + id }
    val initial = visible.indexOfFirst { it.id == previewId }
    BackHandler(enabled = selecting && initial < 0) { clearSelection() }

    Box(Modifier.fillMaxSize().background(GlanceColors.canvas), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                MonitorBackButton(onBack)
                Row {
                    TextButton(onClick = { if (selecting) clearSelection() else selecting = true }, enabled = !busy && (selecting || visible.isNotEmpty())) {
                        Text(if (selecting) "完成" else "选择")
                    }
                    TextButton(onClick = { clearSelection(); onRefresh() }, enabled = !busy) { Text("刷新") }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GalleryStorage.entries.forEach { store ->
                    FilterChip(storage == store, { storage = store; date = "全部日期"; tag = null; clearSelection() }, { Text(store.label) })
                }
                listOf(null to "全部", MediaKind.Image to "照片", MediaKind.Video to "视频").forEach { (kind, label) ->
                    FilterChip(filter == kind, { filter = kind; clearSelection() }, { Text(label) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp)) {
                GalleryMenu(layout.label, GalleryLayout.entries.map { it.label }) { label -> layout = GalleryLayout.entries.first { it.label == label } }
                GalleryMenu(date, listOf("全部日期", "今天", "最近 7 天", "最近 30 天") + months) { date = it; clearSelection() }
                GalleryMenu(tag ?: "标签", listOf("全部标签") + availableTags) { tag = it.takeUnless { label -> label == "全部标签" }; clearSelection() }
                TextButton(onClick = { oldestFirst = !oldestFirst }) { Text(if (oldestFirst) "最早优先" else "最新优先") }
            }
            if (selecting) {
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    Text("已选 ${selectedItems.size} 项", Modifier.padding(horizontal = 6.dp))
                    TextButton(enabled = !busy && visible.isNotEmpty(), onClick = {
                        selection = if (selectedItems.size == visible.size) emptySet() else visible.map(GalleryItem::id).toSet()
                    }) { Text(if (selectedItems.size == visible.size) "取消全选" else "全选已加载") }
                    TextButton(enabled = !busy && selectedItems.isNotEmpty() && cameraId != null, onClick = { tagRequest = selectedItems }) { Text("标签") }
                    TextButton(enabled = !busy && canDeleteGalleryItems(selectedItems, state), onClick = { deleteRequest = selectedItems }) { Text("删除") }
                }
                if (selectedItems.any { !it.canDelete }) Text("所选内容含不能安全删除的文件。", style = MaterialTheme.typography.bodySmall)
            }
            state.deletionNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (state.deleting) Text("正在发送删除请求…", style = MaterialTheme.typography.bodySmall)
            val tile: @Composable (GalleryItem) -> Unit = { item ->
                GalleryTile(item, layout, selecting, item.id in selection, item.id in state.submittedDeletes, thumbnailCache,
                    onOpen = { if (selecting) toggle(item.id) else previewId = item.id },
                    onSelect = { selecting = true; toggle(item.id) })
            }
            val footer: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        state.loading -> CircularProgressIndicator()
                        state.message != null -> Text(state.message, color = GlanceColors.inkMuted)
                        visible.isEmpty() -> Text(if (state.hasMore) "已加载的媒体中没有匹配项" else "没有符合条件的媒体")
                    }
                    if (state.hasMore && !busy) TextButton(onClick = onLoadMore) { Text("加载更多") }
                }
            }
            key(storage, filter, date, tag, oldestFirst, layout) {
                if (layout == GalleryLayout.Waterfall) {
                    val grid = rememberLazyStaggeredGridState()
                    val nearEnd by remember(itemCountWithHeaders) { derivedStateOf { (grid.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1) >= itemCountWithHeaders - 3 } }
                    GalleryLoadNearEnd(nearEnd, visible.size, state, initial >= 0, onLoadMore)
                    LazyVerticalStaggeredGrid(StaggeredGridCells.Adaptive(145.dp), Modifier.weight(1f), state = grid,
                        verticalItemSpacing = 2.dp, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        dates.forEach { (day, entries) ->
                            item(key = "date:$day", span = StaggeredGridItemSpan.FullLine) { GalleryDateHeading(day, today) }
                            items(entries, key = GalleryItem::id) { tile(it) }
                        }
                        item(span = StaggeredGridItemSpan.FullLine) { footer() }
                    }
                } else {
                    val grid = rememberLazyGridState()
                    val nearEnd by remember(itemCountWithHeaders) { derivedStateOf { (grid.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1) >= itemCountWithHeaders - 3 } }
                    GalleryLoadNearEnd(nearEnd, visible.size, state, initial >= 0, onLoadMore)
                    LazyVerticalGrid(if (layout == GalleryLayout.Grid) GridCells.Adaptive(108.dp) else GridCells.Fixed(1), Modifier.weight(1f), state = grid,
                        verticalArrangement = Arrangement.spacedBy(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        dates.forEach { (day, entries) ->
                            item(key = "date:$day", span = { GridItemSpan(maxLineSpan) }) { GalleryDateHeading(day, today) }
                            items(entries, key = GalleryItem::id) { tile(it) }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) { footer() }
                    }
                }
            }
        }
    }

    if (initial >= 0 && state.message == null) {
        GalleryPager(visible, initial, state.hasMore && !busy, onLoadMore, thumbnailCache, onClose = { previewId = null })
    }
    if (deleteRequest.isNotEmpty()) {
        val request = deleteRequest
        AlertDialog(onDismissRequest = { deleteRequest = emptyList() }, title = { Text("删除 ${request.size} 项？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("将向相机的「${request.first().storage.label}」发送删除请求。相机文件删除后无法撤销。")
                    request.take(4).forEach { Text(it.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
                    if (request.size > 4) Text("另有 ${request.size - 4} 项")
                }
            }, confirmButton = { TextButton(enabled = canDeleteGalleryItems(request, state) && !busy, onClick = {
                deleteRequest = emptyList(); previewId = null; clearSelection(); onDelete(request)
            }) { Text("确认删除", color = GlanceColors.coral) } },
            dismissButton = { TextButton(onClick = { deleteRequest = emptyList() }) { Text("取消") } })
    }
    if (tagRequest.isNotEmpty() && cameraId != null) {
        val request = tagRequest
        GalleryTagDialog(request.flatMap { itemTags(it) }.distinct().sorted(), onClose = { tagRequest = emptyList() }) { name, add ->
            onUpdateTag(request.map { galleryTagKey(cameraId, it.id) }.toSet(), name, add)
            tagRequest = emptyList()
        }
    }
}

@Composable
private fun GalleryDateHeading(date: LocalDate?, today: LocalDate) {
    val title = when (date) {
        null -> "日期未知"
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> if (date.year == today.year) "${date.monthValue}月${date.dayOfMonth}日"
        else "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
    Text(title, Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
}

private fun canDeleteGalleryItems(items: List<GalleryItem>, state: GalleryState): Boolean =
    items.isNotEmpty() && items.all { it.canDelete && it.id !in state.submittedDeletes }

@Composable
private fun GalleryMenu(label: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(label, maxLines = 1) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.distinct().forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelect(option) }) }
        }
    }
}

@Composable
private fun GalleryTagDialog(existing: List<String>, onClose: () -> Unit, onUpdate: (String, Boolean) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("本机标签") }, text = {
        Column {
            OutlinedTextField(text, { text = it.take(32) }, label = { Text("添加标签") }, singleLine = true)
            if (existing.isNotEmpty()) GalleryMenu("移除已有标签", existing) { onUpdate(it, false) }
        }
    }, confirmButton = { TextButton(enabled = text.isNotBlank() && text.trim() != "全部标签", onClick = { onUpdate(text.trim(), true) }) { Text("添加") } },
        dismissButton = { TextButton(onClick = onClose) { Text("取消") } })
}

@Composable
private fun GalleryLoadNearEnd(nearEnd: Boolean, count: Int, state: GalleryState, previewing: Boolean, onLoadMore: () -> Unit) {
    LaunchedEffect(nearEnd, count, state.hasMore, state.loading, state.deleting, previewing) {
        if (nearEnd && count > 0 && state.hasMore && !state.loading && !state.deleting && !previewing) {
            delay(200)
            onLoadMore()
        }
    }
}

@Composable
private fun GalleryTile(item: GalleryItem, layout: GalleryLayout, selecting: Boolean, selected: Boolean, submitted: Boolean,
    thumbnailCache: GalleryThumbnails, onOpen: () -> Unit, onSelect: () -> Unit) {
    val image by rememberGalleryImage(item, thumbnail = true, thumbnailCache)
    val thumbnail = image.bitmap
    val aspect = when (layout) {
        GalleryLayout.Grid -> 1f
        else -> thumbnail?.let { (it.width.toFloat() / it.height).coerceIn(0.45f, 2.6f) } ?: 1.25f
    }
    Box(Modifier.fillMaxWidth().aspectRatio(aspect).semantics { if (selecting) this.selected = selected }
        .combinedClickable(onLongClickLabel = "选择媒体", onLongClick = onSelect, onClick = onOpen), contentAlignment = Alignment.Center) {
        if (thumbnail != null) Image(thumbnail, item.title, Modifier.fillMaxSize(),
            contentScale = if (layout == GalleryLayout.Grid) ContentScale.Crop else ContentScale.Fit)
        else Text(if (item.kind == MediaKind.Video) "视频" else "照片", color = GlanceColors.inkMuted)
        if (selected) Box(Modifier.fillMaxSize().background(GlanceColors.pear.copy(alpha = 0.18f)))
        if (selecting) Text(if (selected) "✓" else "○", Modifier.align(Alignment.TopEnd).padding(5.dp)
            .clip(CircleShape).background(if (selected) GlanceColors.pear else GlanceColors.surface).padding(horizontal = 6.dp))
        val badge = when {
            submitted -> "待核对"
            item.kind == MediaKind.Video -> if (item.file.durationSeconds > 0) MonitorChromeLabels.formatElapsed(item.file.durationSeconds) else "视频"
            else -> null
        }
        badge?.let { Text(it, Modifier.align(Alignment.BottomEnd).background(GlanceColors.ink.copy(alpha = 0.7f))
            .padding(horizontal = 5.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelSmall) }
    }
}
