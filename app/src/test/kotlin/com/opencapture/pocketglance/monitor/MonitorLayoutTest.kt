package com.opencapture.pocketglance.monitor

import android.content.pm.ActivityInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonitorLayoutTest {
    @Test
    fun landscapeCameraForcesPhoneLandscape() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            MonitorLayout.forcedActivityOrientation(cameraVertical = false),
        )
        assertEquals(16f / 9f, MonitorLayout.plan(false).contentAspect)
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            MonitorLayout.plan(false).requestedOrientation,
        )
    }

    @Test
    fun portraitCameraForcesPhonePortrait() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            MonitorLayout.forcedActivityOrientation(cameraVertical = true),
        )
        assertEquals(9f / 16f, MonitorLayout.plan(true).contentAspect)
    }

    @Test
    fun homeStaysPortrait() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, MonitorLayout.HOME_ORIENTATION)
    }

    @Test
    fun fittedSizeLetterboxesWideContentInTallView() {
        val (w, h) = MonitorLayout.fittedSize(900f, 1600f, 16f / 9f)
        assertEquals(900f, w, 0.1f)
        assertTrue(h < 1600f)
    }

    @Test
    fun fittedSizePillarboxesTallContentInWideView() {
        val (w, h) = MonitorLayout.fittedSize(1600f, 900f, 9f / 16f)
        assertEquals(900f, h, 0.1f)
        assertTrue(w < 1600f)
    }
}
