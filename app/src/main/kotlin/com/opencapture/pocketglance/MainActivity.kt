package com.opencapture.pocketglance

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.opencapture.pocketglance.monitor.MonitorLayout
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
    private val model: GlanceModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlanceTheme {
                GlanceRoot(model)
            }
        }
    }
}

private object Routes {
    const val Home = "home"
    const val Monitor = "monitor"
    const val Gallery = "gallery"
    const val Settings = "settings"
}

@Composable
private fun GlanceRoot(model: GlanceModel) {
    val nav = rememberNavController()
    val connection by model.connection.collectAsState()
    val previewLuts by model.previewLuts.collectAsState()
    val startup by model.startupPreference.collectAsState()
    val filter by model.galleryFilter.collectAsState()
    val items by model.filteredGallery.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val tablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
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
    var didAutoOpenMonitor by remember { mutableStateOf(false) }

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

    LaunchedEffect(connection.phase, tablet) {
        activity?.requestedOrientation = when {
            connection.phase == ConnectionPhase.Live -> MonitorLayout.SENSOR_ORIENTATION
            tablet -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER
            else -> MonitorLayout.HOME_ORIENTATION
        }
        when (connection.phase) {
            ConnectionPhase.Live -> {
                if (!didAutoOpenMonitor) {
                    didAutoOpenMonitor = true
                    if (route != Routes.Monitor) {
                        nav.navigate(Routes.Monitor) { launchSingleTop = true }
                    }
                }
            }
            ConnectionPhase.Idle, ConnectionPhase.Failed -> {
                didAutoOpenMonitor = false
            }
            else -> Unit
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
    val usePreviewLut = showFeed && previewAvailable && !connection.chrome.isPhoto
    val activeLut = previewLuts.selected.takeIf { usePreviewLut }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showFeed) {
            LiveFeedHost(
                cameraVertical = connection.cameraVertical,
                pictureWidth = connection.pictureWidth,
                pictureHeight = connection.pictureHeight,
                renderPlan = if (usePreviewLut) previewLuts.plan ?: FeedEffectsRenderPlan.IDENTITY else FeedEffectsRenderPlan.IDENTITY,
                onPreviewAvailable = { previewAvailable = it },
                focus = connection.focus.takeIf { route == Routes.Monitor && !showLutPicker && !startingFeed && it.available },
                onFocus = model::focus,
                onAttachSurface = model::attachSurface,
                onFramePresented = model::notePresented,
            )
        }

        NavHost(navController = nav, startDestination = Routes.Home) {
            composable(Routes.Home) {
                HomeScreen(
                    connection = connection,
                    startup = startup,
                    permissionHint = permissionHint,
                    onConnectWireless = { startWireless() },
                    onConnectWired = { model.connect(ConnectionMode.Wired) },
                    onPickCamera = model::connectToFoundCamera,
                    onDisconnect = model::disconnect,
                    onRetryRecovery = model::retryRecovery,
                    onOpenMonitor = { nav.navigate(Routes.Monitor) { launchSingleTop = true } },
                    onOpenSettings = { nav.navigate(Routes.Settings) },
                    onRequestPermission = {
                        pendingWireless = true
                        discovery.request()
                    },
                )
            }
            composable(Routes.Monitor) {
                MonitorScreen(
                    connection = connection,
                    startingFeed = startingFeed,
                    onUnlockFocus = { model.focus(FocusGesture.Unlock, 0f, 0f) },
                    onStopTracking = { model.focus(FocusGesture.StopTracking, 0f, 0f) },
                    activeLut = activeLut?.name,
                    raster = if (connection.pictureReady) "${connection.pictureWidth}×${connection.pictureHeight}" else null,
                    onOpenLuts = { showLutPicker = true },
                    onGallery = { nav.navigate(Routes.Gallery) },
                    onSettings = { nav.navigate(Routes.Settings) },
                    onRetryRecovery = model::retryRecovery,
                    onHome = { nav.popBackStack(Routes.Home, inclusive = false) },
                )
            }
            composable(Routes.Gallery) {
                GalleryScreen(
                    items = items,
                    filter = filter,
                    onFilter = model::setGalleryFilter,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    startup = startup,
                    onStartup = model::setStartupPreference,
                    onBack = { nav.popBackStack() },
                )
            }
        }
        if (showLutPicker && route == Routes.Monitor && !connection.chrome.isPhoto) {
            PreviewLutDialog(previewLuts, previewAvailable, { importLuts.launch(arrayOf("*/*")) }, model::selectPreviewLut) {
                showLutPicker = false
            }
        }
    }
}
