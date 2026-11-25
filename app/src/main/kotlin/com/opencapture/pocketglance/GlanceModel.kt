package com.opencapture.pocketglance

import android.app.Application
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbDevice
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
import com.opencapture.pocketglance.connection.PocketState
import com.opencapture.pocketglance.connection.StartupPreference
import com.opencapture.pocketglance.connection.WiredUsbTransport
import com.opencapture.pocketglance.connection.WirelessSoftApTransport
import com.opencapture.pocketglance.diagnostics.GlanceLog
import com.opencapture.pocketglance.gallery.GlanceGallery
import com.opencapture.pocketglance.monitor.MonitorOrientation
import com.opencapture.pocketglance.session.LiveKeepaliveService
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
    val loadingCatalog: Boolean = true,
    val catalogUnavailable: Boolean = false,
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

    val monitorOrientation = prefs.monitorOrientation.stateIn(viewModelScope, SharingStarted.Eagerly, MonitorOrientation.LandscapeLocked)

    val lastMode: StateFlow<ConnectionMode?> =
        prefs.lastMode.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _connection = MutableStateFlow(ConnectionState())
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()
    private val _monitorForeground = MutableStateFlow(false)
    val monitorForeground = _monitorForeground.asStateFlow()

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
            _previewLuts.value = _previewLuts.value.copy(
                entries = builtIns + _previewLuts.value.entries,
                loadingCatalog = false,
                catalogUnavailable = builtIns.isEmpty(),
            )
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

    private var transport: ConnectionTransport? = null
    internal val gallery = GlanceGallery(viewModelScope) { (transport as? WirelessSoftApTransport)?.galleryService() }
    internal val galleryTags = prefs.galleryTags.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    internal fun updateGalleryTag(keys: Set<String>, tag: String, add: Boolean) {
        if (keys.isNotEmpty() && tag.isNotBlank()) viewModelScope.launch { prefs.updateGalleryTag(keys, tag, add) }
    }
    private var observeJob: kotlinx.coroutines.Job? = null
    private var lastSurface: Surface? = null
    private val transitionMutex = Mutex()
    fun setMonitorForeground(foreground: Boolean) {
        _monitorForeground.value = foreground
        (transport as? WirelessSoftApTransport)?.setMonitorForeground(foreground)
    }

    fun setStartupPreference(value: StartupPreference) {
        viewModelScope.launch { prefs.setStartupPreference(value) }
    }

    fun setMonitorOrientation(value: MonitorOrientation) {
        viewModelScope.launch { prefs.setMonitorOrientation(value) }
    }

    fun connect(mode: ConnectionMode) {
        connect(mode, seedDevice = null, seedAccessory = null)
    }

    fun reportWiredPermissionDenied() {
        _connection.value = ConnectionState(
            mode = ConnectionMode.Wired,
            phase = ConnectionPhase.Failed,
            message = "未授予相机权限，无法打开外接 UVC 摄像头。请在系统设置允许相机权限后重试。",
            pocketState = PocketState.Failed,
        )
    }

    /** USB 插入时由 Activity 带上设备／附件种子，走有线 UVC 会话。 */
    fun connectWired(device: UsbDevice? = null, accessory: UsbAccessory? = null) {
        connect(ConnectionMode.Wired, seedDevice = device, seedAccessory = accessory)
    }

    private fun connect(
        mode: ConnectionMode,
        seedDevice: UsbDevice?,
        seedAccessory: UsbAccessory?,
    ) {
        viewModelScope.launch {
            transitionMutex.withLock {
                val current = transport
                if (current != null &&
                    current.mode == mode &&
                    seedDevice == null &&
                    seedAccessory == null &&
                    (_connection.value.phase == ConnectionPhase.Live ||
                        _connection.value.phase == ConnectionPhase.Connecting)
                ) {
                    liveSurface()?.let(current::attachSurface)
                    return@withLock
                }
                gallery.closeAndWait()
                transport?.disconnect()
                observeJob?.cancel()
                observeJob = null
                transport = null
                if (mode == ConnectionMode.Wireless) {
                    val started = runCatching { LiveKeepaliveService.start(getApplication()) }
                    if (started.isFailure) {
                        _connection.value = ConnectionState(mode, ConnectionPhase.Failed, "无法启用后台连接，请回到应用后重试。")
                        return@withLock
                    }
                } else {
                    LiveKeepaliveService.stop(getApplication())
                }
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
                        ConnectionMode.Wired ->
                            WiredUsbTransport(
                                getApplication(),
                                seedDevice = seedDevice,
                                seedAccessory = seedAccessory,
                            )
                    }
                transport = next
                (next as? WirelessSoftApTransport)?.setMonitorForeground(_monitorForeground.value)
                observeJob =
                    viewModelScope.launch {
                        if (next is WirelessSoftApTransport) {
                            combine(next.state, next.focus) { connection, focus -> connection.copy(focus = focus) }
                                .collect { state ->
                                    _connection.value = state
                                    if ((state.phase == ConnectionPhase.Failed && !next.hasRetryTarget) ||
                                        state.pocketState == PocketState.TakenOver) {
                                        LiveKeepaliveService.stop(getApplication())
                                    }
                                }
                        } else {
                            next.state.collect { _connection.value = it }
                        }
                    }
                prefs.setLastMode(mode)
                GlanceLog.i(
                    "connect",
                    "mode=$mode seedDevice=${seedDevice?.deviceName} seedAccessory=${seedAccessory?.model}",
                )
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
                gallery.closeAndWait()
                observeJob?.cancel()
                observeJob = null
                transport?.attachSurface(null)
                lastSurface = null
                transport?.disconnect()
                transport = null
                _connection.value = ConnectionState()
                LiveKeepaliveService.stop(getApplication())
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
        if (_connection.value.pocketState == PocketState.TakenOver) {
            if (runCatching { LiveKeepaliveService.start(getApplication()) }.isFailure) return
        }
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

}
