package com.opencapture.pocketglance.monitor

import com.opencapture.opencine.motion.WorldLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class PocketPicturePoseTest {
    @Test
    fun bothSidesAndInvertedAreDistinctAfterSettling() {
        val pose = PocketPicturePose()
        val right = WorldLevel.Vec(0.0, 0.0, 1.0)
        val left = WorldLevel.Vec(0.0, 0.0, -1.0)
        val inverted = WorldLevel.Vec(0.0, 1.0, 0.0)
        assertEquals(PictureRotation.Upright, pose.observe(right, 0))
        assertEquals(PictureRotation.Right, pose.observe(right, 200))
        assertEquals(PictureRotation.Right, pose.observe(left, 300))
        assertEquals(PictureRotation.Left, pose.observe(left, 500))
        assertEquals(PictureRotation.Left, pose.observe(inverted, 600))
        assertEquals(PictureRotation.UpsideDown, pose.observe(inverted, 800))
    }

    @Test
    fun lookingDownOrAStaleSampleDoesNotInventAnOrientation() {
        val pose = PocketPicturePose()
        val right = WorldLevel.Vec(0.0, 0.0, 1.0)
        val plumb = WorldLevel.Vec(1.0, 0.0, 0.0)
        assertEquals(PictureRotation.Upright, pose.observe(right, 0))
        assertEquals(PictureRotation.Upright, pose.observe(plumb, 200))
        assertEquals(PictureRotation.Upright, pose.observe(right, 300))
        assertEquals(PictureRotation.Upright, pose.observe(right, 900))
        assertEquals(PictureRotation.Right, pose.observe(right, 1100))
    }
}
