package com.opencapture.pocketglance.ui

import com.opencapture.opencine.vocabulary.CameraCommands
import com.opencapture.opencine.vocabulary.CameraStatus
import com.opencapture.opencine.vocabulary.EvComp
import com.opencapture.opencine.vocabulary.VideoFormat
import com.opencapture.opencine.vocabulary.VideoFrameRate
import com.opencapture.opencine.vocabulary.VideoResolution
import com.opencapture.pocketglance.connection.MonitorChrome

/** Map OpenCine status → Chinese HUD chips for Pocket 监视器. */
object MonitorChromeLabels {
    fun from(status: CameraStatus): MonitorChrome {
        val mode = status.shootingMode
        val hideColor = status.isPhoto || hidesColorChip(mode)
        return MonitorChrome(
            shootingModeLabel = shootingModeZh(mode, status.isPhoto),
            formatChip = formatChip(status),
            colorChip = if (hideColor) "—" else colorZh(status.colorMode),
            isoChip = isoChip(status),
            evChip = EvComp.fromRaw(status.evComp)?.let { "EV ${it.label}" } ?: "—",
            wbChip = wbChip(status),
            supportsPreviewLut = !status.isPhoto &&
                !hidesColorChip(mode) &&
                (status.colorMode == CameraCommands.COLOR_DLOG || status.colorMode == CameraCommands.COLOR_DLOG2),
            isRecording = !status.isPhoto && status.isRecording,
            recordElapsedSec = status.recordElapsedSec.coerceAtLeast(0),
            isPhoto = status.isPhoto,
            photoTimerRemainingSeconds =
                if (status.isPhoto || mode == CameraCommands.SHOOT_PANO) {
                    status.photoTimerRemainingSeconds.coerceAtLeast(0)
                } else {
                    0
                },
        )
    }

    /** 低光／慢动作／静止·运动延时／全景无色彩档，不显示「普通」。视频等其它模式保留。 */
    fun hidesColorChip(shootingMode: Int): Boolean =
        shootingMode == CameraCommands.SHOOT_SUPER_NIGHT ||
            shootingMode == CameraCommands.SHOOT_SLOWMO ||
            shootingMode == CameraCommands.SHOOT_TIMELAPSE ||
            shootingMode == CameraCommands.SHOOT_HYPERLAPSE ||
            shootingMode == CameraCommands.SHOOT_PANO

    fun shootingModeZh(raw: Int, isPhoto: Boolean = CameraCommands.isPhotoMode(raw)): String =
        when {
            isPhoto && (raw == CameraCommands.SHOOT_LIVE_PHOTO) -> "动图"
            isPhoto -> "拍照"
            raw == CameraCommands.SHOOT_VIDEO -> "视频"
            raw == CameraCommands.SHOOT_SUPER_NIGHT -> "低光视频"
            raw == CameraCommands.SHOOT_SLOWMO -> "慢动作"
            raw == CameraCommands.SHOOT_TIMELAPSE -> "静止延时"
            raw == CameraCommands.SHOOT_MOTIONLAPSE -> "轨迹延时"
            raw == CameraCommands.SHOOT_HYPERLAPSE -> "运动延时"
            raw == CameraCommands.SHOOT_PANO -> "全景"
            raw < 0 -> "—"
            else -> CameraCommands.shootingModeLabel(raw) ?: "模式 0x%02X".format(raw and 0xFF)
        }

