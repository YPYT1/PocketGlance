package com.opencapture.pocketglance

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.isActive
import com.opencapture.opencine.appkit.DiscoveryPermissions
import com.opencapture.opencine.appkit.rememberPermissionGate
import com.opencapture.opencine.media.FeedEffectsRenderPlan
import com.opencapture.pocketglance.connection.ConnectionMode
import com.opencapture.pocketglance.connection.ConnectionPhase
import com.opencapture.pocketglance.connection.FocusGesture
import com.opencapture.pocketglance.diagnostics.GlanceLog
import com.opencapture.pocketglance.monitor.MonitorLayout
import com.opencapture.pocketglance.monitor.MonitorOrientation
import com.opencapture.pocketglance.monitor.PictureRotation
import com.opencapture.pocketglance.monitor.fullScreenPortraitFallback
import com.opencapture.pocketglance.monitor.rememberMonitorDisplay
import com.opencapture.pocketglance.ui.GalleryScreen
import com.opencapture.pocketglance.ui.GlanceTheme
import com.opencapture.pocketglance.ui.HomeScreen
import com.opencapture.pocketglance.ui.LiveFeedHost
import com.opencapture.pocketglance.ui.LivePerformanceGate
import com.opencapture.pocketglance.ui.LogViewerScreen
import com.opencapture.pocketglance.ui.MonitorScreen
import com.opencapture.pocketglance.ui.PreviewLutDialog
import com.opencapture.pocketglance.ui.SettingsScreen
import com.opencapture.pocketglance.ui.UvcFeedHost
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val model: GlanceModel get() = (application as GlanceApp).model
    private var openRequest by mutableIntStateOf(0)

    private var wiredCameraPermissionHint by mutableStateOf<String?>(null)
    private var canRetryCameraPermission = true
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                wiredCameraPermissionHint = null
                model.connectWired()
                canRetryCameraPermission = true
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                wiredCameraPermissionHint = "外接 UVC 预览需要相机权限，只用来显示 Pocket 画面。再次点有线连接即可授权。"
            } else {
                canRetryCameraPermission = false
                wiredCameraPermissionHint = "相机权限已关闭。有线 UVC 预览无法打开；可在系统设置重新允许后再连接。"
            }
        }

    /** 有线 UVC 预览需要 CAMERA 权限；统一走权限门后再 connect。 */
    private fun connectWired() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            model.connectWired()
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            wiredCameraPermissionHint = "外接 UVC 预览需要相机权限，只用来显示 Pocket 画面。再次点有线连接即可授权。"
        } else if (canRetryCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            wiredCameraPermissionHint = "相机权限已关闭。有线 UVC 预览无法打开；可在系统设置重新允许后再连接。"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlanceTheme {
                GlanceRoot(
                    model = model,
                    openRequest = openRequest,
                    wiredCameraPermissionHint = wiredCameraPermissionHint,
                    onConnectWired = ::connectWired,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        model.setMonitorForeground(true)
    }

    override fun onResume() {
        super.onResume()
        openRequest++
    }

    override fun onStop() {
        model.setMonitorForeground(false)
        super.onStop()
    }
}

private object Routes {
    const val Home = "home"
    const val Monitor = "monitor"
    const val Gallery = "gallery"
    const val Settings = "settings"
    const val Logs = "logs"
}

@Composable
private fun GlanceRoot(
    model: GlanceModel,
    openRequest: Int,
    wiredCameraPermissionHint: String?,
    onConnectWired: () -> Unit,
) {
    val nav = rememberNavController()
    val connection by model.connection.collectAsState()
    val foreground by model.monitorForeground.collectAsState()
    val previewLuts by model.previewLuts.collectAsState()
    val startup by model.startupPreference.collectAsState()
    val monitorOrientation by model.monitorOrientation.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val tablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var rotationOverride by rememberSaveable { mutableStateOf<PictureRotation?>(null) }
    val automaticRotation = MonitorLayout.autoPictureRotation(connection.cameraVertical, connection.cameraPoseRotation)
    val pictureRotation = rotationOverride ?: automaticRotation
    val picturePortrait = MonitorLayout.isPortraitPicture(
        connection.pictureWidth.toFloat() / connection.pictureHeight.coerceAtLeast(1), pictureRotation,
    )
    val display = rememberMonitorDisplay()
    val wantsPortrait = connection.phase == ConnectionPhase.Live && route == Routes.Monitor &&
        monitorOrientation == MonitorOrientation.AutoRotate && picturePortrait
    var portraitFallback by remember(wantsPortrait) { mutableStateOf(false) }
    LaunchedEffect(wantsPortrait, display.landscape, portraitFallback) {
        if (wantsPortrait && display.landscape && !portraitFallback) {
            delay(550)
            portraitFallback = true
        }
    }
    val usePortraitFallback = wantsPortrait && display.landscape && portraitFallback
    var showLutPicker by rememberSaveable { mutableStateOf(false) }
    var exportNote by remember { mutableStateOf<String?>(null) }
    val computerHint = remember(context) { GlanceLog.computerAccessHint(context) }
    fun exportDiagnostics() {
        runCatching {
            val file = GlanceLog.exportBundle(context)
            context.startActivity(
                Intent.createChooser(GlanceLog.shareIntent(context, file), "分享诊断日志"),
            )
            exportNote = "已导出 ${file.name}。设备连电脑后也可用 adb 拉取外部 logs 目录。"
        }.onFailure {
            GlanceLog.e("export", "share failed", it)
            exportNote = "导出失败：${it.message}"
        }
    }
    fun clearDiagnostics() {
        runCatching {
            GlanceLog.clear(context)
            exportNote = "日志已清除"
        }.onFailure {
            GlanceLog.e("log", "clear failed", it)
            exportNote = "清除失败：${it.message}"
        }
    }
    fun syncDiagnosticsForComputer() {
        runCatching {
            GlanceLog.flush()
            GlanceLog.mirrorToExternal(context, force = true)
            val path = GlanceLog.externalPersistentFile(context)?.absolutePath
            exportNote =
                if (path != null) "已同步到可拉取目录：\n$path"
                else "同步失败：设备无外部存储目录"
        }.onFailure {
            GlanceLog.e("export", "mirror failed", it)
            exportNote = "同步失败：${it.message}"
        }
    }
    fun openLogViewer() {
        nav.navigate(Routes.Logs) { launchSingleTop = true }
    }
    val importLuts = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), model::importPreviewLuts)
    val discovery =
        rememberPermissionGate(
            label = "发现相机",
            needed = DiscoveryPermissions.discovery(),
            preferencesName = "pocketglance.permissions",
            log = { },
        )

    var pendingWireless by remember { mutableStateOf(false) }

    val enableBluetooth =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (discovery.granted) {
                model.connect(ConnectionMode.Wireless)
            }
        }

    fun bluetoothOn(): Boolean {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        return adapter?.isEnabled == true
    }

    fun startWireless() {
        if (!discovery.granted) {
            pendingWireless = true
            discovery.request()
            return
        }
        if (!bluetoothOn()) {
            runCatching {
                enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }.onFailure {
                runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            }
            return
        }
        model.connect(ConnectionMode.Wireless)
    }

    LaunchedEffect(discovery.granted, pendingWireless) {
        if (discovery.granted && pendingWireless) {
            pendingWireless = false
            if (bluetoothOn()) {
                model.connect(ConnectionMode.Wireless)
            } else {
                runCatching {
                    enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
            }
        }
    }

    LaunchedEffect(connection.phase, route, tablet, picturePortrait, monitorOrientation, usePortraitFallback) {
        activity?.requestedOrientation = when {
            usePortraitFallback -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            connection.phase == ConnectionPhase.Live && route == Routes.Monitor ->
                MonitorLayout.monitorOrientation(monitorOrientation, picturePortrait)
            connection.phase == ConnectionPhase.Live -> MonitorLayout.SENSOR_ORIENTATION
            tablet -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER
            else -> MonitorLayout.HOME_ORIENTATION
        }
    }

    val sessionLive = connection.phase == ConnectionPhase.Live
    var leavingGallery by remember { mutableStateOf(false) }
    fun returnToMonitor() {
        showLutPicker = false
        if (nav.currentDestination?.route == Routes.Monitor) return
        if (nav.popBackStack(Routes.Monitor, inclusive = false)) return
        nav.navigate(Routes.Monitor) {
            popUpTo(Routes.Home)
            launchSingleTop = true
        }
    }
    fun leaveGalleryToMonitor() {
        if (leavingGallery) return
        leavingGallery = true
        model.leaveGallery {
            // Keep leavingGallery true through pop so Gallery onDispose does not
            // race a second gallery.close() before browse return finishes.
            returnToMonitor()
            leavingGallery = false
        }
    }

    LaunchedEffect(sessionLive, foreground, openRequest) {
        if (foreground && sessionLive && nav.currentDestination?.route != Routes.Monitor) {
            if (nav.currentDestination?.route == Routes.Gallery) leaveGalleryToMonitor()
            else returnToMonitor()
        }
    }
    LaunchedEffect(sessionLive, route, foreground) {
        if (!sessionLive || route != Routes.Monitor || !foreground) return@LaunchedEffect
        while (isActive) {
            delay(2_000)
            model.recoverStaleMonitorFeed()
        }
    }
    LaunchedEffect(connection.phase, route) {
        if (connection.phase == ConnectionPhase.Idle || connection.phase == ConnectionPhase.Failed) {
            rotationOverride = null
        }
        if (connection.phase == ConnectionPhase.Idle && (route == Routes.Monitor || route == Routes.Gallery)) {
            showLutPicker = false
            nav.popBackStack(Routes.Home, inclusive = false)
        }
    }

    LivePerformanceGate(
        live = connection.phase == ConnectionPhase.Live,
        immersive = connection.phase == ConnectionPhase.Live && route == Routes.Monitor,
    )

    val permissionHint =
        when {
            !discovery.granted && pendingWireless ->
                "无线连接需要「附近的设备」权限，用于扫描 Pocket。"
            !discovery.granted ->
                "尚未授予附近设备权限。点无线连接时会弹出申请。"
            else -> null
        }

    val showFeed = connection.phase == ConnectionPhase.Live
    // Wired Camera2 output is mounted only after permission and external-camera discovery succeed.
    val wiredUvc = connection.mode == ConnectionMode.Wired && connection.phase == ConnectionPhase.Live
    var startingFeed by remember(showFeed) { mutableStateOf(true) }
    LaunchedEffect(showFeed, connection.pictureReady) {
        if (showFeed && connection.pictureReady) {
            delay(3000)
            startingFeed = false
        }
    }
    var previewAvailable by remember(showFeed) { mutableStateOf(true) }
    val canOpenLut = showFeed && connection.chrome.supportsPreviewLut
    val lutPickerVisible = showLutPicker && canOpenLut && route == Routes.Monitor
    LaunchedEffect(canOpenLut) {
        if (!canOpenLut) showLutPicker = false
    }
    val usePreviewLut = canOpenLut && previewAvailable
    val activeLut = previewLuts.selected.takeIf { usePreviewLut }

    Box(modifier = Modifier.fillMaxSize().fullScreenPortraitFallback(if (usePortraitFallback) display.portraitFallbackDegrees else 0f)) {
        when {
            wiredUvc -> UvcFeedHost(
                pictureWidth = connection.pictureWidth,
                pictureHeight = connection.pictureHeight,
                foreground = foreground,
                onAttachSurface = model::attachSurface,
                onFramePresented = model::notePresented,
            )
            showFeed -> LiveFeedHost(
                rotation = pictureRotation,
                blackSides = picturePortrait,
                foreground = foreground,
                pictureWidth = connection.pictureWidth,
                pictureHeight = connection.pictureHeight,
                sourceWidth = connection.sourceWidth,
                sourceHeight = connection.sourceHeight,
                renderPlan = if (usePreviewLut) previewLuts.plan ?: FeedEffectsRenderPlan.IDENTITY else FeedEffectsRenderPlan.IDENTITY,
                onPreviewAvailable = { previewAvailable = it },
                focus = connection.focus.takeIf { route == Routes.Monitor && !lutPickerVisible && !startingFeed && it.available },
                onFocus = model::focus,
                onAttachSurface = model::attachSurface,
                onFramePresented = model::notePresented,
            )
        }

        NavHost(navController = nav, startDestination = Routes.Home) {
            composable(Routes.Home) {
                BackHandler(enabled = sessionLive && route == Routes.Home) { returnToMonitor() }
                HomeScreen(
                    connection = connection,
                    permissionHint = permissionHint,
                    onConnectWireless = { startWireless() },
                    onConnectWired = onConnectWired,
                    wiredCameraPermissionHint = wiredCameraPermissionHint,
                    onPickCamera = model::connectToFoundCamera,
                    onDisconnect = model::disconnect,
                    onRetryRecovery = model::retryRecovery,
                    onOpenMonitor = { returnToMonitor() },
                    onOpenSettings = { nav.navigate(Routes.Settings) },
                    onRequestPermission = {
                        pendingWireless = true
                        discovery.request()
                    },
                    startup = startup,
                    onStartup = model::setStartupPreference,
                    monitorOrientation = monitorOrientation,
                    onMonitorOrientation = model::setMonitorOrientation,
                )
            }
            composable(Routes.Monitor) {
                BackHandler(enabled = sessionLive && route == Routes.Monitor && activity != null && !lutPickerVisible) {
                    activity?.moveTaskToBack(true)
                }
                MonitorScreen(
                    connection = connection,
                    startingFeed = startingFeed,
                    onUnlockFocus = { model.focus(FocusGesture.Unlock, 0f, 0f) },
                    onStopTracking = { model.focus(FocusGesture.StopTracking, 0f, 0f) },
                    activeLut = activeLut?.name,
                    onOpenLuts = { showLutPicker = true },
                    onGallery = { nav.navigate(Routes.Gallery) },
                    onSettings = { nav.navigate(Routes.Settings) { launchSingleTop = true } },
                    onRetryRecovery = model::retryRecovery,
                    onDisconnect = model::disconnect,
                    onHome = { nav.popBackStack(Routes.Home, inclusive = false) },
                )
            }
            composable(Routes.Gallery) {
                BackHandler(enabled = sessionLive && route == Routes.Gallery && !leavingGallery) {
                    leaveGalleryToMonitor()
                }
                val gallery by model.gallery.state.collectAsState()
                val galleryTags by model.galleryTags.collectAsState()
                DisposableEffect(model) {
                    model.gallery.open()
                    onDispose {
                        // 正常返回已走 leaveGallery（closeAndWait）；此处兜底取消会话。
                        if (!leavingGallery) model.gallery.close()
                    }
                }
                GalleryScreen(
                    state = gallery,
                    tags = galleryTags,
                    onUpdateTag = model::updateGalleryTag,
                    onDelete = model.gallery::delete,
                    onRefresh = model.gallery::refresh,
                    onLoadMore = model.gallery::loadMore,
                    onBack = {
                        when {
                            leavingGallery -> Unit
                            sessionLive -> leaveGalleryToMonitor()
                            else -> nav.popBackStack(Routes.Home, inclusive = false)
                        }
                    },
                )
            }
            composable(Routes.Settings) {
                BackHandler(enabled = sessionLive && route == Routes.Settings) { returnToMonitor() }
                SettingsScreen(
                    startup = startup,
                    onStartup = model::setStartupPreference,
                    monitorOrientation = monitorOrientation,
                    onMonitorOrientation = model::setMonitorOrientation,
                    backToMonitor = sessionLive,
                    onBack = { if (sessionLive) returnToMonitor() else nav.popBackStack(Routes.Home, inclusive = false) },
                    onViewLogs = ::openLogViewer,
                    onClearLogs = ::clearDiagnostics,
                    onExportLogs = ::exportDiagnostics,
                    onSyncLogsForComputer = ::syncDiagnosticsForComputer,
                    exportNote = exportNote,
                    computerHint = computerHint,
                )
            }
            composable(Routes.Logs) {
                val previous = nav.previousBackStackEntry?.destination?.route
                val backLabel =
                    when {
                        previous == Routes.Settings -> "返回设置"
                        sessionLive -> "返回监视界面"
                        else -> "返回连接首页"
                    }
                BackHandler(enabled = true) {
                    if (!nav.popBackStack()) nav.popBackStack(Routes.Home, inclusive = false)
                }
                LogViewerScreen(
                    backLabel = backLabel,
                    onBack = {
                        if (!nav.popBackStack()) {
                            if (sessionLive) returnToMonitor() else nav.popBackStack(Routes.Home, inclusive = false)
                        }
                    },
                )
            }
        }
        if (lutPickerVisible) {
            PreviewLutDialog(
                state = previewLuts,
                available = previewAvailable,
                colorLabel = connection.chrome.colorChip,
                onImport = { importLuts.launch(arrayOf("*/*")) },
                onSelect = model::selectPreviewLut,
                onToggleFavorite = model::toggleFavoriteLut,
                onClose = { returnToMonitor() },
            )
        }
    }
}
