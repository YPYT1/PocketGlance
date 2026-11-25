package com.opencapture.pocketglance.ui

import com.opencapture.opencine.vocabulary.CameraCommands
import com.opencapture.opencine.vocabulary.CameraStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        assertEquals("超清 · 16:9 · 倒计时 3 秒 · 动图关", chrome.formatChip)
        assertEquals("—", chrome.colorChip)
        assertTrue(chrome.isPhoto)
        assertNull(chrome.logPreviewLabel)
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
    fun logPreviewDoesNotPretendTheCameraSettingIsOff() {
        val video = CameraStatus(shootingMode = CameraCommands.SHOOT_VIDEO)
        assertEquals("机身预览还原状态未读回", MonitorChromeLabels.from(video.copy(colorMode = CameraCommands.COLOR_DLOG)).logPreviewLabel)
        assertNull(MonitorChromeLabels.from(video.copy(colorMode = CameraCommands.COLOR_NORMAL)).logPreviewLabel)
    }
}
