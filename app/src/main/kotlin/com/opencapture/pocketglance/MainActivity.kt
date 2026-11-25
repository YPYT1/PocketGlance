package com.opencapture.pocketglance

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.opencapture.opencine.appkit.DiscoveryPermissions
import com.opencapture.opencine.appkit.rememberPermissionGate
import com.opencapture.opencine.media.FeedEffectsRenderPlan
import com.opencapture.pocketglance.connection.ConnectionMode
import com.opencapture.pocketglance.connection.ConnectionPhase
import com.opencapture.pocketglance.connection.FocusGesture
import com.opencapture.pocketglance.connection.WiredUsbTransport
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
import com.opencapture.pocketglance.ui.MonitorScreen
import com.opencapture.pocketglance.ui.PreviewLutDialog
import com.opencapture.pocketglance.ui.SettingsScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val model: GlanceModel get() = (application as GlanceApp).model
    private var openRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleUsbIntent(intent)
        setContent {
            GlanceTheme {
                GlanceRoot(model, openRequest)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbIntent(intent)
        openRequest++
    }

    private fun handleUsbIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device = intent.usbParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device != null && WiredUsbTransport.isPocketDevice(device)) {
                    model.connectWired(device = device)
                }
            }
            UsbManager.ACTION_USB_ACCESSORY_ATTACHED -> {
                val accessory = intent.usbParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
                if (accessory != null && WiredUsbTransport.isPocketAccessory(accessory)) {
                    model.connectWired(accessory = accessory)
                }
            }
        }
    }

    override fun onStop() {
        model.setMonitorForeground(false)
        super.onStop()
    }
}

@Suppress("DEPRECATION")
private inline fun <reified T : Any> Intent.usbParcelableExtra(key: String): T? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, T::class.java)
    } else {
        getParcelableExtra(key) as? T
    }

private object Routes {
    const val Home = "home"
    const val Monitor = "monitor"
    const val Gallery = "gallery"
    const val Settings = "settings"
}

@Composable
private fun GlanceRoot(model: GlanceModel, openRequest: Int) {
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
    fun returnToMonitor() {
        showLutPicker = false
        if (nav.currentDestination?.route == Routes.Monitor) return
        if (nav.popBackStack(Routes.Monitor, inclusive = false)) return
        nav.navigate(Routes.Monitor) {
            popUpTo(Routes.Home)
            launchSingleTop = true
        }
    }

    LaunchedEffect(sessionLive, foreground, openRequest) {
        if (foreground && sessionLive && nav.currentDestination?.route != Routes.Monitor) {
            returnToMonitor()
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
        if (showFeed) {
            LiveFeedHost(
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
                    startup = startup,
                    permissionHint = permissionHint,
                    onConnectWireless = { startWireless() },
                    onConnectWired = { model.connect(ConnectionMode.Wired) },
                    onPickCamera = model::connectToFoundCamera,
                    onDisconnect = model::disconnect,
                    onRetryRecovery = model::retryRecovery,
                    onOpenMonitor = { returnToMonitor() },
                    onOpenSettings = { nav.navigate(Routes.Settings) },
                    onRequestPermission = {
                        pendingWireless = true
                        discovery.request()
                    },
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
                BackHandler(enabled = sessionLive && route == Routes.Gallery) { returnToMonitor() }
                val gallery by model.gallery.state.collectAsState()
                val galleryTags by model.galleryTags.collectAsState()
                DisposableEffect(model) {
                    model.gallery.open()
                    onDispose { model.gallery.close() }
                }
                GalleryScreen(
                    state = gallery,
                    tags = galleryTags,
                    onUpdateTag = model::updateGalleryTag,
                    onDelete = model.gallery::delete,
                    onRefresh = model.gallery::refresh,
                    onLoadMore = model.gallery::loadMore,
                    onBack = { if (sessionLive) returnToMonitor() else nav.popBackStack(Routes.Home, inclusive = false) },
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
                )
            }
        }
        if (lutPickerVisible) {
            PreviewLutDialog(previewLuts, previewAvailable, connection.chrome.colorChip,
                { importLuts.launch(arrayOf("*/*")) }, model::selectPreviewLut) {
                returnToMonitor()
            }
        }
    }
}
