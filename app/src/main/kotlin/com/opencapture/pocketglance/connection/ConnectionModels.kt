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

data class FoundCameraRow(
    val id: String,
    val title: String,
)

data class ConnectionState(
    val mode: ConnectionMode? = null,
    val phase: ConnectionPhase = ConnectionPhase.Idle,
    val message: String = "",
    val cameraVertical: Boolean = false,
    val foundCameras: List<FoundCameraRow> = emptyList(),
)
