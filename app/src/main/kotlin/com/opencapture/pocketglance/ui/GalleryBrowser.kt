package com.opencapture.pocketglance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencapture.pocketglance.gallery.GalleryItem
import com.opencapture.pocketglance.gallery.GalleryState
import com.opencapture.pocketglance.gallery.GalleryStorage
import com.opencapture.pocketglance.gallery.MediaKind
import com.opencapture.pocketglance.gallery.galleryTagKey
import java.time.LocalDate
import kotlinx.coroutines.delay

private enum class GalleryLayout(val label: String, val icon: ImageVector) {
    Grid("宫格", Icons.Default.GridView),
    Waterfall("瀑布", Icons.Default.ViewStream),
    Large("大图", Icons.Default.ViewAgenda),
}

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
            GalleryNavBar(
                selecting = selecting,
                canSelect = !busy && (selecting || visible.isNotEmpty()),
                canRefresh = !busy,
                onBack = onBack,
                onToggleSelect = { if (selecting) clearSelection() else selecting = true },
                onRefresh = { clearSelection(); onRefresh() },
            )

            GalleryStorageSegment(
                selected = storage,
                onSelect = {
                    storage = it
                    date = "全部日期"
                    tag = null
                    clearSelection()
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            GalleryKindTabs(
                selected = filter,
                onSelect = { filter = it; clearSelection() },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )

            GalleryFilterBar(
                layout = layout,
                date = date,
                dateOptions = listOf("全部日期", "今天", "最近 7 天", "最近 30 天") + months,
                tag = tag,
                tagOptions = listOf("全部标签") + availableTags,
                oldestFirst = oldestFirst,
                onLayout = {
                    val order = GalleryLayout.entries
                    layout = order[(order.indexOf(layout) + 1) % order.size]
                },
                onDate = { date = it; clearSelection() },
                onTag = { tag = it.takeUnless { label -> label == "全部标签" }; clearSelection() },
                onSort = { oldestFirst = !oldestFirst },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )

            if (selecting) {
                GallerySelectionBar(
                    count = selectedItems.size,
                    allSelected = selectedItems.size == visible.size && visible.isNotEmpty(),
                    canTag = !busy && selectedItems.isNotEmpty() && cameraId != null,
                    canDelete = !busy && canDeleteGalleryItems(selectedItems, state),
                    busy = busy,
                    hasUnsafe = selectedItems.any { !it.canDelete },
                    onToggleAll = {
                        selection = if (selectedItems.size == visible.size) emptySet() else visible.map(GalleryItem::id).toSet()
                    },
                    onTag = { tagRequest = selectedItems },
                    onDelete = { deleteRequest = selectedItems },
                )
            }

            state.deletionNote?.let {
                Text(it, Modifier.padding(horizontal = 20.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall)
            }
            if (state.deleting) {
                Text("正在发送删除请求…", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall)
            }

            val tile: @Composable (GalleryItem) -> Unit = { item ->
                GalleryTile(
                    item = item,
                    layout = layout,
                    selecting = selecting,
                    selected = item.id in selection,
                    thumbnailCache = thumbnailCache,
                    onOpen = { if (selecting) toggle(item.id) else previewId = item.id },
                    onSelect = { selecting = true; toggle(item.id) },
                )
            }
            val footer: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        state.loading -> CircularProgressIndicator(color = GlanceColors.ink, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                        state.message != null -> Text(state.message, color = GlanceColors.inkMuted)
                        visible.isEmpty() -> Text(
                            if (state.hasMore) "已加载内容中没有匹配项" else "没有符合条件的媒体",
                            color = GlanceColors.inkMuted,
                        )
                    }
                    if (state.hasMore && !busy) {
                        TextButton(onClick = onLoadMore) { Text("加载更多", color = GlanceColors.azure) }
                    }
                }
            }
            key(storage, filter, date, tag, oldestFirst, layout) {
                if (layout == GalleryLayout.Waterfall) {
                    val grid = rememberLazyStaggeredGridState()
                    val nearEnd by remember(itemCountWithHeaders) {
                        derivedStateOf { (grid.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1) >= itemCountWithHeaders - 3 }
                    }
                    GalleryLoadNearEnd(nearEnd, visible.size, state, initial >= 0, onLoadMore)
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Adaptive(145.dp),
                        modifier = Modifier.weight(1f),
                        state = grid,
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        verticalItemSpacing = 2.dp,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        dates.forEach { (day, entries) ->
                            item(key = "date:$day", span = StaggeredGridItemSpan.FullLine) { GalleryDateHeading(day, today) }
                            items(entries, key = GalleryItem::id) { tile(it) }
                        }
                        item(span = StaggeredGridItemSpan.FullLine) { footer() }
                    }
                } else {
                    val grid = rememberLazyGridState()
                    val nearEnd by remember(itemCountWithHeaders) {
                        derivedStateOf { (grid.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1) >= itemCountWithHeaders - 3 }
                    }
                    GalleryLoadNearEnd(nearEnd, visible.size, state, initial >= 0, onLoadMore)
                    LazyVerticalGrid(
                        columns = if (layout == GalleryLayout.Grid) GridCells.Adaptive(108.dp) else GridCells.Fixed(1),
                        modifier = Modifier.weight(1f),
                        state = grid,
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
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
        AlertDialog(
            onDismissRequest = { deleteRequest = emptyList() },
            title = { Text("删除 ${request.size} 项？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("将向相机的「${request.first().storage.label}」发送删除请求。相机文件删除后无法撤销。")
                    request.take(4).forEach {
                        Text(it.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                    if (request.size > 4) Text("另有 ${request.size - 4} 项")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canDeleteGalleryItems(request, state) && !busy,
                    onClick = {
                        deleteRequest = emptyList()
                        previewId = null
                        clearSelection()
                        onDelete(request)
                    },
                ) { Text("确认删除", color = GlanceColors.coral) }
            },
            dismissButton = { TextButton(onClick = { deleteRequest = emptyList() }) { Text("取消") } },
        )
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
private fun GalleryNavBar(
    selecting: Boolean,
    canSelect: Boolean,
    canRefresh: Boolean,
    onBack: () -> Unit,
    onToggleSelect: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = GlanceColors.ink)
        }
        Text(
            if (selecting) "选择项目" else "相册",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        TextButton(onClick = onToggleSelect, enabled = canSelect) {
            Text(if (selecting) "完成" else "选择", color = if (canSelect) GlanceColors.azure else GlanceColors.inkMuted)
        }
        IconButton(onClick = onRefresh, enabled = canRefresh) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "刷新",
                tint = if (canRefresh) GlanceColors.inkMuted else GlanceColors.hairline,
            )
        }
    }
}

/** 系统相册式分段：机身 / 存储卡。 */
@Composable
private fun GalleryStorageSegment(
    selected: GalleryStorage,
    onSelect: (GalleryStorage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFE8E2D8))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        GalleryStorage.entries.forEach { store ->
            val active = selected == store
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) GlanceColors.surface else Color.Transparent)
                    .clickable { onSelect(store) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (store == GalleryStorage.Internal) "机身" else "存储卡",
                    color = if (active) GlanceColors.ink else GlanceColors.inkMuted,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/** 下划线标签：全部 / 照片 / 视频。 */
@Composable
private fun GalleryKindTabs(
    selected: MediaKind?,
    onSelect: (MediaKind?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(null to "全部", MediaKind.Image to "照片", MediaKind.Video to "视频")
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tabs.forEach { (kind, label) ->
            val active = selected == kind
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(kind) }
                    .padding(top = 10.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    color = if (active) GlanceColors.ink else GlanceColors.inkMuted,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .width(22.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (active) GlanceColors.ink else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun GalleryFilterBar(
    layout: GalleryLayout,
    date: String,
    dateOptions: List<String>,
    tag: String?,
    tagOptions: List<String>,
    oldestFirst: Boolean,
    onLayout: () -> Unit,
    onDate: (String) -> Unit,
    onTag: (String) -> Unit,
    onSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GalleryQuietMenu(
            label = if (date == "全部日期") "日期" else date,
            active = date != "全部日期",
            options = dateOptions.distinct(),
            onSelect = onDate,
        )
        GalleryQuietMenu(
            label = tag ?: "标签",
            active = tag != null,
            options = tagOptions.distinct(),
            onSelect = onTag,
        )
        GalleryQuietChip(label = if (oldestFirst) "最早" else "最新", active = oldestFirst, onClick = onSort)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onLayout, modifier = Modifier.size(36.dp)) {
            Icon(layout.icon, contentDescription = "布局：${layout.label}", tint = GlanceColors.inkMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun GalleryQuietChip(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) GlanceColors.ink.copy(alpha = 0.08f) else Color.Transparent)
            .border(1.dp, if (active) GlanceColors.ink.copy(alpha = 0.12f) else GlanceColors.hairline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (active) GlanceColors.ink else GlanceColors.inkMuted, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun GalleryQuietMenu(label: String, active: Boolean, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (active) GlanceColors.ink.copy(alpha = 0.08f) else Color.Transparent)
                .border(1.dp, if (active) GlanceColors.ink.copy(alpha = 0.12f) else GlanceColors.hairline, RoundedCornerShape(16.dp))
                .clickable { expanded = true }
                .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = if (active) GlanceColors.ink else GlanceColors.inkMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = GlanceColors.inkMuted, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun GallerySelectionBar(
    count: Int,
    allSelected: Boolean,
    canTag: Boolean,
    canDelete: Boolean,
    busy: Boolean,
    hasUnsafe: Boolean,
    onToggleAll: () -> Unit,
    onTag: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(GlanceColors.surface).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("已选 $count 项", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
            TextButton(enabled = !busy, onClick = onToggleAll) {
                Text(if (allSelected) "取消全选" else "全选", color = GlanceColors.azure)
            }
            TextButton(enabled = canTag, onClick = onTag) { Text("标签", color = if (canTag) GlanceColors.azure else GlanceColors.inkMuted) }
            TextButton(enabled = canDelete, onClick = onDelete) { Text("删除", color = if (canDelete) GlanceColors.coral else GlanceColors.inkMuted) }
        }
        if (hasUnsafe) {
            Text("所选内容含不能安全删除的文件。", Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall)
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
    Text(
        title,
        Modifier.padding(start = 18.dp, top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

private fun canDeleteGalleryItems(items: List<GalleryItem>, state: GalleryState): Boolean =
    items.isNotEmpty() && !state.deleting && items.all { it.canDelete }

@Composable
private fun GalleryTagDialog(existing: List<String>, onClose: () -> Unit, onUpdate: (String, Boolean) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("本机标签") },
        text = {
            Column {
                OutlinedTextField(text, { text = it.take(32) }, label = { Text("添加标签") }, singleLine = true)
                if (existing.isNotEmpty()) {
                    GalleryQuietMenu("移除已有标签", active = false, options = existing) { onUpdate(it, false) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && text.trim() != "全部标签", onClick = { onUpdate(text.trim(), true) }) {
                Text("添加")
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("取消") } },
    )
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
private fun GalleryTile(
    item: GalleryItem,
    layout: GalleryLayout,
    selecting: Boolean,
    selected: Boolean,
    thumbnailCache: GalleryThumbnails,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
) {
    val image by rememberGalleryImage(item, thumbnail = true, thumbnailCache)
    val thumbnail = image.bitmap
    val aspect = when (layout) {
        GalleryLayout.Grid -> 1f
        else -> thumbnail?.let { (it.width.toFloat() / it.height).coerceIn(0.45f, 2.6f) } ?: 1.25f
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .semantics { if (selecting) this.selected = selected }
            .combinedClickable(onLongClickLabel = "选择媒体", onLongClick = onSelect, onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null) {
            Image(
                thumbnail,
                item.title,
                Modifier.fillMaxSize(),
                contentScale = if (layout == GalleryLayout.Grid) ContentScale.Crop else ContentScale.Fit,
            )
        } else {
            Text(if (item.kind == MediaKind.Video) "视频" else "照片", color = GlanceColors.inkMuted)
        }
        if (selected) Box(Modifier.fillMaxSize().background(GlanceColors.azure.copy(alpha = 0.18f)))
        if (selecting) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) GlanceColors.azure else Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp),
            )
        }
        val badge =
            if (item.kind == MediaKind.Video) {
                if (item.file.durationSeconds > 0) MonitorChromeLabels.formatElapsed(item.file.durationSeconds) else "视频"
            } else {
                null
            }
        badge?.let {
            Text(
                it,
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(GlanceColors.ink.copy(alpha = 0.72f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
