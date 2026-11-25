package com.opencapture.pocketglance.connection

import android.bluetooth.BluetoothManager
import android.content.Context
import android.view.Surface
import com.opencapture.opencine.dji.PocketCameraSession
import com.opencapture.opencine.vocabulary.CameraStatus
import com.opencapture.opencine.vocabulary.ConnectionPhase as DjiPhase
import com.opencapture.opencine.vocabulary.FoundCamera
import com.opencapture.pocketglance.session.GlanceSessionHost
import com.opencapture.pocketglance.session.SwiftCoreBootstrap
import com.opencapture.pocketglance.ui.MonitorChromeLabels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * SoftAP 无线：OpenCine [PocketCameraSession]。须先加载 JNI，并已授予附近设备权限。
 */
class WirelessSoftApTransport(
    context: Context,
    private val scope: CoroutineScope,
    private val preferredCameraId: String? = null,
    private val onCameraConnected: (String) -> Unit = {},
) : ConnectionTransport {
    override val mode: ConnectionMode = ConnectionMode.Wireless

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(ConnectionState(mode = mode))
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var session: PocketCameraSession? = null
    private var observeJob: Job? = null
    private var pendingSurface: Surface? = null
    private var autoConnectAttempted = false
    private var reportedLiveCamera: String? = null

    override suspend fun connect() {
        if (!SwiftCoreBootstrap.isAvailable) {
            _state.value =
                ConnectionState(
                    mode = mode,
                    phase = ConnectionPhase.Failed,
                    message = "无线核心未加载。请重新安装本应用，或先在电脑上完成原生库构建。",
                )
            return
        }
        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            _state.value =
                ConnectionState(
                    mode = mode,
                    phase = ConnectionPhase.Failed,
                    message = "本机没有蓝牙，无法无线连接 Pocket。",
                )
            return
        }
        if (!adapter.isEnabled) {
            _state.value =
                ConnectionState(
                    mode = mode,
                    phase = ConnectionPhase.Failed,
                    message = "请先打开手机蓝牙，再点无线连接。",
                )
            return
        }
        // 已有会话且仍在连/已直播：只补 Surface，不重开扫描。
        session?.let { existing ->
            if (existing.phase == DjiPhase.LIVE ||
                existing.phase == DjiPhase.JOINING_WIFI ||
                existing.phase == DjiPhase.OPENING_DATALINK ||
                existing.phase == DjiPhase.CONNECTING_GATT ||
                existing.phase == DjiPhase.PAIRING ||
                existing.phase == DjiPhase.AWAITING_APPROVAL ||
                existing.phase == DjiPhase.READING_WIFI_CREDS
            ) {
                pendingSurface?.let(existing::attachSurface)
                _state.value =
                    mapState(
                        existing.phase,
                        existing.decoder.isVerticalPicture.value,
                        existing.found.value,
                        existing.chromeStatus.value,
                    )
                return
            }
        }
        disconnectSession()
        autoConnectAttempted = false
        reportedLiveCamera = null
        val warmId = preferredCameraId
        _state.value =
            ConnectionState(
                mode = mode,
                phase = ConnectionPhase.Connecting,
                message =
                    if (warmId != null) "正在快速重连上次的 Pocket…"
                    else "正在扫描 Pocket…",
            )
        val created = PocketCameraSession(appContext, GlanceSessionHost(appContext))
        session = created
        pendingSurface?.let(created::attachSurface)
        observeJob =
            scope.launch {
                combine(
                    created.phaseFlow,
                    created.decoder.isVerticalPicture,
                    created.found,
                    created.chromeStatus,
                ) { phase, vertical, found, chrome ->
                    Quad(phase, vertical, found, chrome)
                }.collectLatest { (phase, vertical, found, chrome) ->
                    _state.value = mapState(phase, vertical, found, chrome)
                    if (phase == DjiPhase.LIVE) {
                        val id = created.connectedCamera?.id ?: found.firstOrNull()?.id
                        if (id != null && id != reportedLiveCamera) {
                            reportedLiveCamera = id
                            onCameraConnected(id)
                        }
                    }
                    if (!autoConnectAttempted &&
                        phase == DjiPhase.SCANNING &&
                        found.isNotEmpty() &&
                        warmId == null
                    ) {
                        autoConnectAttempted = true
                        created.connect(found.first())
                    }
                }
            }
        // 有上次相机 id：走会话暖重连，少一轮盲扫等待。
        if (warmId != null) {
            autoConnectAttempted = true
            created.startScan(reconnect = warmId)
        } else {
            created.startScan()
        }
    }

    /** 用户从列表点选某台相机。 */
    fun connectTo(cameraId: String) {
        val created = session ?: return
        val camera = created.found.value.firstOrNull { it.id == cameraId } ?: return
        autoConnectAttempted = true
        created.connect(camera)
    }

    override suspend fun disconnect() {
        disconnectSession()
        _state.value = ConnectionState(mode = mode, phase = ConnectionPhase.Idle)
    }

    override fun attachSurface(surface: Surface?) {
        pendingSurface = surface
        session?.attachSurface(surface)
    }

    private fun disconnectSession() {
        observeJob?.cancel()
        observeJob = null
        session?.disconnect()
        session = null
        autoConnectAttempted = false
        reportedLiveCamera = null
    }

    private fun mapState(
        phase: DjiPhase,
        cameraVertical: Boolean,
        found: List<FoundCamera>,
        status: CameraStatus,
    ): ConnectionState {
        val rows =
            found.map {
                FoundCameraRow(
                    id = it.id,
                    title = it.name.ifBlank { it.model.name },
                )
            }
        val (glancePhase, message) =
            when (phase) {
                DjiPhase.IDLE -> ConnectionPhase.Idle to ""
                DjiPhase.SCANNING ->
                    ConnectionPhase.Connecting to
                        if (rows.isEmpty()) "正在扫描 Pocket…"
                        else "已发现 ${rows.size} 台，正在连接…"
                DjiPhase.CONNECTING_GATT -> ConnectionPhase.Connecting to "正在建立蓝牙…"
                DjiPhase.PAIRING -> ConnectionPhase.Connecting to "正在配对…"
                DjiPhase.AWAITING_APPROVAL -> ConnectionPhase.Connecting to "请在相机屏幕上确认配对"
                DjiPhase.READING_WIFI_CREDS -> ConnectionPhase.Connecting to "正在读取相机热点…"
                DjiPhase.JOINING_WIFI -> ConnectionPhase.Connecting to "正在加入相机 Wi‑Fi…"
                DjiPhase.OPENING_DATALINK -> ConnectionPhase.Connecting to "正在打开直播通道…"
                DjiPhase.FAILED ->
                    ConnectionPhase.Failed to "连接失败。请确认已退出官方 Mimo，并靠近相机后重试。"
                DjiPhase.LIVE -> ConnectionPhase.Live to "无线已连接"
            }
        return ConnectionState(
            mode = mode,
            phase = glancePhase,
            message = message,
            cameraVertical = cameraVertical,
            foundCameras = rows,
            chrome = MonitorChromeLabels.from(status),
        )
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
