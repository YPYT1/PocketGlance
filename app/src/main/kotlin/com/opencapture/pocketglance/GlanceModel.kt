package com.opencapture.pocketglance

import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opencapture.pocketglance.connection.ConnectionMode
import com.opencapture.pocketglance.connection.ConnectionPhase
import com.opencapture.pocketglance.connection.ConnectionState
import com.opencapture.pocketglance.connection.ConnectionTransport
import com.opencapture.pocketglance.connection.GlancePrefs
import com.opencapture.pocketglance.connection.StartupPreference
import com.opencapture.pocketglance.connection.WiredUsbTransport
import com.opencapture.pocketglance.connection.WirelessSoftApTransport
import com.opencapture.pocketglance.gallery.GalleryItem
import com.opencapture.pocketglance.gallery.MediaKind
import com.opencapture.pocketglance.gallery.MediaKindClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GlanceModel(app: Application) : AndroidViewModel(app) {
    private val prefs = GlancePrefs(app)

    val startupPreference: StateFlow<StartupPreference> =
        prefs.startupPreference.stateIn(viewModelScope, SharingStarted.Eagerly, StartupPreference.Last)

    val lastMode: StateFlow<ConnectionMode?> =
        prefs.lastMode.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _connection = MutableStateFlow(ConnectionState())
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

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

    fun setStartupPreference(value: StartupPreference) {
        viewModelScope.launch { prefs.setStartupPreference(value) }
    }

    fun setGalleryFilter(kind: MediaKind?) {
        _galleryFilter.value = kind
    }

    fun connect(mode: ConnectionMode) {
        viewModelScope.launch {
            val current = transport
            if (current != null &&
                current.mode == mode &&
                (_connection.value.phase == ConnectionPhase.Live ||
                    _connection.value.phase == ConnectionPhase.Connecting)
            ) {
                lastSurface?.let(current::attachSurface)
                return@launch
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
                    next.state.collect { _connection.value = it }
                }
            prefs.setLastMode(mode)
            lastSurface?.let(next::attachSurface)
            next.connect()
        }
    }

    fun connectToFoundCamera(cameraId: String) {
        viewModelScope.launch { prefs.setLastCameraId(cameraId) }
        (transport as? WirelessSoftApTransport)?.connectTo(cameraId)
    }

    fun disconnect() {
        viewModelScope.launch {
            transport?.disconnect()
            transport = null
            _connection.value = ConnectionState()
        }
    }

    fun attachSurface(surface: Surface?) {
        lastSurface = surface
        transport?.attachSurface(surface)
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
