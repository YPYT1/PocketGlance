package com.opencapture.pocketglance.ui

import com.opencapture.opencine.vocabulary.CameraCommands
import com.opencapture.opencine.vocabulary.CameraStatus
import com.opencapture.pocketglance.connection.MonitorChrome

/** Map OpenCine status → Chinese HUD chips for Pocket 监视器. */
object MonitorChromeLabels {
    fun from(status: CameraStatus): MonitorChrome =
        MonitorChrome(
            shootingModeLabel = if (status.isPhoto) "拍照" else shootingModeZh(status.shootingMode),
            formatChip = formatChip(status),
            colorChip = if (status.isPhoto) "—" else colorZh(status.colorMode),
            logPreviewLabel = when (status.monitorColorMode) {
                CameraCommands.COLOR_DLOG, CameraCommands.COLOR_DLOG2, CameraCommands.COLOR_DLOG_M -> "机身预览还原状态未读回"
                else -> null
            },
            isRecording = !status.isPhoto && status.isRecording,
            recordElapsedSec = status.recordElapsedSec.coerceAtLeast(0),
            recordRemainingSec = status.recordRemainingSec.coerceAtLeast(0),
            isPhoto = status.isPhoto,
            photoTimerRemainingSeconds = if (status.isPhoto) status.photoTimerRemainingSeconds.coerceAtLeast(0) else 0,
            photoCaptureCount = status.photoCaptureCount.coerceAtLeast(0),
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

    fun formatPhotoTimer(ms: Int?): String =
        when (ms) {
            null -> "倒计时 —"
            0 -> "倒计时关"
            else -> "倒计时 ${ms / 1000}${if (ms % 1000 == 0) "" else ".${(ms % 1000).toString().padStart(3, '0').trimEnd('0')}"} 秒"
        }

    private fun photoTimerDelay(status: CameraStatus): Int? =
        status.photoTimerDelayMs.takeIf { it >= 0 }
            ?: status.photoTimerSeconds.takeIf { it >= 0 }
                ?.let { it * 1000 + status.photoTimerMillis.coerceAtLeast(0) }

    private fun formatChip(status: CameraStatus): String {
        if (!status.isPhoto) return status.recFormatLabel.ifBlank { "—" }
        val size =
            when (status.photoSizeMode) {
                0 -> "标准"
                4 -> "超清"
                else -> "画质 —"
            }
        val aspect =
            when (status.photoAspect) {
                1 -> "16:9"
                2 -> "4:3"
                3 -> "1:1"
                else -> "比例 —"
            }
        val motion =
            if (status.shootingMode == CameraCommands.SHOOT_LIVE_PHOTO) "动图开" else "动图关"
        return listOf(size, aspect, formatPhotoTimer(photoTimerDelay(status)), motion).joinToString(" · ")
    }
}
