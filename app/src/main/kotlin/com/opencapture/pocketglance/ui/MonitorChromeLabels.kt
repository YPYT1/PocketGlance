package com.opencapture.pocketglance.ui

import com.opencapture.opencine.vocabulary.CameraCommands
import com.opencapture.opencine.vocabulary.CameraStatus
import com.opencapture.pocketglance.connection.MonitorChrome

/** Map OpenCine status → Chinese HUD chips for Pocket 监视器. */
object MonitorChromeLabels {
    fun from(status: CameraStatus): MonitorChrome =
        MonitorChrome(
            shootingModeLabel = shootingModeZh(status.shootingMode),
            formatChip = status.recFormatLabel.ifBlank { "—" },
            colorChip = colorZh(status.colorMode),
            isRecording = status.isRecording,
            recordElapsedSec = status.recordElapsedSec.coerceAtLeast(0),
            recordRemainingSec = status.recordRemainingSec.coerceAtLeast(0),
        )

    fun shootingModeZh(raw: Int): String =
        when (raw) {
            CameraCommands.SHOOT_PHOTO, CameraCommands.SHOOT_PHOTO_POCKET4 -> "拍照"
            CameraCommands.SHOOT_LIVE_PHOTO -> "动图"
            CameraCommands.SHOOT_VIDEO -> "视频"
            CameraCommands.SHOOT_SUPER_NIGHT -> "低光"
            CameraCommands.SHOOT_SLOWMO -> "慢动作"
            CameraCommands.SHOOT_TIMELAPSE -> "延时"
            CameraCommands.SHOOT_HYPERLAPSE -> "运动延时"
            -1 -> "—"
            else -> CameraCommands.shootingModeLabel(raw) ?: "—"
        }

    fun colorZh(mode: Int): String =
        when (mode) {
            -1 -> "—"
            CameraCommands.COLOR_NORMAL, CameraCommands.COLOR_NORMAL10 -> "Normal"
            CameraCommands.COLOR_HDR -> "HDR"
            CameraCommands.COLOR_DLOG -> "D-Log"
            CameraCommands.COLOR_DLOG2 -> "D-Log2"
            CameraCommands.COLOR_DLOG_M -> "D-Log M"
            CameraCommands.COLOR_DOLBY_VISION -> "Dolby"
            else -> CameraCommands.colorLabel(mode).ifBlank { "—" }
        }

    fun formatElapsed(sec: Int): String {
        val s = sec.coerceAtLeast(0)
        val m = s / 60
        val r = s % 60
        return "%d:%02d".format(m, r)
    }

    fun formatRemaining(sec: Int): String? =
        if (sec > 0) "剩 ${formatElapsed(sec)}" else null
}