    fun colorZh(mode: Int): String =
        when (mode) {
            -1 -> "—"
            CameraCommands.COLOR_NORMAL, CameraCommands.COLOR_NORMAL10 -> "普通"
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

    fun formatPhotoTimer(ms: Int?): String =
        when (ms) {
            null -> "倒计时 —"
            0 -> "倒计时关"
            else -> "倒计时 ${ms / 1000}${if (ms % 1000 == 0) "" else ".${(ms % 1000).toString().padStart(3, '0').trimEnd('0')}"} 秒"
        }

    /**
     * 慢动作：传感器 fps → `4X（120）` / `8X（240）`（非「约几倍」）。
     */
    fun slowMoSpeedLabel(fps: Int): String? =
        when (fps) {
            100 -> "4X（100）"
            120 -> "4X（120）"
            200 -> "8X（200）"
            240 -> "8X（240）"
            else -> null
        }

    fun lapseIntervalLabel(tenths: Int): String? =
        when {
            tenths < 0 -> null
            tenths % 10 == 0 -> "${tenths / 10}s"
            else -> "${tenths / 10}.${tenths % 10}s"
        }

    fun lapseDurationLabel(sec: Int): String? =
        when {
            sec < 0 -> null
            sec == 0 -> "时长无限"
            sec % 3600 == 0 -> "时长 ${sec / 3600} 时"
            sec % 60 == 0 && sec >= 60 -> "时长 ${sec / 60} 分"
            else -> "时长 ${sec}s"
        }

    fun hyperlapseRatioLabel(ratio: Int): String? =
        when (ratio) {
            -1 -> null
            0 -> "倍速 Auto"
            2, 5, 10, 15, 30 -> "倍速 ${ratio}×"
            else -> "倍速 $ratio"
        }

    fun motionlapsePathLabel(path: Int): String? =
        when (path) {
            CameraCommands.MOTIONLAPSE_PATH_CUSTOM -> "自定义轨迹"
            CameraCommands.MOTIONLAPSE_PATH_L_TO_R -> "左→右"
            CameraCommands.MOTIONLAPSE_PATH_R_TO_L -> "右→左"
            else -> null
        }

    private fun photoTimerDelay(status: CameraStatus): Int? =
        status.photoTimerDelayMs.takeIf { it >= 0 }
            ?: status.photoTimerSeconds.takeIf { it >= 0 }
                ?.let { it * 1000 + status.photoTimerMillis.coerceAtLeast(0) }

    private fun formatChip(status: CameraStatus): String {
        if (status.isPhoto) return photoFormatChip(status)
        return when (status.shootingMode) {
            CameraCommands.SHOOT_SLOWMO -> slowMoFormatChip(status)
            CameraCommands.SHOOT_SUPER_NIGHT -> videoStyleFormatChip(status)
            CameraCommands.SHOOT_TIMELAPSE -> lapseFormatChip(status, hyper = false)
            CameraCommands.SHOOT_HYPERLAPSE -> lapseFormatChip(status, hyper = true)
            CameraCommands.SHOOT_MOTIONLAPSE -> motionlapseFormatChip(status)
            CameraCommands.SHOOT_PANO -> panoFormatChip(status)
            CameraCommands.SHOOT_VIDEO -> videoStyleFormatChip(status)
            else -> videoStyleFormatChip(status)
        }
    }

    private fun photoFormatChip(status: CameraStatus): String {
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

    /** 全景：样式 + 倒计时（不再显示 JPEG／「格式 —」）。 */
    private fun panoFormatChip(status: CameraStatus): String {
        val type =
            when (status.panoType) {
                CameraCommands.PANO_TYPE_180 -> "180°"
                CameraCommands.PANO_TYPE_3X3 -> "3×3"
                else -> "样式 —"
            }
        return listOf(type, formatPhotoTimer(photoTimerDelay(status))).joinToString(" · ")
    }

    private fun videoStyleFormatChip(status: CameraStatus): String {
        val base = recFormatParts(status)
        return if (base.isEmpty()) "—" else base.joinToString(" · ")
    }

    /** 分辨率 + `4X（120）`；不再并列单独的 `120p · 约4×`。 */
    private fun slowMoFormatChip(status: CameraStatus): String {
        val res = resolutionLabelOnly(status)
        val fps = resolvedFps(status)
        val speed = slowMoSpeedLabel(fps)
        val parts = listOfNotNull(res, speed ?: fps.takeIf { it > 0 }?.let { "${it}p" })
        return if (parts.isEmpty()) "—" else parts.joinToString(" · ")
    }

    private fun lapseFormatChip(status: CameraStatus, hyper: Boolean): String {
        val parts = recFormatParts(status).toMutableList()
        lapseSaveLabel(status.lapseSaveType)?.let { parts.add(it) }
        if (hyper) {
            hyperlapseRatioLabel(status.hyperlapseRatio)?.let { parts.add(it) }
        } else {
            lapseIntervalLabel(status.lapseIntervalTenths)?.let { parts.add("间隔 $it") }
            lapseDurationLabel(status.lapseDurationSec)?.let { parts.add(it) }
        }
        if (status.recordElapsedSec > 0 || status.isRecording) {
            parts.add("录制中")
        }
        return if (parts.isEmpty()) "规格 —" else parts.joinToString(" · ")
    }

    private fun motionlapseFormatChip(status: CameraStatus): String {
        val parts = recFormatParts(status).filter { it.isNotBlank() && it != "—" }.toMutableList()
        motionlapsePathLabel(status.motionlapsePath)?.let { parts.add(it) }
        // 机身轨迹点只有 2／3／4；1 点等噪声不展示。息屏不展示。
        if (status.motionlapsePointCount in 2..4) {
            parts.add("${status.motionlapsePointCount} 点")
        }
        lapseIntervalLabel(status.lapseIntervalTenths)?.let { parts.add("间隔 $it") }
        lapseDurationLabel(status.lapseDurationSec)?.let { parts.add(it) }
        lapseSaveLabel(status.lapseSaveType)?.let { parts.add(it) }
        if (status.recordElapsedSec > 0 || status.isRecording) {
            parts.add("录制中")
        }
        return if (parts.isEmpty()) "规格 —" else parts.joinToString(" · ")
    }

    private fun lapseSaveLabel(save: Int): String? =
        when (save) {
            0x00 -> "存视频"
            0x02 -> "JPEG+视频"
            0x03 -> "Raw+视频"
            else -> null
        }

    private fun recFormatParts(status: CameraStatus): List<String> {
        val parsed = VideoFormat.parse(status.resolutionCode, status.fpsIndex)
        if (parsed != null) return listOf(parsed.resolution.label, parsed.frameRate.drumLabel)
        val res = resolutionLabelOnly(status)
        val fpsText =
            when {
                status.fps > 0 -> "${status.fps}p"
                status.fpsIndex >= 0 -> VideoFrameRate.fromRaw(status.fpsIndex)?.drumLabel
                else -> null
            }
        return listOfNotNull(res, fpsText)
    }

    private fun resolutionLabelOnly(status: CameraStatus): String? {
        VideoFormat.parse(status.resolutionCode, status.fpsIndex)?.resolution?.label?.let { return it }
        return VideoResolution.fromRaw(status.resolutionCode)?.label?.takeIf { status.resolutionCode >= 0 }
            ?: status.recFormatLabel.takeIf { it.isNotBlank() && it != "—" }?.substringBefore(" · ")
                ?.takeIf { it.isNotBlank() }
    }

    private fun resolvedFps(status: CameraStatus): Int =
        status.fps.takeIf { it > 0 }
            ?: VideoFrameRate.fps(status.fpsIndex)
            ?: 0

    /** Auto 时带出机身当前实测 ISO；手动只显示数值。 */
    private fun isoChip(status: CameraStatus): String {
        val live = status.iso.takeIf { it in 50..102_400 }
        return when {
            status.isoIndex == 0 && live != null -> "ISO Auto $live"
            status.isoIndex == 0 -> "ISO Auto"
            live != null -> "ISO $live"
            else -> "—"
        }
    }

    /** Auto 时带出实时色温；Custom 显示设定 Kelvin。 */
    private fun wbChip(status: CameraStatus): String =
        when {
            status.wbMode == CameraCommands.WB_AUTO && status.autoWbKelvin > 0 ->
                "WB Auto ${status.autoWbKelvin}K"
            status.wbMode == CameraCommands.WB_AUTO -> "WB Auto"
            status.wbKelvin > 0 -> "WB ${status.wbKelvin}K"
            else -> "—"
        }
}
