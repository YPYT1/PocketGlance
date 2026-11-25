package com.opencapture.pocketglance.monitor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FrameSyncedRasterTest {
    private val wide = PictureRaster(1280, 720)
    private val square = PictureRaster(720, 720)

    @Test
    fun oldPictureKeepsItsGeometryThroughALongGapAndQueuedOldFrames() {
        val feed = FrameSyncedRaster(1280, 720, startedAtNs = 1)
        assertEquals(wide, feed.present(10))

        feed.observe(720, 720, observedAtNs = 800_000_000)
        assertEquals(square, feed.state.value.source)
        assertEquals(wide, feed.state.value.display)
        assertNull(feed.present(10)) // redraw of held picture
        assertNull(feed.present(17)) // old decoder buffer arriving late
        assertEquals(wide, feed.state.value.display)

        assertEquals(square, feed.present(800_000_001))
        assertEquals(square, feed.state.value.display)

        feed.observe(1280, 720, observedAtNs = 1_600_000_000)
        assertEquals(square, feed.state.value.display)
        assertNull(feed.present(800_000_001))
        assertEquals(wide, feed.present(1_600_000_001))
        assertEquals(wide, feed.state.value.display)
    }

    @Test
    fun latestFormatWinsIfCameraSwitchesAgainBeforeSendingAFrame() {
        val feed = FrameSyncedRaster(1280, 720, startedAtNs = 1)
        feed.present(10)
        feed.observe(720, 720, observedAtNs = 30)
        feed.observe(1280, 720, observedAtNs = 50)
        assertNull(feed.present(40))
        assertNull(feed.present(51)) // wide was already on screen
        assertEquals(wide, feed.state.value.display)
    }

    @Test
    fun repeatedAnnouncementsAndInvalidDimensionsDoNotPushBackTheSwitch() {
        val feed = FrameSyncedRaster(1280, 720, startedAtNs = 1)
        feed.present(10)
        feed.observe(720, 720, observedAtNs = 30)
        feed.observe(720, 720, observedAtNs = 45)
        feed.observe(0, 0, observedAtNs = 46)
        feed.observe(1280, 720, observedAtNs = 20)
        assertEquals(square, feed.present(35))
    }

    @Test
    fun aNewSessionIgnoresFramesFromThePreviousSessionAndStartsWithItsSourceShape() {
        val feed = FrameSyncedRaster(1280, 720, startedAtNs = 1_000)
        feed.observe(720, 720, observedAtNs = 1_030)
        assertNull(feed.present(999))
        assertNull(feed.state.value.presented)
        assertEquals(square, feed.state.value.display)
        assertEquals(square, feed.present(1_031))
    }
}
