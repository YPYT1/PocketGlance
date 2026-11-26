package com.opencapture.pocketglance.ui

import com.opencapture.opencine.vocabulary.CameraCommands
import com.opencapture.opencine.vocabulary.CameraStatus
import com.opencapture.opencine.vocabulary.VideoFrameRate
import com.opencapture.opencine.vocabulary.VideoResolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonitorChromeLabelsTest {
    @Test
    fun photoShowsConfigurationAndIgnoresLastVideoColor() {
        val chrome = MonitorChromeLabels.from(CameraStatus(
            shootingMode = CameraCommands.SHOOT_PHOTO_POCKET4,
            photoSizeMode = 4,
            photoAspect = 1,
            photoTimerDelayMs = 3000,
            colorMode = CameraCommands.COLOR_DLOG2,
        ))
        assertEquals("拍照", chrome.shootingModeLabel)
        assertEquals("超清 · 16:9 · 倒计时 3 秒 · 动图关", chrome.formatChip)
        assertEquals("—", chrome.colorChip)
        assertTrue(chrome.isPhoto)
        assertFalse(chrome.supportsPreviewLut)
    }

    @Test
    fun livePhotoShowsSquareAndTimerOffInsteadOfStaleSplitTimer() {
        val chrome = MonitorChromeLabels.from(CameraStatus(
            shootingMode = CameraCommands.SHOOT_LIVE_PHOTO,
            photoSizeMode = 0,
            photoAspect = 3,
            photoTimerSeconds = 3,
            photoTimerMillis = 0,
            photoTimerDelayMs = 0,
        ))
        assertEquals("动图", chrome.shootingModeLabel)
        assertEquals("标准 · 1:1 · 倒计时关 · 动图开", chrome.formatChip)
    }

    @Test
    fun unknownTimerDoesNotLookLikeOffAndLegacyTimerCanBeRead() {
        val status = CameraStatus(shootingMode = CameraCommands.SHOOT_PHOTO_POCKET4)
        assertEquals("画质 — · 比例 — · 倒计时 — · 动图关", MonitorChromeLabels.from(status).formatChip)
        assertEquals(0, MonitorChromeLabels.from(status).photoTimerRemainingSeconds)
        val running = status.copy(photoTimerSeconds = 5, photoTimerRemainingSeconds = 2)
        assertEquals("画质 — · 比例 — · 倒计时 5 秒 · 动图关", MonitorChromeLabels.from(running).formatChip)
        assertEquals(2, MonitorChromeLabels.from(running).photoTimerRemainingSeconds)
        assertEquals("倒计时 2.5 秒", MonitorChromeLabels.formatPhotoTimer(2500))
    }

    @Test
    fun localLutIsOnlyAvailableInDLogAndDLog2() {
        val video = CameraStatus(shootingMode = CameraCommands.SHOOT_VIDEO)
        assertTrue(MonitorChromeLabels.from(video.copy(colorMode = CameraCommands.COLOR_DLOG)).supportsPreviewLut)
        assertTrue(MonitorChromeLabels.from(video.copy(colorMode = CameraCommands.COLOR_DLOG2)).supportsPreviewLut)
        assertFalse(MonitorChromeLabels.from(video.copy(colorMode = CameraCommands.COLOR_DLOG_M)).supportsPreviewLut)
        assertFalse(MonitorChromeLabels.from(video.copy(colorMode = CameraCommands.COLOR_NORMAL)).supportsPreviewLut)
    }

    @Test
    fun videoKeepsNormalColorButLowLightSlowMoHyperHideIt() {
        val video =
            MonitorChromeLabels.from(
                CameraStatus(
                    shootingMode = CameraCommands.SHOOT_VIDEO,
                    resolutionCode = VideoResolution.P4K.rawValue,
                    fpsIndex = VideoFrameRate.FPS60.rawValue,
                    fps = 60,
                    colorMode = CameraCommands.COLOR_NORMAL,
                ),
            )
        assertEquals("视频", video.shootingModeLabel)
        assertEquals("4K · 60p", video.formatChip)
        assertEquals("普通", video.colorChip)

        val lowLight =
            MonitorChromeLabels.from(
                CameraStatus(
                    shootingMode = CameraCommands.SHOOT_SUPER_NIGHT,
                    resolutionCode = VideoResolution.P1080.rawValue,
                    fpsIndex = VideoFrameRate.FPS25.rawValue,
                    fps = 25,
                    colorMode = CameraCommands.COLOR_NORMAL,
                ),
            )
        assertEquals("低光视频", lowLight.shootingModeLabel)
        assertEquals("1080p · 25p", lowLight.formatChip)
        assertEquals("—", lowLight.colorChip)

        assertEquals(
            "—",
            MonitorChromeLabels.from(
                CameraStatus(
                    shootingMode = CameraCommands.SHOOT_SLOWMO,
                    colorMode = CameraCommands.COLOR_NORMAL,
                ),
            ).colorChip,
        )
        assertEquals(
            "—",
            MonitorChromeLabels.from(
                CameraStatus(
                    shootingMode = CameraCommands.SHOOT_HYPERLAPSE,
                    colorMode = CameraCommands.COLOR_NORMAL,
                ),
            ).colorChip,
        )
        assertEquals(
            "—",
            MonitorChromeLabels.from(
                CameraStatus(
                    shootingMode = CameraCommands.SHOOT_TIMELAPSE,
                    colorMode = CameraCommands.COLOR_NORMAL,
                ),
            ).colorChip,
        )
    }

    @Test
    fun slowMoShowsSpeedWithFpsInParens() {
        val fourX =
            MonitorChromeLabels.from(
                CameraStatus(
                    shootingMode = CameraCommands.SHOOT_SLOWMO,
                    resolutionCode = VideoResolution.P4K.rawValue,
                    fpsIndex = VideoFrameRate.FPS120.rawValue,
                    fps = 120,
                ),
            )
        assertEquals("慢动作", fourX.shootingModeLabel)
        assertEquals("4K · 4X（120）", fourX.formatChip)
        assertEquals("8X（240）", MonitorChromeLabels.slowMoSpeedLabel(240))
        assertEquals("8X（200）", MonitorChromeLabels.slowMoSpeedLabel(200))
        assertEquals(null, MonitorChromeLabels.slowMoSpeedLabel(60))
    }

    @Test
    fun lapseModesShowKnownFormatAndUnknownModeShowsHex() {
        val lapse =
            MonitorChromeLabels.from(
                CameraStatus(
                    shootingMode = CameraCommands.SHOOT_TIMELAPSE,
                    resolutionCode = VideoResolution.P1080.rawValue,
                    fpsIndex = VideoFrameRate.FPS30.rawValue,
                    fps = 30,
                    lapseSaveType = 0x02,
                    lapseIntervalTenths = 20,
                    lapseDurationSec = 300,
                ),
            )
        assertEquals("静止延时", lapse.shootingModeLabel)
        assertEquals("1080p · 30p · JPEG+视频 · 间隔 2s · 时长 5 分", lapse.formatChip)

        val hyper =
            MonitorChromeLabels.from(
                CameraStatus(
                    shootingMode = CameraCommands.SHOOT_HYPERLAPSE,
                    isRecording = true,
                    resolutionCode = VideoResolution.P2_7K.rawValue,
                    fpsIndex = VideoFrameRate.FPS30.rawValue,
                    fps = 30,
                    hyperlapseRatio = 15,
                ),
            )
        assertEquals("运动延时", hyper.shootingModeLabel)
        assertEquals("2.7K · 30p · 倍速 15× · 录制中", hyper.formatChip)

        assertEquals("模式 0x3C", MonitorChromeLabels.shootingModeZh(0x3C))
    }

    @Test
    fun panoShowsCountdownNotStorageAndMotionlapseFiltersPoints() {
        val pano =
            MonitorChromeLabels.from(
                CameraStatus(
                    shootingMode = CameraCommands.SHOOT_PANO,
                    panoType = CameraCommands.PANO_TYPE_180,
                    photoTimerDelayMs = 5000,
                    photoTimerRemainingSeconds = 3,
                ),
            )
        assertEquals("全景", pano.shootingModeLabel)
        assertEquals("180° · 倒计时 5 秒", pano.formatChip)
        assertEquals("—", pano.colorChip)
        assertEquals(3, pano.photoTimerRemainingSeconds)

        val motion =
            MonitorChromeLabels.from(
                CameraStatus(
                    shootingMode = CameraCommands.SHOOT_MOTIONLAPSE,
                    resolutionCode = VideoResolution.P2_7K.rawValue,
                    fpsIndex = VideoFrameRate.FPS25.rawValue,
                    fps = 25,
                    motionlapsePath = CameraCommands.MOTIONLAPSE_PATH_R_TO_L,
                    motionlapsePointCount = 4,
                    lapseIntervalTenths = 5,
                    lapseScreenOff = 1,
                ),
            )
        assertEquals("轨迹延时", motion.shootingModeLabel)
        assertEquals("2.7K · 25p · 右→左 · 4 点 · 间隔 0.5s", motion.formatChip)

        val onePoint =
            MonitorChromeLabels.from(
                CameraStatus(
                    shootingMode = CameraCommands.SHOOT_MOTIONLAPSE,
                    resolutionCode = VideoResolution.P1080.rawValue,
                    fpsIndex = VideoFrameRate.FPS30.rawValue,
                    fps = 30,
                    motionlapsePointCount = 1,
                    motionlapsePath = CameraCommands.MOTIONLAPSE_PATH_L_TO_R,
                ),
            )
        assertEquals("1080p · 30p · 左→右", onePoint.formatChip)
    }
}
