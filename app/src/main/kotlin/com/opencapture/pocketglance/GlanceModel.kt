package com.opencapture.pocketglance

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opencapture.opencine.media.FeedEffectsRenderPlan
import com.opencapture.opencine.media.MonitorLUTCube
import com.opencapture.pocketglance.connection.ConnectionMode
import com.opencapture.pocketglance.connection.ConnectionPhase
import com.opencapture.pocketglance.connection.ConnectionState
import com.opencapture.pocketglance.connection.ConnectionTransport
import com.opencapture.pocketglance.connection.GlancePrefs
import com.opencapture.pocketglance.connection.FocusGesture
import com.opencapture.pocketglance.connection.StartupPreference
import com.opencapture.pocketglance.connection.WiredUsbTransport
import com.opencapture.pocketglance.connection.WirelessSoftApTransport
import com.opencapture.pocketglance.gallery.GalleryItem
import com.opencapture.pocketglance.gallery.MediaKind
import com.opencapture.pocketglance.gallery.MediaKindClassifier
import com.opencapture.pocketglance.ui.PreviewLut
import com.opencapture.pocketglance.ui.parsePreviewCube
import com.opencapture.pocketglance.ui.previewLook
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class PreviewLutState(
    val entries: List<PreviewLut> = emptyList(),
    val selected: PreviewLut? = null,
    val plan: FeedEffectsRenderPlan? = null,
    val loading: PreviewLut? = null,
    val importing: Boolean = false,
    val message: String? = null,
)

class GlanceModel(app: Application) : AndroidViewModel(app) {
    private val prefs = GlancePrefs(app)

    val startupPreference: StateFlow<StartupPreference> =
        prefs.startupPreference.stateIn(viewModelScope, SharingStarted.Eagerly, StartupPreference.Last)

