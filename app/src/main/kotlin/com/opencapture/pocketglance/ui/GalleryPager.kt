package com.opencapture.pocketglance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.opencapture.pocketglance.gallery.GalleryItem
import com.opencapture.pocketglance.gallery.MediaKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val VIDEO_THUMBNAIL_IDLE_MS = 3_000L

@Composable
internal fun GalleryPager(
    items: List<GalleryItem>,
    initialPage: Int,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    thumbnailCache: GalleryThumbnails,
    onClose: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = initialPage) { items.size }
    val thumbnails = rememberLazyListState(initialFirstVisibleItemIndex = (initialPage - 2).coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val close by rememberUpdatedState(onClose)
    val dismissProgress = remember { Animatable(0f) }
    var dismissing by remember { mutableStateOf(false) }
    val dismiss: () -> Unit = {
        if (!dismissing) {
            dismissing = true
            scope.launch {
                dismissProgress.animateTo(1f, tween(170))
                close()
            }
        }
    }
    var showThumbnails by remember { mutableStateOf(true) }
    var videoInteractTick by remember { mutableIntStateOf(0) }
    var imageZoom by remember { mutableFloatStateOf(1f) }
    var dragOffset by remember { mutableStateOf(0f) }
    val currentItem = items.getOrNull(pager.settledPage)
    val viewingVideo = currentItem?.kind == MediaKind.Video

    LaunchedEffect(pager.settledPage) {
        imageZoom = 1f
        val selected = pager.settledPage
        if (thumbnails.layoutInfo.visibleItemsInfo.none { it.index == selected }) {
            thumbnails.scrollToItem((selected - 2).coerceAtLeast(0))
        }
    }
    LaunchedEffect(pager.settledPage, items.size, canLoadMore) {
        if (canLoadMore && pager.settledPage >= items.lastIndex - 2) onLoadMore()
    }
    // 视频：3 秒无点击后自动收起底部缩略图；照片点出后保持显示。
    LaunchedEffect(showThumbnails, viewingVideo, pager.settledPage, videoInteractTick) {
        if (!showThumbnails || !viewingVideo) return@LaunchedEffect
        delay(VIDEO_THUMBNAIL_IDLE_MS)
        showThumbnails = false
    }

    fun noteVideoInteract() {
        showThumbnails = true
        videoInteractTick += 1
    }

    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val insets = window?.let { WindowCompat.getInsetsController(it, view) }
            insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { insets?.show(WindowInsetsCompat.Type.systemBars()) }
        }
        Column(Modifier.fillMaxSize().graphicsLayer {
            translationY = dragOffset
            alpha = 1f - dismissProgress.value
            val scale = 1f - dismissProgress.value * 0.12f
            scaleX = scale
            scaleY = scale
        }.background(Color.Black)
            .pointerInput(imageZoom) {
                if (imageZoom > 1.01f) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffset >= 72.dp.toPx()) dismiss() else dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                ) { change, delta ->
                    if (!dismissing && (delta > 0f || dragOffset > 0f)) {
                        change.consume()
                        dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                    }
                }
            }) {
            HorizontalPager(
                state = pager,
                key = { items[it].id },
                userScrollEnabled = imageZoom <= 1.01f,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { index ->
                items.getOrNull(index)?.let { item ->
                    if (item.kind == MediaKind.Video && index == pager.settledPage && !pager.isScrollInProgress) {
                        CameraVideo(
                            item = item,
                            modifier = Modifier.fillMaxSize(),
                            onUserInteract = ::noteVideoInteract,
                            onClose = dismiss,
                        )
                    } else {
                        BoxWithConstraints(
                            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { dismiss() } },
                            contentAlignment = Alignment.Center,
                        ) {
                            val image by rememberGalleryImage(item, thumbnail = item.kind == MediaKind.Video || index != pager.settledPage, thumbnailCache)
                            val picture = image.bitmap
                            if (picture != null) {
                                val aspect = picture.width.toFloat() / picture.height
                                val width = minOf(maxWidth, maxHeight * aspect)
                                ZoomableGalleryImage(
                                    bitmap = picture,
                                    contentDescription = item.title,
                                    modifier = Modifier.size(width, width / aspect),
                                    onTap = {
                                        // 照片：点一下只显示缩略图，不再点隐，也无 ripple。
                                        showThumbnails = true
                                    },
                                    onZoomChanged = { zoom ->
                                        if (index == pager.settledPage) imageZoom = zoom
                                    },
                                )
                            } else if (image.finished) Text("无法预览", color = Color.White)
                            else CircularProgressIndicator(color = GlanceColors.pear)
                        }
                    }
                }
            }
            if (showThumbnails) {
                LazyRow(
                    state = thumbnails,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        val thumbnail by rememberGalleryImage(item, thumbnail = true, thumbnailCache)
                        val active = index == pager.currentPage
                        Box(Modifier.size(width = if (active) 64.dp else 48.dp, height = 48.dp)
                            .semantics { selected = active }
                            .clickable {
                                scope.launch { pager.scrollToPage(index) }
                                if (items.getOrNull(index)?.kind == MediaKind.Video) noteVideoInteract()
                                else showThumbnails = true
                            }, contentAlignment = Alignment.Center) {
                            thumbnail.bitmap?.let { Image(it, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                ?: Text(if (item.kind == MediaKind.Video) "视频" else "照片", color = Color.White)
                            if (!active) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.24f)))
                        }
                    }
                }
            }
        }
    }
}
