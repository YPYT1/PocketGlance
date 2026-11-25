package com.opencapture.pocketglance.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.opencapture.opencine.vocabulary.MediaHTTP
import com.opencapture.pocketglance.gallery.GalleryItem
import com.opencapture.pocketglance.gallery.GalleryState
import com.opencapture.pocketglance.gallery.GalleryStorage
import com.opencapture.pocketglance.gallery.MediaKind
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@Composable
internal fun GalleryScreen(
    state: GalleryState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
) {
    var storage by rememberSaveable { mutableStateOf(GalleryStorage.Internal) }
    var filter by rememberSaveable { mutableStateOf<MediaKind?>(null) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = remember(state.items, storage, filter) {
        state.items.filter { it.storage == storage && (filter == null || it.kind == filter) }
    }

    Box(Modifier.fillMaxSize().background(GlanceColors.canvas), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 1120.dp).fillMaxWidth().statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MonitorBackButton(onBack)
            Text("相机相册", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GalleryStorage.entries.forEach { store ->
                    FilterChip(selected = storage == store, onClick = { storage = store }, label = { Text(store.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "全部", MediaKind.Image to "照片", MediaKind.Video to "视频").forEach { (kind, label) ->
                    FilterChip(selected = filter == kind, onClick = { filter = kind }, label = { Text(label) })
                }
                TextButton(onClick = onRefresh, enabled = !state.loading) { Text("刷新") }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(138.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(visible, key = GalleryItem::id) { item ->
                    GalleryTile(item) { previewId = item.id }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        when {
                            state.loading -> CircularProgressIndicator()
                            state.message != null -> Text(state.message, color = GlanceColors.inkMuted)
                            visible.isEmpty() -> Text(if (state.hasMore) "这一批没有符合条件的媒体" else "未读取到${storage.label}中的媒体")
                        }
                        if (state.hasMore && !state.loading) TextButton(onClick = onLoadMore) { Text("加载更多") }
                    }
                }
            }
        }
    }

    state.items.firstOrNull { it.id == previewId && state.message == null }?.let { item ->
        GalleryPreview(item, onBackToMonitor = onBack) { previewId = null }
    }
}

@Composable
private fun GalleryTile(item: GalleryItem, onOpen: () -> Unit) {
    val image by rememberGalleryImage(item, thumbnail = true)
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).background(GlanceColors.surface).clickable(onClick = onOpen),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            val thumbnail = image.bitmap
            if (thumbnail != null) Image(thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(if (item.kind == MediaKind.Video) "视频" else "照片")
            if (item.kind == MediaKind.Video && item.file.durationSeconds > 0) {
                Text(MonitorChromeLabels.formatElapsed(item.file.durationSeconds), Modifier.align(Alignment.BottomEnd)
                    .background(GlanceColors.canvas).padding(horizontal = 6.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(item.title, Modifier.padding(8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun GalleryPreview(item: GalleryItem, onBackToMonitor: () -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 840.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(GlanceColors.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MonitorBackButton(onBackToMonitor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(item.storage.label, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onClose) { Text("关闭") }
            }
            if (item.kind == MediaKind.Video) CameraVideo(item)
            else {
                val image by rememberGalleryImage(item, thumbnail = false)
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 440.dp).aspectRatio(4f / 3f), contentAlignment = Alignment.Center) {
                    val picture = image.bitmap
                    when {
                        picture != null -> Image(picture, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        image.finished -> Text("这张照片暂时无法预览")
                        else -> CircularProgressIndicator()
                    }
                }
                if (image.isThumbnail) Text("原图暂不可用，显示相机缩略图", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CameraVideo(item: GalleryItem) {
    val context = LocalContext.current
    val paths = remember(item) { MediaHTTP.previewPaths(item.file) }
    val player = remember(item, context) { ExoPlayer.Builder(context).build() }
    var failed by remember(item) { mutableStateOf(false) }
    DisposableEffect(player, item) {
        var index = 0
        fun load() {
            val path = paths[index]
            player.setMediaItem(MediaItem.Builder().setUri(Uri.parse(item.url(path))).setMimeType(MimeTypes.VIDEO_MP4).build())
            player.prepare()
        }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                index++
                if (index < paths.size) load() else failed = true
            }
        }
        player.addListener(listener)
        player.playWhenReady = true
        load()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    if (failed) Text("无法播放此视频，请检查相机连接。")
    else AndroidView(
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).aspectRatio(16f / 9f),
        factory = { PlayerView(it) },
        update = { it.player = player },
        onRelease = { it.player = null },
    )
}

private data class GalleryImage(val bitmap: ImageBitmap? = null, val finished: Boolean = false, val isThumbnail: Boolean = false)

private val galleryImageSlots = Semaphore(4)

@Composable
private fun rememberGalleryImage(item: GalleryItem, thumbnail: Boolean) = produceState(GalleryImage(), item, thumbnail) {
    value = withContext(Dispatchers.IO) {
        galleryImageSlots.withPermit {
            val paths = if (thumbnail) listOf(item.file.thumbPath) else listOf(item.file.path, item.file.thumbPath)
            for (path in paths.filter(String::isNotBlank).distinct()) {
                try {
                    val url = URL(item.url(path))
                    val connection = url.openConnection() as HttpURLConnection
                    val bytes = try {
                        connection.instanceFollowRedirects = false
                        connection.connectTimeout = 4000
                        connection.readTimeout = 5000
                        val limit = if (path == item.file.thumbPath) 2 * 1024 * 1024 else 24 * 1024 * 1024
                        if (connection.responseCode != HttpURLConnection.HTTP_OK || connection.contentLengthLong > limit) continue
                        connection.inputStream.use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= limit)
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                    } finally { connection.disconnect() }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) continue
                    val maxEdge = if (thumbnail) 420 else 1800
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    if (bitmap != null) return@withPermit GalleryImage(bitmap.asImageBitmap(), true, path == item.file.thumbPath)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    continue
                }
            }
            GalleryImage(finished = true)
        }
    }
}
