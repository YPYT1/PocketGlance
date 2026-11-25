package com.opencapture.pocketglance.monitor

import android.content.pm.ActivityInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonitorLayoutTest {
    @Test
    fun touchesFollowTheRenderedCropAndQuarterTurn() {
        val (croppedX, croppedY) = requireNotNull(MonitorLayout.picturePoint(0f, 0f, 1200f, 900f, 16f / 9f, 0f))
        assertEquals(0.125f, croppedX, 0.001f)
        assertEquals(0f, croppedY, 0.001f)
        val (rotatedX, rotatedY) = requireNotNull(MonitorLayout.picturePoint(900f, 450f, 1200f, 900f, 9f / 16f, 90f))
        assertEquals(0.5f, rotatedX, 0.001f)
        assertTrue(rotatedY < 0.5f)
        val (screenX, screenY) = MonitorLayout.screenPoint(rotatedX, rotatedY, 1200f, 900f, 9f / 16f, 90f)
        assertEquals(900f, screenX, 0.001f)
        assertEquals(450f, screenY, 0.001f)
    }

    @Test
    fun centerCropPreservesSourceAspectAndCoversBothWindowOrientations() {
        for ((windowWidth, windowHeight) in listOf(2760f to 1256f, 1256f to 2760f)) {
            for (sourceAspect in listOf(16f / 9f, 9f / 16f)) {
                val rotation = MonitorLayout.contentRotationDegrees(windowHeight > windowWidth, sourceAspect < 1f)
                val quarterTurn = rotation != 0f
                val (width, height) =
                    MonitorLayout.contentSizeBeforeRotation(windowWidth, windowHeight, sourceAspect, quarterTurn)
                assertEquals(sourceAspect, width / height, 0.001f)
                assertTrue((if (quarterTurn) height else width) >= windowWidth - 0.1f)
                assertTrue((if (quarterTurn) width else height) >= windowHeight - 0.1f)
            }
        }
    }

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