    val lastMode: StateFlow<ConnectionMode?> =
        prefs.lastMode.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _connection = MutableStateFlow(ConnectionState())
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _previewLuts = MutableStateFlow(PreviewLutState())
    internal val previewLuts = _previewLuts.asStateFlow()
    private var lutSelectionJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            val builtIns = withContext(Dispatchers.IO) {
                try {
                    app.assets.open("built-in-luts/catalog.tsv").bufferedReader().useLines { lines ->
                        lines.mapNotNull { line ->
                            val fields = line.split('\t', limit = 3)
                            if (fields.size == 3) PreviewLut("built-in-luts/${fields[0]}", fields[2], fields[1]) else null
                        }.toList()
                    }
                } catch (_: IOException) {
                    emptyList()
                }
            }
            _previewLuts.value = _previewLuts.value.copy(entries = builtIns + _previewLuts.value.entries)
        }
    }

    internal fun selectPreviewLut(lut: PreviewLut?) {
        lutSelectionJob?.cancel()
        if (lut == null) {
            _previewLuts.value = _previewLuts.value.copy(selected = null, plan = null, loading = null, message = null)
            return
        }
        lutSelectionJob = viewModelScope.launch {
            _previewLuts.value = _previewLuts.value.copy(loading = lut, message = null)
            try {
                val plan = withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val cube = if (lut.category == null) {
                        app.contentResolver.openInputStream(Uri.parse(lut.id))?.bufferedReader()?.use(::parsePreviewCube)
                    } else {
                        app.assets.open(lut.id).use { MonitorLUTCube.fromPacked(it.readBytes()) }
                    }
                    previewLook(cube ?: error("Cannot open LUT"))
                }
                _previewLuts.value = _previewLuts.value.copy(selected = lut, plan = plan, loading = null)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                val hint = if (lut.category == null) "请选择有效的 3D .cube 文件。" else "内置资源无法打开。"
                _previewLuts.value = _previewLuts.value.copy(loading = null, message = "无法读取「${lut.name}」。$hint")
            }
        }
    }

    internal fun importPreviewLuts(uris: List<Uri>) {
        if (uris.isEmpty() || _previewLuts.value.importing) return
        viewModelScope.launch {
            _previewLuts.value = _previewLuts.value.copy(importing = true, message = null)
            val accepted = mutableListOf<PreviewLut>()
            val existing = _previewLuts.value.entries
            var errors = 0
            for (uri in uris.distinct()) {
                if (existing.any { it.id == uri.toString() }) continue
                try {
                    accepted += withContext(Dispatchers.IO) {
                        val resolver = getApplication<Application>().contentResolver
                        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        } ?: uri.lastPathSegment.orEmpty()
                        require(name.endsWith(".cube", ignoreCase = true))
                        PreviewLut(uri.toString(), name.dropLast(5))
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    errors++
                }
            }
            _previewLuts.value = _previewLuts.value.copy(entries = _previewLuts.value.entries + accepted, importing = false,
                message = when {
                    errors > 0 -> "已添加 ${accepted.size} 个，跳过 $errors 个。请选择 3D .cube 文件。"
                    accepted.isEmpty() -> "所选文件已导入。"
                    else -> "已导入 ${accepted.size} 个。请选择一个 LUT 预览。"
                })
        }
    }

    private val _gallery = MutableStateFlow(sampleGallery())
    val gallery: StateFlow<List<GalleryItem>> = _gallery.asStateFlow()

    private val _galleryFilter = MutableStateFlow<MediaKind?>(null)
    val galleryFilter: StateFlow<MediaKind?> = _galleryFilter.asStateFlow()

    val filteredGallery: StateFlow<List<GalleryItem>> =
        combine(_gallery, _galleryFilter) { items, filter ->
            if (filter == null) items else items.filter { it.kind == filter }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, sampleGallery())

    private var transport: ConnectionTransport? = null
    private var observeJob: kotlinx.coroutines.Job? = null
    private var lastSurface: Surface? = null
    private val transitionMutex = Mutex()

    fun setStartupPreference(value: StartupPreference) {
        viewModelScope.launch { prefs.setStartupPreference(value) }
    }

    fun setGalleryFilter(kind: MediaKind?) {
        _galleryFilter.value = kind
    }

    fun connect(mode: ConnectionMode) {
        viewModelScope.launch {
            transitionMutex.withLock {
                val current = transport
                if (current != null &&
                    current.mode == mode &&
                    (_connection.value.phase == ConnectionPhase.Live ||
                        _connection.value.phase == ConnectionPhase.Connecting)
                ) {
                    liveSurface()?.let(current::attachSurface)
                    return@withLock
                }
                transport?.disconnect()
                observeJob?.cancel()
                val warmCamera =
                    if (mode == ConnectionMode.Wireless) prefs.lastCameraId.first() else null
                val next =
                    when (mode) {
                        ConnectionMode.Wireless ->
                            WirelessSoftApTransport(
                                getApplication(),
                                viewModelScope,
                                preferredCameraId = warmCamera,
                                onCameraConnected = { id ->
                                    viewModelScope.launch { prefs.setLastCameraId(id) }
                                },
                            )
                        ConnectionMode.Wired -> WiredUsbTransport(getApplication())
                    }
                transport = next
                observeJob =
                    viewModelScope.launch {
                        if (next is WirelessSoftApTransport) {
                            combine(next.state, next.focus) { connection, focus -> connection.copy(focus = focus) }
                                .collect { _connection.value = it }
                        } else {
                            next.state.collect { _connection.value = it }
                        }
                    }
                prefs.setLastMode(mode)
                liveSurface()?.let(next::attachSurface)
                next.connect()
            }
        }
    }

    fun connectToFoundCamera(cameraId: String) {
        viewModelScope.launch { prefs.setLastCameraId(cameraId) }
        (transport as? WirelessSoftApTransport)?.connectTo(cameraId)
    }

    fun disconnect() {
        viewModelScope.launch {
            transitionMutex.withLock {
                observeJob?.cancel()
                observeJob = null
                transport?.attachSurface(null)
                lastSurface = null
                transport?.disconnect()
                transport = null
                _connection.value = ConnectionState()
            }
        }
    }

    fun attachSurface(surface: Surface?) {
        lastSurface = surface?.takeIf { it.isValid }
        transport?.attachSurface(lastSurface)
    }

    fun notePresented(sourceTimestampNs: Long) {
        transport?.notePresented(sourceTimestampNs)
    }

    fun retryRecovery() {
        (transport as? WirelessSoftApTransport)?.retryRecovery()
    }

    fun focus(gesture: FocusGesture, x: Float, y: Float) {
        (transport as? WirelessSoftApTransport)?.focus(gesture, x, y)
    }

    private fun liveSurface(): Surface? =
        lastSurface?.takeIf { it.isValid } ?: run {
            lastSurface = null
            null
        }

    companion object {
        fun sampleGallery(): List<GalleryItem> =
            listOf(
                GalleryItem("1", "样片 静图 01", MediaKindClassifier.classify("DSC_1001.jpg")),
                GalleryItem("2", "样片 视频 08", MediaKindClassifier.classify("CLIP_08.mp4")),
                GalleryItem("3", "样片 动图 A", MediaKindClassifier.classify("MOTION_A.gif")),
                GalleryItem("4", "样片 代理 12", MediaKindClassifier.classify("PROXY_12.lrf")),
                GalleryItem("5", "样片 静图 22", MediaKind.Image),
                GalleryItem("6", "样片 动图 03", MediaKindClassifier.classify("LIVE_03.dng", motionFlag = true)),
            )
    }
}
