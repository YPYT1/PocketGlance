package com.opencapture.pocketglance.connection

enum class ConnectionMode {
    Wireless,
    Wired,
}

/** What the next cold start should attempt. */
enum class StartupPreference {
    Wireless,
    Wired,
    Last,
}

enum class ConnectionPhase {
    Idle,
    Connecting,
    Live,
    Failed,
}

enum class PocketState(val label: String) {
    Unknown("未知"),
    Discovering("寻找中"),
    ConnectingBluetooth("连接蓝牙"),
    Waking("唤醒中"),
    Pairing("唤醒／配对中"),
    JoiningWifi("加入相机 Wi‑Fi"),
    OpeningLink("打开监视通道"),
    Awake("已唤醒"),
    Monitoring("监视中"),
    Recovering("恢复连接中"),
    NeedsRetry("等待手动重试"),
    Failed("连接失败"),
}

data class FoundCameraRow(
    val id: String,
    val title: String,
)

/**
 * Live monitor chrome from OpenCine CameraStatus.
 * Defaults when wired or not yet reporting.
 */
data class MonitorChrome(
    val shootingModeLabel: String = "—",
    val formatChip: String = "—",
    val colorChip: String = "—",
    val isRecording: Boolean = false,
    val recordElapsedSec: Int = 0,
    val recordRemainingSec: Int = 0,
)

data class ConnectionState(
    val mode: ConnectionMode? = null,
    val phase: ConnectionPhase = ConnectionPhase.Idle,
    val message: String = "",
    val cameraVertical: Boolean = false,
    val pictureReady: Boolean = false,
    val pocketState: PocketState = PocketState.Unknown,
    val foundCameras: List<FoundCameraRow> = emptyList(),
    val chrome: MonitorChrome = MonitorChrome(),
)
