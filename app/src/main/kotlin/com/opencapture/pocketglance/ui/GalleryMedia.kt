package com.opencapture.pocketglance.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.opencapture.opencine.vocabulary.MediaHTTP
import com.opencapture.pocketglance.gallery.GalleryItem
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
@androidx.annotation.OptIn(UnstableApi::class)
internal fun CameraVideo(
    item: GalleryItem,
    modifier: Modifier,
    fullscreen: Boolean,
    onFullscreen: (Boolean) -> Unit,
    onToggleThumbnails: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val paths = remember(item) { MediaHTTP.previewPaths(item.file) }
    val player = remember(item, context) { ExoPlayer.Builder(context).build() }
    var failed by remember(item) { mutableStateOf(false) }
    var videoAspect by remember(item) { mutableStateOf(16f / 9f) }
    DisposableEffect(player, item) {
        var index = 0
        fun load() {
            player.setMediaItem(MediaItem.Builder().setUri(Uri.parse(item.url(paths[index]))).setMimeType(MimeTypes.VIDEO_MP4).build())
            player.prepare()
        }
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspect = (videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height).coerceIn(0.2f, 5f)
                }
            }

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
    BoxWithConstraints(modifier.background(Color.Black).clickable(onClickLabel = "收起预览", onClick = onClose), contentAlignment = Alignment.Center) {
        if (failed) {
            Text("无法播放此视频", color = Color.White)
        } else {
            val width = minOf(maxWidth, maxHeight * videoAspect)
            AndroidView(
                modifier = Modifier.size(width, width / videoAspect),
                factory = {
                    PlayerView(it).apply {
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setControllerHideOnTouch(false)
                    }
                },
                update = { view ->
                    view.player = player
                    view.setOnClickListener {
                        if (!view.isControllerFullyVisible) view.showController()
                        onToggleThumbnails()
                    }
                    view.setFullscreenButtonClickListener { onFullscreen(it) }
                    view.setFullscreenButtonState(fullscreen)
                },
                onRelease = { it.player = null },
            )
        }
    }
}

internal data class GalleryImage(val bitmap: ImageBitmap? = null, val finished: Boolean = false)

private val galleryImageSlots = Semaphore(4)
private const val THUMBNAIL_CACHE_BYTES = 24 * 1024 * 1024
internal class GalleryThumbnails {
    val cache = object : LruCache<String, ImageBitmap>(THUMBNAIL_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
}

@Composable
internal fun rememberGalleryImage(item: GalleryItem, thumbnail: Boolean, thumbnails: GalleryThumbnails) =
    produceState(GalleryImage(), item, thumbnail, thumbnails) {
        val cached = thumbnails.cache.get(item.id)
        value = GalleryImage(cached, finished = thumbnail && cached != null)
        if (thumbnail && cached != null) return@produceState
        value = withContext(Dispatchers.IO) {
            galleryImageSlots.withPermit {
                val paths = if (thumbnail) listOf(item.file.thumbPath) else listOf(item.file.path, item.file.thumbPath)
                for (path in paths.filter(String::isNotBlank).distinct()) {
                    try {
                        val connection = URL(item.url(path)).openConnection() as HttpURLConnection
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
                        val options = BitmapFactory.Options().apply { inSampleSize = sample }
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap() ?: continue
                        if (thumbnail) thumbnails.cache.put(item.id, bitmap)
                        return@withPermit GalleryImage(bitmap, finished = true)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        continue
                    }
                }
                GalleryImage(cached, finished = true)
            }
        }
    }
