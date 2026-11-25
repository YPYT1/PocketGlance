package com.opencapture.pocketglance.monitor

import android.content.pm.ActivityInfo
import kotlin.math.max

enum class MonitorOrientation { LandscapeLocked, AutoRotate }

object MonitorLayout {
    const val HOME_ORIENTATION: Int = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    const val SENSOR_ORIENTATION: Int = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

    fun monitorOrientation(preference: MonitorOrientation, picturePortrait: Boolean): Int = when {
        preference == MonitorOrientation.LandscapeLocked -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        picturePortrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun autoPictureRotation(cameraVertical: Boolean, pose: PictureRotation): PictureRotation =
        if (cameraVertical && pose.quarterTurn) PictureRotation.Upright else pose

    fun isPortraitPicture(aspect: Float, rotation: PictureRotation): Boolean =
        if (rotation.quarterTurn) aspect > 1f else aspect < 1f

    fun fittedSize(viewW: Float, viewH: Float, contentAspect: Float): Pair<Float, Float> {
        if (viewW <= 0f || viewH <= 0f || contentAspect <= 0f) return 0f to 0f
        val viewAspect = viewW / viewH
        return if (viewAspect > contentAspect) {
            val h = viewH
            val w = h * contentAspect
            w to h
        } else {
            val w = viewW
            val h = w / contentAspect
            w to h
        }
    }

    fun contentSizeBeforeRotation(
        viewWidth: Float,
        viewHeight: Float,
        sourceAspect: Float,
        quarterTurn: Boolean,
    ): Pair<Float, Float> {
        if (viewWidth <= 0f || viewHeight <= 0f || sourceAspect <= 0f) return 0f to 0f
        val targetWidth = if (quarterTurn) viewHeight else viewWidth
        val targetHeight = if (quarterTurn) viewWidth else viewHeight
        val width = max(targetWidth, targetHeight * sourceAspect)
        return width to width / sourceAspect
    }

    fun pictureSize(width: Float, height: Float, aspect: Float, rotation: PictureRotation): Pair<Float, Float> {
        if (width <= 0f || height <= 0f || aspect <= 0f) return 0f to 0f
        val portrait = isPortraitPicture(aspect, rotation)
        // The Pocket already supplies a square picture in 1:1; fit it instead of clipping its edges to fill the screen.
        if (aspect != 1f && portrait == (height > width)) {
            return contentSizeBeforeRotation(width, height, aspect, rotation.quarterTurn)
        }
        val targetAspect = if (rotation.quarterTurn) 1f / aspect else aspect
        val (fittedWidth, fittedHeight) = fittedSize(width, height, targetAspect)
        return if (rotation.quarterTurn) fittedHeight to fittedWidth else fittedWidth to fittedHeight
    }

    fun picturePoint(x: Float, y: Float, width: Float, height: Float, aspect: Float, rotation: PictureRotation): Pair<Float, Float>? {
        val (contentWidth, contentHeight) = pictureSize(width, height, aspect, rotation)
        if (contentWidth <= 0f || contentHeight <= 0f) return null
        val localX = x - width / 2f
        val localY = y - height / 2f
        val (pictureX, pictureY) = when (rotation) {
            PictureRotation.Upright -> localX to localY
            PictureRotation.Right -> localY to -localX
            PictureRotation.UpsideDown -> -localX to -localY
            PictureRotation.Left -> -localY to localX
        }
        val sourceX = 0.5f + pictureX / contentWidth
        val sourceY = 0.5f + pictureY / contentHeight
        return if (sourceX in 0f..1f && sourceY in 0f..1f) sourceX to sourceY else null
    }

    fun screenPoint(x: Float, y: Float, width: Float, height: Float, aspect: Float, rotation: PictureRotation): Pair<Float, Float> {
        val (contentWidth, contentHeight) = pictureSize(width, height, aspect, rotation)
        val localX = (x - 0.5f) * contentWidth
        val localY = (y - 0.5f) * contentHeight
        val (viewX, viewY) = when (rotation) {
            PictureRotation.Upright -> localX to localY
            PictureRotation.Right -> -localY to localX
            PictureRotation.UpsideDown -> -localX to -localY
            PictureRotation.Left -> localY to -localX
        }
        return (width / 2f + viewX) to (height / 2f + viewY)
    }
}
