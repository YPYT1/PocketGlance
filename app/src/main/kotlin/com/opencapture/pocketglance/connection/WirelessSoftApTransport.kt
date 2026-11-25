package com.opencapture.pocketglance.connection

import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import android.view.Surface
import com.opencapture.opencine.dji.PocketCameraSession
import com.opencapture.opencine.vocabulary.CameraStatus
import com.opencapture.opencine.vocabulary.ConnectionPhase as DjiPhase
import com.opencapture.opencine.vocabulary.FoundCamera
import com.opencapture.opencine.vocabulary.SessionRecoveryUi
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
                        existing.decoder.hasPicture.value,
                        existing.found.value,
                        existing.chromeStatus.value,
                        existing.recoveryState.value,
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
                pocketState = PocketState.Discovering,
            )
        val created = PocketCameraSession(appContext, GlanceSessionHost(appContext))
        session = created
        pendingSurface?.let(created::attachSurface)
        observeJob =
            scope.launch {
                combine(
                    combine(created.decoder.isVerticalPicture, created.decoder.hasPicture, ::PictureState),
                    created.phaseFlow, created.found, created.chromeStatus,
                    created.recoveryState,
                ) { picture, phase, found, chrome, recovery ->
                    SessionState(picture, phase, found, chrome, recovery)
                }.collectLatest { (picture, phase, found, chrome, recovery) ->
                    val next = mapState(phase, picture.vertical, picture.ready, found, chrome, recovery)
                    if (_state.value.pocketState != next.pocketState) {
                        Log.d("PocketGlance", "camera phase=$phase state=${next.pocketState}")
                    }
                    _state.value = next
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

    override fun notePresented(sourceTimestampNs: Long) {
        if (pendingSurface?.isValid == true) session?.decoder?.notePresented(sourceTimestampNs)
    }

    fun retryRecovery() {
        val current = session ?: return
        if (current.recoveryState.value.requiresOperator()) current.retrySessionRecovery()
    }

    private fun disconnectSession() {
        observeJob?.cancel()
        observeJob = null
        session?.close()
        session = null
        autoConnectAttempted = false
        reportedLiveCamera = null
    }

    private fun mapState(
        phase: DjiPhase,
        cameraVertical: Boolean,
        pictureReady: Boolean,
        found: List<FoundCamera>,
        status: CameraStatus,
        recovery: SessionRecoveryUi,
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
                DjiPhase.CONNECTING_GATT -> ConnectionPhase.Connecting to "正在建立蓝牙连接…"
                DjiPhase.PAIRING -> ConnectionPhase.Connecting to "正在发送唤醒和配对请求…"
                DjiPhase.AWAITING_APPROVAL -> ConnectionPhase.Connecting to "如 Pocket 屏幕提示，请确认配对。"
                DjiPhase.READING_WIFI_CREDS -> ConnectionPhase.Connecting to "正在读取相机热点…"
                DjiPhase.JOINING_WIFI -> ConnectionPhase.Connecting to "正在加入相机 Wi‑Fi…"
                DjiPhase.OPENING_DATALINK -> ConnectionPhase.Connecting to "正在打开直播通道…"
                DjiPhase.FAILED ->
                    ConnectionPhase.Failed to "连接失败。请确认已退出官方 Mimo，并靠近相机后重试。"
                DjiPhase.LIVE -> ConnectionPhase.Live to when (recovery) {
                    is SessionRecoveryUi.Retrying -> "正在恢复连接（${recovery.attempt}/${recovery.maxAttempts}）…"
                    is SessionRecoveryUi.TakenOver -> "相机已被另一台设备连接。"
                    is SessionRecoveryUi.PausedAfterDrops -> "连接反复中断，已暂停自动重试。"
                    is SessionRecoveryUi.WaitingForOperator -> "自动恢复未成功，请重试。"
                    SessionRecoveryUi.Idle -> "无线已连接"
                }
            }
        return ConnectionState(
            mode = mode,
            phase = glancePhase,
            message = message,
            cameraVertical = cameraVertical,
            pictureReady = pictureReady,
            foundCameras = rows,
            chrome = MonitorChromeLabels.from(status),
            pocketState = pocketStateFor(phase, pictureReady, recovery),
        )
    }

    private data class PictureState(val vertical: Boolean, val ready: Boolean)

    private data class SessionState(
        val picture: PictureState,
        val phase: DjiPhase,
        val found: List<FoundCamera>,
        val chrome: CameraStatus,
        val recovery: SessionRecoveryUi,
    )
}

internal fun pocketStateFor(phase: DjiPhase, pictureReady: Boolean, recovery: SessionRecoveryUi): PocketState {
    if (phase == DjiPhase.LIVE && recovery.isReconnecting) return PocketState.Recovering
    if (phase == DjiPhase.LIVE && recovery.requiresOperator()) return PocketState.NeedsRetry
    return when (phase) {
        DjiPhase.IDLE -> PocketState.Unknown
        DjiPhase.SCANNING -> PocketState.Discovering
        DjiPhase.CONNECTING_GATT -> PocketState.ConnectingBluetooth
        DjiPhase.PAIRING -> PocketState.Waking
        DjiPhase.AWAITING_APPROVAL -> PocketState.Pairing
        DjiPhase.READING_WIFI_CREDS, DjiPhase.JOINING_WIFI -> PocketState.JoiningWifi
        DjiPhase.OPENING_DATALINK -> PocketState.OpeningLink
        DjiPhase.LIVE -> if (pictureReady) PocketState.Monitoring else PocketState.Awake
        DjiPhase.FAILED -> PocketState.Failed
    }
}

private fun SessionRecoveryUi.requiresOperator(): Boolean = isRecovering && !isReconnecting
