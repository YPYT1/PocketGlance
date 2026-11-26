package com.opencapture.pocketglance.connection

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.opencapture.opencine.dji.PocketCameraSession
import com.opencapture.opencine.dji.DjiAndroidMediaService
import com.opencapture.opencine.media.CameraMediaService
import com.opencapture.opencine.vocabulary.CameraStatus
import com.opencapture.opencine.vocabulary.CameraCommands
import com.opencapture.opencine.vocabulary.ConnectionPhase as DjiPhase
import com.opencapture.opencine.vocabulary.FoundCamera
import com.opencapture.opencine.vocabulary.FocusOverlay
import com.opencapture.opencine.vocabulary.SessionRecoveryUi
import com.opencapture.pocketglance.diagnostics.GlanceLog
import com.opencapture.pocketglance.session.GlanceSessionHost
import com.opencapture.pocketglance.session.SwiftCoreBootstrap
import com.opencapture.pocketglance.ui.MonitorChromeLabels
import com.opencapture.pocketglance.monitor.FrameSyncedRaster
import com.opencapture.pocketglance.monitor.PictureRotation
import com.opencapture.pocketglance.monitor.RasterState
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

    @Volatile private var feed: ActiveFeed? = null
    private val session: PocketCameraSession? get() = feed?.session
    private var observeJob: Job? = null
    private var trackingJob: Job? = null
    private var focusJob: Job? = null
    @Volatile private var pendingSurface: Surface? = null
    private var autoConnectAttempted = false
    private var reportedLiveCamera: String? = null
    private var warmRetryAttempt = 0
    private var warmRetryJob: Job? = null
    private var recoveryRetryJob: Job? = null
    private var monitorForeground = true
    private var cameraTarget = preferredCameraId
    val hasRetryTarget: Boolean get() = cameraTarget != null

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
        feed?.let { active ->
            val existing = active.session
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
                        existing.decoder.hasPicture.value,
                        existing.found.value,
                        existing.chromeStatus.value,
                        existing.recoveryState.value,
                        active.raster.state.value,
                        _state.value.cameraPoseRotation,
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
        cameraTarget = camera.id
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
        noteDisconnect()
        warmRetryAttempt = 0
        _state.value = ConnectionState(mode = mode, phase = ConnectionPhase.Idle)
    }

    override fun attachSurface(surface: Surface?) {
        pendingSurface = surface
        session?.let { current ->
            if (surface == null) current.detachMonitorSurface()
            else {
                current.attachSurface(surface)
                if (monitorForeground) {
                    current.restoreMonitorPictureIfNeeded()
                    // Surface 一挂上立刻要 live，不等 MainActivity 700ms 轮询。
                    current.resumeLiveForMonitor("surface attached")
                }
            }
        }
    }

    fun setMonitorForeground(foreground: Boolean) {
        if (monitorForeground == foreground) return
        monitorForeground = foreground
        val current = session ?: return
        if (foreground) {
            current.noteMonitorReturned()
            if (pendingSurface?.isValid == true) current.restoreMonitorPictureIfNeeded()
            current.resumeLiveForMonitor("app foreground")
            if (_state.value.phase == ConnectionPhase.Failed) {
                cameraTarget?.let(::scheduleWarmRetry)
            }
        }
    }

    /** 从相册回到监视：退出残留回放并重开 live。 */
    fun resumeLiveAfterGallery() {
        session?.resumeLiveForMonitor("gallery closed")
    }

    /** 监视页周期探测：静置后画面变旧则强制恢复。 */
    fun recoverStaleMonitorFeed() {
        if (!monitorForeground || _state.value.pocketState != PocketState.Monitoring) return
        session?.recoverStaleMonitorFeed()
    }

    /**
     * Debug only：本地丢掉视频 UDP，ACK 照常发 —— 复现「视频死、控制面活」卡顿。
     * 返回是否成功挂上注入门。
     */
    fun debugDropVideoPackets(durationMs: Long): Boolean {
        val current = session ?: return false
        if (_state.value.phase != ConnectionPhase.Live) return false
        val until = SystemClock.elapsedRealtime() + durationMs.coerceIn(1_000L, 30_000L)
        Log.i(TAG, "debug: drop video packets for ${until - SystemClock.elapsedRealtime()}ms (ACK continues)")
        // false → 丢包；窗口结束后放行并清掉门。
        current.debugVideoPacketAdmission = {
            val open = SystemClock.elapsedRealtime() >= until
            if (open) current.debugVideoPacketAdmission = null
            open
        }
        return true
    }

    override fun notePresented(sourceTimestampNs: Long) {
        val active = feed ?: return
        if (pendingSurface?.isValid != true) return
        active.raster.present(sourceTimestampNs)?.let { raster ->
            Log.d(TAG, "live raster presented ${raster.width}x${raster.height}")
        }
        active.session.decoder.notePresented(sourceTimestampNs)
    }

    fun retryRecovery() {
        val current = session ?: return
        if (current.recoveryState.value.requiresOperator()) current.retrySessionRecovery()
    }

    internal fun galleryService(): Pair<String, DjiAndroidMediaService>? {
        val current = session ?: return null
        if (_state.value.pocketState != PocketState.Monitoring || current.status.value.isRecording || focusJob?.isActive == true) return null
        val id = current.connectedCamera?.id ?: return null
        val service = current.services.get<CameraMediaService>() as? DjiAndroidMediaService ?: return null
        return id to service
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
        val since = lastDisconnectAtMs
        val elapsed = SystemClock.elapsedRealtime() - since
        val remaining = DISCONNECT_QUIET_MS - elapsed
        if (since > 0L && remaining > 0L) {
            Log.i(TAG, "quiet ${remaining}ms after disconnect before rescan")
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
                        warmId != null && warmRetryAttempt > 0 -> "正在重新连接 Pocket…"
                        warmId != null -> "正在快速重连上次的 Pocket…"
                        else -> "正在扫描 Pocket…"
                    },
                pocketState = PocketState.Discovering,
            )
        val host = GlanceSessionHost(appContext)
        val created = PocketCameraSession(appContext, host)
        val raster = FrameSyncedRaster(created.decoder.pictureWidth, created.decoder.pictureHeight, System.nanoTime())
        created.decoder.onPictureSizeObserved = { width, height ->
            // Decoder output buffers use System.nanoTime() too; a queued frame from the old codec predates this report.
            raster.observe(width, height, System.nanoTime())
        }
        feed = ActiveFeed(created, raster)
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
                    combine(created.decoder.hasPicture, raster.state, host.pictureRotation, ::PictureState),
                    created.phaseFlow, created.found, created.chromeStatus,
                    created.recoveryState,
                ) { picture, phase, found, chrome, recovery ->
                    SessionState(picture, phase, found, chrome, recovery)
                }.collectLatest { (picture, phase, found, chrome, recovery) ->
                    val next = mapState(phase, picture.ready, found, chrome, recovery, picture.raster, picture.rotation)
                    if (_state.value.pocketState != next.pocketState) {
                        Log.d(TAG, "camera phase=$phase state=${next.pocketState}")
                        GlanceLog.i("wireless", "phase=$phase state=${next.pocketState} ready=${picture.ready}")
                    }
                    val prevChrome = _state.value.chrome
                    if (prevChrome.shootingModeLabel != next.chrome.shootingModeLabel ||
                        prevChrome.formatChip != next.chrome.formatChip
                    ) {
                        GlanceLog.i(
                            "chrome",
                            "mode=${next.chrome.shootingModeLabel} format=${next.chrome.formatChip} " +
                                "rawMode=0x%02X".format(chrome.shootingMode and 0xFF),
                        )
                    }
                    val prevLive = _state.value
                    if (next.pictureReady &&
                        (prevLive.sourceWidth != next.sourceWidth || prevLive.sourceHeight != next.sourceHeight)
                    ) {
                        GlanceLog.i(
                            "live",
                            "stream ${next.sourceWidth}x${next.sourceHeight} " +
                                "(record chip=${next.chrome.formatChip})",
                        )
                    }
                    _state.value = next
                    if (recovery is SessionRecoveryUi.WaitingForOperator || recovery is SessionRecoveryUi.PausedAfterDrops) {
                        scheduleRecoveryRetry(created)
                    } else {
                        recoveryRetryJob?.cancel()
                        recoveryRetryJob = null
                    }
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
                            cameraTarget = id
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
                        cameraTarget = found.first().id
                        created.connect(found.first())
                    }
                    if (phase == DjiPhase.FAILED) {
                        // GATT 撞窗失败后立刻扫会再撞；记下时刻，暖重连前强制再等静默期。
                        noteDisconnect()
                        (cameraTarget ?: warmId)?.let(::scheduleWarmRetry)
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
        // 最多 3 次，避免失败后叠太勤拖慢「给人点重试」的时机。
        if (warmRetryAttempt >= 3) {
            Log.i(TAG, "warm reconnect exhausted after $warmRetryAttempt tries — wait for operator")
            return
        }
        warmRetryAttempt += 1
        val backoffMs = (WARM_RETRY_BASE_MS shl (warmRetryAttempt - 1)).coerceAtMost(12_000)
        val quietLeft =
            if (lastDisconnectAtMs > 0L) {
                (DISCONNECT_QUIET_MS - (SystemClock.elapsedRealtime() - lastDisconnectAtMs))
                    .coerceAtLeast(0L)
            } else {
                0L
            }
        // 退避与断开静默取较大值，避免 GATT 失败后 0.5s 内再撞窗。
        val waitMs = maxOf(backoffMs, quietLeft)
        Log.i(TAG, "warm reconnect retry $warmRetryAttempt in ${waitMs}ms (quietLeft=${quietLeft}ms)")
        warmRetryJob =
            scope.launch {
                _state.value =
                    _state.value.copy(
                        phase = ConnectionPhase.Connecting,
                        message = "连接未完成，${(waitMs + 999) / 1000} 秒后自动重试…",
                        pocketState = PocketState.Discovering,
                    )
                delay(waitMs)
                awaitDisconnectQuietPeriod()
                warmRetryJob = null
                beginSession(warmId, resetWarmRetries = false)
            }
    }

    private fun scheduleRecoveryRetry(current: PocketCameraSession) {
        if (recoveryRetryJob?.isActive == true) return
        recoveryRetryJob = scope.launch {
            delay(20_000)
            recoveryRetryJob = null
            if (session === current && (current.recoveryState.value is SessionRecoveryUi.WaitingForOperator ||
                    current.recoveryState.value is SessionRecoveryUi.PausedAfterDrops)) {
                current.retrySessionRecovery()
            }
        }
    }

    private fun disconnectSession() {
        recoveryRetryJob?.cancel()
        recoveryRetryJob = null
        warmRetryJob?.cancel()
        warmRetryJob = null
        observeJob?.cancel()
        observeJob = null
        trackingJob?.cancel()
        trackingJob = null
        focusJob?.cancel()
        focusJob = null
        _focus.value = MonitorFocus()
        val closing = feed
        feed = null
        closing?.session?.close()
        autoConnectAttempted = false
        reportedLiveCamera = null
    }

    private fun mapState(
        phase: DjiPhase,
        pictureReady: Boolean,
        found: List<FoundCamera>,
        status: CameraStatus,
        recovery: SessionRecoveryUi,
        raster: RasterState,
        rotation: PictureRotation,
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
                        if (cameraTarget != null) {
                            "连接未完成，即将自动重试…"
                        } else {
                            "连接失败。请确认已退出官方 Mimo，并靠近相机后重试。"
                        }
                DjiPhase.LIVE -> ConnectionPhase.Live to when (recovery) {
                    is SessionRecoveryUi.Retrying -> "正在恢复连接（${recovery.attempt}/${recovery.maxAttempts}）…"
                    is SessionRecoveryUi.TakenOver -> "相机已被另一台设备连接。"
                    is SessionRecoveryUi.PausedAfterDrops -> "连接反复中断，稍后会自动重连。"
                    is SessionRecoveryUi.WaitingForOperator -> "暂未恢复，稍后会自动重连。"
                    SessionRecoveryUi.Idle -> "无线已连接"
                }
            }
        return ConnectionState(
            mode = mode,
            phase = glancePhase,
            message = message,
            cameraVertical = raster.display.width < raster.display.height,
            cameraPoseRotation = rotation,
            pictureWidth = raster.display.width,
            pictureHeight = raster.display.height,
            sourceWidth = raster.source.width,
            sourceHeight = raster.source.height,
            pictureReady = pictureReady,
            foundCameras = rows,
            chrome = MonitorChromeLabels.from(status),
            pocketState = pocketStateFor(phase, pictureReady, recovery),
        )
    }

    private data class ActiveFeed(val session: PocketCameraSession, val raster: FrameSyncedRaster)

    private data class PictureState(val ready: Boolean, val raster: RasterState, val rotation: PictureRotation)

    private data class SessionState(
        val picture: PictureState,
        val phase: DjiPhase,
        val found: List<FoundCamera>,
        val chrome: CameraStatus,
        val recovery: SessionRecoveryUi,
    )

    companion object {
        private const val TAG = "PocketGlance"
        /** 断开/GATT 失败后静默，避免立刻重连撞 BLE 释放窗。 */
        private const val DISCONNECT_QUIET_MS = 2_000L
        private const val WARM_RETRY_BASE_MS = 2_000L

        /**
         * GlanceModel 每次点「无线」都会 new 一个 Transport；静默期必须跨实例，
         * 否则断开后立刻重连会跳过等待、白撞一次 GATT（实机约浪费 4–5s）。
         */
        @Volatile private var lastDisconnectAtMs = 0L

        private fun noteDisconnect() {
            lastDisconnectAtMs = SystemClock.elapsedRealtime()
        }
    }
}

internal fun pocketStateFor(phase: DjiPhase, pictureReady: Boolean, recovery: SessionRecoveryUi): PocketState {
    if (phase == DjiPhase.LIVE && recovery is SessionRecoveryUi.TakenOver) return PocketState.TakenOver
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
