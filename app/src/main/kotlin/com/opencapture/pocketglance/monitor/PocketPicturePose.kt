package com.opencapture.pocketglance.monitor

import com.opencapture.opencine.motion.WorldLevel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

internal class PocketPicturePose {
    var rotation: PictureRotation = PictureRotation.Upright
        private set
    private var candidate = rotation
    private var candidateSince = 0L
    private var lastSample: Long? = null

    fun observe(gravity: WorldLevel.Vec, now: Long): PictureRotation {
        if (!gravity.y.isFinite() || !gravity.z.isFinite() || hypot(gravity.y, gravity.z) < 0.45) {
            lastSample = null
            return rotation
        }
        val clockwiseCorrection = Math.toDegrees(atan2(gravity.z, -gravity.y))
        val nearest = PictureRotation.entries.minBy { distance(clockwiseCorrection, it.degrees) }
        val target = if (distance(clockwiseCorrection, nearest.degrees) <= 32.0) nearest else rotation
        val previous = lastSample
        lastSample = now
        if (target != candidate || previous == null || now - previous !in 0..350) {
            candidate = target
            candidateSince = now
        } else if (now - candidateSince >= 180) {
            rotation = target
        }
        return rotation
    }

    private fun distance(angle: Double, quarterTurn: Float): Double = abs((angle - quarterTurn + 540) % 360 - 180)
}

enum class PictureRotation(val degrees: Float, val quarterTurn: Boolean, val label: String) {
    Upright(0f, false, "不旋转"),
    Right(90f, true, "向右转 90°"),
    UpsideDown(180f, false, "旋转 180°"),
    Left(-90f, true, "向左转 90°"),
}
