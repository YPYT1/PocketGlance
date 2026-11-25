package com.opencapture.pocketglance.monitor

import android.content.pm.ActivityInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonitorLayoutTest {
    @Test
    fun monitorOrientationHonorsTheSavedRotationMode() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, MonitorLayout.monitorOrientation(MonitorOrientation.LandscapeLocked, true))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, MonitorLayout.monitorOrientation(MonitorOrientation.LandscapeLocked, false))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, MonitorLayout.monitorOrientation(MonitorOrientation.AutoRotate, true))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, MonitorLayout.monitorOrientation(MonitorOrientation.AutoRotate, false))
    }

    @Test
    fun touchesFollowTheRenderedCropAndQuarterTurn() {
        val (croppedX, croppedY) = requireNotNull(MonitorLayout.picturePoint(0f, 0f, 1200f, 900f, 16f / 9f, PictureRotation.Upright))
        assertEquals(0.125f, croppedX, 0.001f)
        assertEquals(0f, croppedY, 0.001f)
        val (rotatedX, rotatedY) = requireNotNull(MonitorLayout.picturePoint(900f, 450f, 1200f, 900f, 9f / 16f, PictureRotation.Right))
        assertEquals(0.5f, rotatedX, 0.001f)
        assertTrue(rotatedY < 0.5f)
        val (screenX, screenY) = MonitorLayout.screenPoint(rotatedX, rotatedY, 1200f, 900f, 9f / 16f, PictureRotation.Right)
        assertEquals(900f, screenX, 0.001f)
        assertEquals(450f, screenY, 0.001f)
    }

    @Test
    fun centerCropPreservesSourceAspectAndCoversBothWindowOrientations() {
        for ((windowWidth, windowHeight) in listOf(2760f to 1256f, 1256f to 2760f)) {
            for (sourceAspect in listOf(16f / 9f, 9f / 16f)) {
                for (rotation in PictureRotation.entries) {
                    val (width, height) = MonitorLayout.contentSizeBeforeRotation(windowWidth, windowHeight, sourceAspect, rotation.quarterTurn)
                    assertEquals(sourceAspect, width / height, 0.001f)
                    assertTrue((if (rotation.quarterTurn) height else width) >= windowWidth - 0.1f)
                    assertTrue((if (rotation.quarterTurn) width else height) >= windowHeight - 0.1f)
                }
            }
        }
    }

    @Test
    fun squareFeedShowsAllFourEdgesInEitherWindowOrientation() {
        for ((windowWidth, windowHeight) in listOf(1600f to 900f, 900f to 1600f)) {
            for (rotation in PictureRotation.entries) {
                assertEquals(900f to 900f, MonitorLayout.pictureSize(windowWidth, windowHeight, 1f, rotation))
                for ((sourceX, sourceY) in listOf(0f to 0f, 0f to 1f, 1f to 0f, 1f to 1f)) {
                    val (x, y) = MonitorLayout.screenPoint(sourceX, sourceY, windowWidth, windowHeight, 1f, rotation)
                    val screenLeft = (windowWidth - 900f) / 2f
                    val screenTop = (windowHeight - 900f) / 2f
                    assertTrue(x == screenLeft || x == screenLeft + 900f)
                    assertTrue(y == screenTop || y == screenTop + 900f)
                    assertEquals(sourceX to sourceY, MonitorLayout.picturePoint(x, y, windowWidth, windowHeight, 1f, rotation))
                }
                val (blackX, blackY) = if (windowWidth > windowHeight) 10f to 450f else 450f to 10f
                assertNull(MonitorLayout.picturePoint(blackX, blackY, windowWidth, windowHeight, 1f, rotation))
            }
        }
    }

    @Test
    fun switchingBackToWideFeedKeepsTheExistingVideoFraming() {
        val wide = 1280f / 720f
        val square = 720f / 720f
        val rotation = PictureRotation.Upright
        val video = MonitorLayout.pictureSize(1920f, 1080f, wide, rotation)
        assertEquals(1920f to 1080f, video)
        assertEquals(1080f to 1080f, MonitorLayout.pictureSize(1920f, 1080f, square, rotation))
        assertEquals(video, MonitorLayout.pictureSize(1920f, 1080f, wide, rotation))
    }

    @Test
    fun eitherSidewaysWidePictureIsPortraitWithoutChangingTheSource() {
        val aspect = 16f / 9f
        for (pose in listOf(PictureRotation.Left, PictureRotation.Right)) {
            val rotation = MonitorLayout.autoPictureRotation(cameraVertical = false, pose)
            assertEquals(pose, rotation)
            assertTrue(MonitorLayout.isPortraitPicture(aspect, rotation))
            val (x, y) = MonitorLayout.screenPoint(0.62f, 0.55f, 1200f, 900f, aspect, rotation)
            val (sourceX, sourceY) = requireNotNull(MonitorLayout.picturePoint(x, y, 1200f, 900f, aspect, rotation))
            assertEquals(0.62f, sourceX, 0.001f)
            assertEquals(0.55f, sourceY, 0.001f)
            assertNull(MonitorLayout.picturePoint(10f, 450f, 1200f, 900f, aspect, rotation))
        }
    }

    @Test
    fun portraitBitstreamIsNotTurnedSidewaysAgainButCanBeFlipped() {
        assertEquals(PictureRotation.Upright, MonitorLayout.autoPictureRotation(true, PictureRotation.Left))
        val rotation = MonitorLayout.autoPictureRotation(true, PictureRotation.UpsideDown)
        assertEquals(PictureRotation.UpsideDown, rotation)
        assertTrue(MonitorLayout.isPortraitPicture(9f / 16f, rotation))
        assertEquals(0.75f to 0.75f, MonitorLayout.picturePoint(225f, 400f, 900f, 1600f, 9f / 16f, rotation))
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
