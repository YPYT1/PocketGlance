package com.opencapture.pocketglance.connection

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.opencapture.opencine.dji.PocketCameraSession
import com.opencapture.opencine.vocabulary.CameraStatus
import com.opencapture.opencine.vocabulary.CameraCommands
import com.opencapture.opencine.vocabulary.ConnectionPhase as DjiPhase
import com.opencapture.opencine.vocabulary.FoundCamera
import com.opencapture.opencine.vocabulary.FocusOverlay
import com.opencapture.opencine.vocabulary.SessionRecoveryUi
import com.opencapture.pocketglance.session.GlanceSessionHost
import com.opencapture.pocketglance.session.SwiftCoreBootstrap
import com.opencapture.pocketglance.ui.MonitorChromeLabels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update

/**
 * SoftAP 无线：OpenCine [PocketCameraSession]。须先加载 JNI，并已授予附近设备权限。
 *
 * 断开后立刻重连容易撞上 BLE/Wi‑Fi 未放完的时序窗，表现为 FAILED
 * （界面文案是「请确认已退出官方 Mimo」）。这里用静默期 + 暖重连自动重试兜底。
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
    private val _focus = MutableStateFlow(MonitorFocus())
    val focus = _focus.asStateFlow()

    private var session: PocketCameraSession? = null
    private var observeJob: Job? = null
    private var trackingJob: Job? = null
    private var focusJob: Job? = null
    private var pendingSurface: Surface? = null
    private var autoConnectAttempted = false
    private var reportedLiveCamera: String? = null
    private var lastDisconnectAtMs = 0L
    private var warmRetryAttempt = 0
    private var warmRetryJob: Job? = null

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
                        existing.decoder.pictureWidth to existing.decoder.pictureHeight,
                    )
                return
            }
        }
        awaitDisconnectQuietPeriod()
        beginSession(preferredCameraId, resetWarmRetries = true)
    }

    /** 用户从列表点选某台相机。 */
    fun connectTo(cameraId: String) {
        val created = session ?: return
        val camera = created.found.value.firstOrNull { it.id == cameraId } ?: return
        autoConnectAttempted = true
        warmRetryJob?.cancel()
        created.connect(camera)
    }

    override suspend fun disconnect() {
        warmRetryJob?.cancel()
        warmRetryJob = null
        focusJob?.join()
        session?.let { releaseFocusLock(it, _focus.value.lock) }
        disconnectSession()
        lastDisconnectAtMs = SystemClock.elapsedRealtime()
        warmRetryAttempt = 0
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

    fun focus(gesture: FocusGesture, x: Float, y: Float) {
        val current = session ?: return
        if (!current.canUseMonitorFocus || _state.value.pocketState != PocketState.Monitoring || focusJob?.isActive == true) return
        val before = _focus.value
        if (gesture == FocusGesture.Unlock && before.lock == null) return
        _focus.update { it.copy(pending = true, notice = null) }
        focusJob = scope.launch {
            try {
                if (before.lock != null) {
                    if (!releaseFocusLock(current, before.lock)) {
                        _focus.update { it.copy(notice = "相机未确认解除锁定") }
                        return@launch
                    }
                    _focus.update { it.copy(lock = null) }
                }
                when (gesture) {
                    FocusGesture.Unlock -> _focus.update { it.copy(point = null) }
                    FocusGesture.StopTracking -> current.cancelTracking()
                    FocusGesture.Track -> {
                        current.startTracking(x, y)
                        _focus.update { it.copy(point = null) }
                    }
                    FocusGesture.Tap, FocusGesture.Hold -> {
                        val confirmed = current.focusForMonitor(x, y)
                        val lock = if (confirmed && gesture == FocusGesture.Hold) holdFocus(current, x, y) else null
                        _focus.update {
                            it.copy(
                                point = if (confirmed) x to y else null,
                                lock = lock,
                                notice = when {
                                    !confirmed -> "相机未确认对焦"
                                    gesture == FocusGesture.Hold && lock == null -> "相机未确认对焦锁定"
                                    else -> null
                                },
                            )
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _focus.update { it.copy(notice = "相机暂时无法对焦") }
            } finally {
                _focus.update { it.copy(pending = false) }
            }
        }
    }

    private suspend fun holdFocus(current: PocketCameraSession, x: Float, y: Float): MonitorFocusLock? {
        if (current.setMonitorAfLock(true)) return MonitorFocusLock.Native
        return when (current.status.value.focusMode) {
            CameraCommands.FOCUS_SINGLE -> MonitorFocusLock.PreviouslySingleAf
            CameraCommands.FOCUS_CONTINUOUS -> {
                if (!current.setMonitorFocusMode(continuous = false)) return null
                if (current.focusForMonitor(x, y)) MonitorFocusLock.SingleAf
                else if (!current.setMonitorFocusMode(continuous = true)) MonitorFocusLock.SingleAf
                else null
            }
            else -> null
        }
    }

    private suspend fun releaseFocusLock(current: PocketCameraSession, lock: MonitorFocusLock?): Boolean = when (lock) {
        MonitorFocusLock.Native -> current.setMonitorAfLock(false)
        MonitorFocusLock.SingleAf -> current.setMonitorFocusMode(continuous = true)
        MonitorFocusLock.PreviouslySingleAf, null -> true
    }

    private suspend fun awaitDisconnectQuietPeriod() {
        val elapsed = SystemClock.elapsedRealtime() - lastDisconnectAtMs
        val remaining = DISCONNECT_QUIET_MS - elapsed
        if (lastDisconnectAtMs > 0L && remaining > 0L) {
            Log.d(TAG, "quiet ${remaining}ms after disconnect before rescan")
            _state.value =
                ConnectionState(
                    mode = mode,
                    phase = ConnectionPhase.Connecting,
                    message = "正在等待上次连接释放…",
                    pocketState = PocketState.Discovering,
                )
            delay(remaining)
        }
    }

    private fun beginSession(warmId: String?, resetWarmRetries: Boolean) {
        disconnectSession()
        if (resetWarmRetries) warmRetryAttempt = 0
        autoConnectAttempted = false
        reportedLiveCamera = null
        _state.value =
            ConnectionState(
                mode = mode,
                phase = ConnectionPhase.Connecting,
                message =
                    when {
                        warmId != null && warmRetryAttempt > 0 ->
                            "重连未成功，正在重试（${warmRetryAttempt + 1}/$MAX_WARM_RETRIES）…"
                        warmId != null -> "正在快速重连上次的 Pocket…"
                        else -> "正在扫描 Pocket…"
                    },
                pocketState = PocketState.Discovering,
            )
        val created = PocketCameraSession(appContext, GlanceSessionHost(appContext))
        val raster = MutableStateFlow(created.decoder.pictureWidth to created.decoder.pictureHeight)
        created.decoder.onPictureSizeObserved = { width, height -> raster.value = width to height }
        session = created
        trackingJob = scope.launch {
            created.trackingHud.collect { hud ->
                val box = when (val overlay = hud.overlay) {
                    is FocusOverlay.Search -> overlay.box
                    is FocusOverlay.Subject -> overlay.box
                    else -> null
                }
                _focus.update { it.copy(trackBox = box, tracking = hud.isTracking) }
            }
        }
        pendingSurface?.let(created::attachSurface)
        observeJob =
            scope.launch {
                combine(
                    combine(created.decoder.isVerticalPicture, created.decoder.hasPicture, raster, ::PictureState),
                    created.phaseFlow, created.found, created.chromeStatus,
                    created.recoveryState,
                ) { picture, phase, found, chrome, recovery ->
                    SessionState(picture, phase, found, chrome, recovery)
                }.collectLatest { (picture, phase, found, chrome, recovery) ->
                    val next = mapState(phase, picture.vertical, picture.ready, found, chrome, recovery, picture.raster)
                    if (_state.value.pocketState != next.pocketState) {
                        Log.d(TAG, "camera phase=$phase state=${next.pocketState}")
                    }
                    _state.value = next
                    val available = next.pocketState == PocketState.Monitoring && created.canUseMonitorFocus
                    if (_focus.value.available != available) {
                        _focus.update { it.copy(available = available, point = null, trackBox = null, notice = null) }
                    }
                    if (phase == DjiPhase.LIVE) {
                        warmRetryAttempt = 0
                        warmRetryJob?.cancel()
                        warmRetryJob = null
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
                    if (phase == DjiPhase.FAILED && warmId != null) {
                        scheduleWarmRetry(warmId)
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

    private fun scheduleWarmRetry(warmId: String) {
        if (warmRetryJob?.isActive == true) return
        if (warmRetryAttempt + 1 >= MAX_WARM_RETRIES) {
            Log.w(TAG, "warm reconnect exhausted after $MAX_WARM_RETRIES attempts")
            return
        }
        warmRetryAttempt += 1
        val waitMs = WARM_RETRY_BASE_MS * warmRetryAttempt
        Log.i(TAG, "warm reconnect retry $warmRetryAttempt in ${waitMs}ms")
        warmRetryJob =
            scope.launch {
                _state.value =
                    _state.value.copy(
                        phase = ConnectionPhase.Connecting,
                        message = "连接未完成，${waitMs / 1000} 秒后自动重试…",
                        pocketState = PocketState.Discovering,
                    )
                delay(waitMs)
                beginSession(warmId, resetWarmRetries = false)
            }
    }

    private fun disconnectSession() {
        warmRetryJob?.cancel()
        warmRetryJob = null
        observeJob?.cancel()
        observeJob = null
        trackingJob?.cancel()
        trackingJob = null
        focusJob?.cancel()
        focusJob = null
        _focus.value = MonitorFocus()
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
        raster: Pair<Int, Int>,
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
                    ConnectionPhase.Failed to
                        if (warmRetryAttempt + 1 < MAX_WARM_RETRIES && preferredCameraId != null) {
                            "连接未完成，即将自动重试…"
                        } else {
                            "连接失败。请确认已退出官方 Mimo，并靠近相机后重试。"
                        }
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
            pictureWidth = raster.first,
            pictureHeight = raster.second,
            pictureReady = pictureReady,
            foundCameras = rows,
            chrome = MonitorChromeLabels.from(status),
            pocketState = pocketStateFor(phase, pictureReady, recovery),
        )
    }

    private data class PictureState(val vertical: Boolean, val ready: Boolean, val raster: Pair<Int, Int>)

    private data class SessionState(
        val picture: PictureState,
        val phase: DjiPhase,
        val found: List<FoundCamera>,
        val chrome: CameraStatus,
        val recovery: SessionRecoveryUi,
    )

    companion object {
        private const val TAG = "PocketGlance"
        private const val DISCONNECT_QUIET_MS = 900L
        private const val MAX_WARM_RETRIES = 3
        private const val WARM_RETRY_BASE_MS = 1200L
    }
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
