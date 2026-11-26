package com.opencapture.pocketglance.gallery

import com.opencapture.opencine.dji.DjiAndroidMediaService
import com.opencapture.opencine.media.CameraMediaBrowse
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
    private var sessionJob: Job? = null
    private var listJob: Job? = null
    private var pageRequests: Channel<Unit>? = null
    private var activeBrowse: ActiveBrowse? = null
    private var browseHandle: CameraMediaBrowse? = null
    private var deleteJob: Job? = null

    private data class ActiveBrowse(
        val service: DjiAndroidMediaService,
        val binding: MediaBrowseBinding,
        val cameraId: String,
    )

    /** 首次进入相册：建立回放会话并拉目录。已在相册内时等价于 refresh。 */
    fun open() {
        if (deleteJob?.isActive == true) return
        if (activeBrowse != null && sessionJob?.isActive == true && browseHandle != null) {
            refresh()
            return
        }
        startSession()
    }

    /**
     * 刷新列表：只重开 catalog，不 close browse。
     * close 会 exitPlayback，相机会先回到拍摄模式再进相册。
     */
    fun refresh() {
        if (deleteJob?.isActive == true) return
        val active = activeBrowse
        val browse = browseHandle
        if (active == null || browse == null || sessionJob?.isActive != true) {
            startSession()
            return
        }
        startListing(active, browse)
    }

    private fun startSession() {
        val previous = sessionJob
        listJob?.cancel()
        activeBrowse = null
        browseHandle = null
        previous?.cancel()
        _state.value = GalleryState(loading = true)
        sessionJob = scope.launch {
            previous?.join()
            try {
                val camera = sourceForCamera()
                if (camera == null) {
                    _state.value = GalleryState(message = "请先连接相机；录制或恢复连接期间不能打开相册。")
                    return@launch
                }
                val (cameraId, source) = camera
                val browse = source.openBrowse()
                browseHandle = browse
                val active = ActiveBrowse(source, browse.binding, cameraId)
                try {
                    if (browse.heldBySource == true) {
                        _state.value = GalleryState(message = "请退出相机屏幕上的相册，再点重试。")
                        awaitCancellation()
                    }
                    activeBrowse = active
                    startListing(active, browse)
                    awaitCancellation()
                } finally {
                    listJob?.cancelAndJoin()
                    if (activeBrowse === active) activeBrowse = null
                    if (browseHandle === browse) browseHandle = null
                    deleteJob?.cancel()
                    withContext(NonCancellable) { browse.close() }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = _state.value.copy(loading = false, hasMore = false, message = "读取相机媒体失败，请重试。")
            }
        }
    }

    private fun startListing(active: ActiveBrowse, browse: CameraMediaBrowse) {
        val previousList = listJob
        previousList?.cancel()
        val requests = Channel<Unit>(Channel.CONFLATED)
        pageRequests = requests
        _state.value = _state.value.copy(
            loading = true,
            hasMore = false,
            message = null,
            deletionNote = null,
            cameraId = active.cameraId,
        )
        listJob = scope.launch {
            previousList?.join()
            if (activeBrowse !== active || browseHandle !== browse) {
                requests.close()
                return@launch
            }
            try {
                val cursor = browse.catalog()
                try {
                    val entries = linkedMapOf<MediaAssetID, GalleryItem>()
                    while (true) {
                        if (activeBrowse !== active) break
                        val delta = cursor.next() ?: break
                        if (delta.reset) entries.clear()
                        delta.removed.forEach(entries::remove)
                        for (asset in delta.upserts) {
                            val record = active.service.mediaCatalogRecord(asset.id, active.binding) ?: continue
                            GalleryItem.from(asset.id.rawValue, record.file, asset.kind)?.let { entries[asset.id] = it }
                        }
                        if (activeBrowse !== active) break
                        _state.value = _state.value.copy(
                            items = entries.values.sortedByDescending { it.file.filenameTimestamp.orEmpty() },
                            loading = false,
                            hasMore = !delta.complete,
                            cameraId = active.cameraId,
                            message = null,
                        )
                        if (delta.complete) break
                        requests.receive()
                        if (activeBrowse !== active) break
                        _state.value = _state.value.copy(loading = true)
                    }
                } finally {
                    cursor.close()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (activeBrowse === active) {
                    _state.value = _state.value.copy(loading = false, hasMore = false, message = "读取相机媒体失败，请重试。")
                }
            } finally {
                requests.close()
            }
        }
    }

    fun loadMore() {
        if (_state.value.hasMore && !_state.value.loading && !_state.value.deleting) pageRequests?.trySend(Unit)
    }

    /**
     * 真正删除：向相机发删除命令，短暂等待后从列表乐观移除（与 OpenPocketCine 一致）。
     * 不再保留「待核对」状态。
     */
    fun delete(items: List<GalleryItem>) {
        val active = activeBrowse ?: return
        if (_state.value.loading || _state.value.deleting || items.isEmpty()) return
        val requested = items.distinctBy(GalleryItem::id)
        val current = _state.value.items.associateBy(GalleryItem::id)
        if (requested.any { current[it.id] != it || !it.canDelete }) return
        _state.value = _state.value.copy(deleting = true, deletionNote = null)
        deleteJob = scope.launch {
            var removed = 0
            var failed = 0
            for (item in requested) {
                if (activeBrowse !== active) break
                val nativeId = MediaAssetID(item.id)
                val record = active.service.mediaCatalogRecord(nativeId, active.binding)
                if (record == null || record.file != item.file || !record.file.isDeletable) {
                    failed++
                    continue
                }
                val submission = try {
                    active.service.submitDeletion(nativeId, active.binding)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                if (submission == null || !active.service.isCurrent(submission)) {
                    failed++
                    continue
                }
                // 与 Pocket 产品一致：发令后等 400ms，再从本地列表拿掉。
                delay(400)
                if (activeBrowse !== active || !active.service.isCurrent(submission)) {
                    failed++
                    continue
                }
                val nextItems = _state.value.items.filterNot { it.id == item.id }
                _state.value = _state.value.copy(items = nextItems)
                removed++
            }
            if (activeBrowse === active) {
                val note = when {
                    removed == 0 && failed > 0 -> "删除失败，请稍后重试。"
                    removed > 0 && failed > 0 -> "已删除 $removed 项，$failed 项失败。"
                    removed > 0 -> "已删除 $removed 项。"
                    else -> "没有可删除的项目。"
                }
                _state.value = _state.value.copy(deleting = false, deletionNote = note)
            }
        }
    }

    fun close() {
        activeBrowse = null
        browseHandle = null
        deleteJob?.cancel()
        listJob?.cancel()
        sessionJob?.cancel()
        _state.value = GalleryState()
    }

    /** 取消会话并等到 browse.close → returnToLive / exitPlayback 跑完。 */
    suspend fun closeAndWait() {
        val browse = browseHandle
        close()
        listJob?.cancelAndJoin()
        sessionJob?.cancelAndJoin()
        // session 已结束后 finally 可能未挂上 browse；幂等补一刀。
        if (browse != null) {
            runCatching { withContext(NonCancellable) { browse.close() } }
        }
    }
}
