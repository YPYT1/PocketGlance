package com.opencapture.pocketglance.gallery

import com.opencapture.opencine.dji.DjiAndroidMediaService
import com.opencapture.opencine.media.MediaAssetID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class GlanceGallery(private val scope: CoroutineScope, private val service: () -> DjiAndroidMediaService?) {
    private val _state = MutableStateFlow(GalleryState())
    val state = _state.asStateFlow()
    private var browseJob: Job? = null
    private var pageRequests: Channel<Unit>? = null

    fun open() {
        val previous = browseJob
        previous?.cancel()
        val requests = Channel<Unit>(Channel.CONFLATED)
        pageRequests = requests
        _state.value = GalleryState(loading = true)
        browseJob = scope.launch {
            previous?.join()
            try {
                val source = service()
                if (source == null) {
                    _state.value = GalleryState(message = "请先连接相机；录制或恢复连接期间不能打开相册。")
                    return@launch
                }
                val browse = source.openBrowse()
                try {
                    if (browse.heldBySource == true) {
                        _state.value = GalleryState(message = "请退出相机屏幕上的相册，再点重试。")
                        awaitCancellation()
                    }
                    val cursor = browse.catalog()
                    try {
                        val entries = linkedMapOf<MediaAssetID, GalleryItem>()
                        while (true) {
                            val delta = cursor.next() ?: break
                            if (delta.reset) entries.clear()
                            delta.removed.forEach(entries::remove)
                            for (asset in delta.upserts) {
                                val record = source.mediaCatalogRecord(asset.id, browse.binding) ?: continue
                                GalleryItem.from(asset.id.rawValue, record.file, asset.kind)?.let { entries[asset.id] = it }
                            }
                            _state.value = GalleryState(
                                items = entries.values.sortedByDescending { it.file.filenameTimestamp.orEmpty() },
                                hasMore = !delta.complete,
                            )
                            if (delta.complete) break
                            requests.receive()
                            _state.value = _state.value.copy(loading = true)
                        }
                    } finally {
                        cursor.close()
                    }
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { browse.close() }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = _state.value.copy(loading = false, hasMore = false, message = "读取相机媒体失败，请重试。")
            } finally {
                requests.close()
            }
        }
    }

    fun loadMore() {
        if (_state.value.hasMore && !_state.value.loading) pageRequests?.trySend(Unit)
    }

    fun close() {
        browseJob?.cancel()
        _state.value = GalleryState()
    }

    suspend fun closeAndWait() {
        close()
        browseJob?.cancelAndJoin()
    }
}
