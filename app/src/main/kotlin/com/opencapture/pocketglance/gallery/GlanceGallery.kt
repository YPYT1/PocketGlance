package com.opencapture.pocketglance.gallery

import com.opencapture.opencine.dji.DjiAndroidMediaService
import com.opencapture.opencine.media.MediaAssetID
import com.opencapture.opencine.media.MediaBrowseBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class GlanceGallery(private val scope: CoroutineScope, private val sourceForCamera: () -> Pair<String, DjiAndroidMediaService>?) {
    private val _state = MutableStateFlow(GalleryState())
    val state = _state.asStateFlow()
    private var browseJob: Job? = null
    private var pageRequests: Channel<Unit>? = null
    private var activeBrowse: ActiveBrowse? = null
    private var deleteJob: Job? = null

    private data class ActiveBrowse(val service: DjiAndroidMediaService, val binding: MediaBrowseBinding)

    fun open() {
        if (deleteJob?.isActive == true) return
        val previous = browseJob
        activeBrowse = null
        previous?.cancel()
        val requests = Channel<Unit>(Channel.CONFLATED)
        pageRequests = requests
        _state.value = GalleryState(loading = true)
        browseJob = scope.launch {
            previous?.join()
            try {
                val camera = sourceForCamera()
                if (camera == null) {
                    _state.value = GalleryState(message = "请先连接相机；录制或恢复连接期间不能打开相册。")
                    return@launch
                }
                val (cameraId, source) = camera
                val browse = source.openBrowse()
                val active = ActiveBrowse(source, browse.binding)
                try {
                    if (browse.heldBySource == true) {
                        _state.value = GalleryState(message = "请退出相机屏幕上的相册，再点重试。")
                        awaitCancellation()
                    }
                    activeBrowse = active
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
                            _state.value = _state.value.copy(
                                items = entries.values.sortedByDescending { it.file.filenameTimestamp.orEmpty() },
                                loading = false,
                                hasMore = !delta.complete,
                                cameraId = cameraId,
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
                    if (activeBrowse === active) activeBrowse = null
                    deleteJob?.cancel()
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
        if (_state.value.hasMore && !_state.value.loading && !_state.value.deleting) pageRequests?.trySend(Unit)
    }

    fun delete(items: List<GalleryItem>) {
        val active = activeBrowse ?: return
        if (_state.value.loading || _state.value.deleting || items.isEmpty()) return
        val requested = items.distinctBy(GalleryItem::id)
        val current = _state.value.items.associateBy(GalleryItem::id)
        if (requested.any { current[it.id] != it || !it.canDelete || it.id in _state.value.submittedDeletes }) return
        _state.value = _state.value.copy(deleting = true, deletionNote = null)
        deleteJob = scope.launch {
            var sent = 0
            for (item in requested) {
                if (activeBrowse !== active) break
                val nativeId = MediaAssetID(item.id)
                val record = active.service.mediaCatalogRecord(nativeId, active.binding) ?: continue
                if (record.file != item.file || !record.file.isDeletable) continue
                val submission = try {
                    active.service.submitDeletion(nativeId, active.binding)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                if (submission != null) {
                    sent++
                    _state.value = _state.value.copy(submittedDeletes = _state.value.submittedDeletes + item.id)
                    delay(400)
                }
            }
            if (activeBrowse === active) {
                val skipped = requested.size - sent
                val note = when {
                    sent == 0 -> "没有发送删除请求，请刷新相册后再试。"
                    skipped > 0 -> "已发送 $sent 项删除请求，$skipped 项未发送。请刷新核对相机结果。"
                    else -> "已发送 $sent 项删除请求，请刷新核对相机结果。"
                }
                _state.value = _state.value.copy(deleting = false, deletionNote = note)
            }
        }
    }

    fun close() {
        activeBrowse = null
        deleteJob?.cancel()
        browseJob?.cancel()
        _state.value = GalleryState()
    }

    suspend fun closeAndWait() {
        close()
        browseJob?.cancelAndJoin()
    }
}
