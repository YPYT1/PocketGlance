package com.opencapture.pocketglance.ui

import com.opencapture.opencine.media.FeedEffectsRenderPlan
import com.opencapture.opencine.media.MonitorLUTCube
import java.io.Reader
import kotlin.math.floor

internal data class PreviewLut(val id: String, val name: String, val category: String? = null)

internal fun parsePreviewCube(source: Reader): MonitorLUTCube {
    var size = 0
    var minimum = floatArrayOf(0f, 0f, 0f)
    var maximum = floatArrayOf(1f, 1f, 1f)
    var values = FloatArray(0)
    var offset = 0
    var characters = 0L
    source.buffered().useLines { lines ->
        lines.forEach { raw ->
            characters += raw.length + 1L
            require(characters <= 24_000_000L)
            val line = raw.trimStart('\uFEFF').substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            val fields = cubeWhitespace.split(line, limit = 4)
            when (fields[0]) {
                "TITLE" -> Unit
                "LUT_3D_SIZE" -> {
                    require(size == 0 && fields.size == 2)
                    size = fields[1].toInt()
                    require(size in 2..65)
                    values = FloatArray(size * size * size * 3)
                }
                "DOMAIN_MIN", "DOMAIN_MAX", "LUT_3D_INPUT_RANGE" -> {
                    require(offset == 0)
                    when (fields[0]) {
                        "DOMAIN_MIN" -> minimum = fields.drop(1).map(String::toFloat).toFloatArray()
                        "DOMAIN_MAX" -> maximum = fields.drop(1).map(String::toFloat).toFloatArray()
                        else -> {
                            require(fields.size == 3)
                            minimum = FloatArray(3) { fields[1].toFloat() }
                            maximum = FloatArray(3) { fields[2].toFloat() }
                        }
                    }
                }
                else -> {
                    require(fields.size == 3 && offset + 3 <= values.size)
                    fields.forEach { values[offset++] = it.toFloat().also { channel -> require(channel.isFinite()) } }
                }
            }
        }
    }
    require(size > 0 && offset == values.size && minimum.size == 3 && maximum.size == 3)
    require((0..2).all { minimum[it].isFinite() && maximum[it].isFinite() && maximum[it] > minimum[it] })
    if (size <= 64 && minimum.contentEquals(floatArrayOf(0f, 0f, 0f)) && maximum.contentEquals(floatArrayOf(1f, 1f, 1f))) {
        return MonitorLUTCube.fromRGB(size, values)
    }
    val outputSize = 64
    val output = FloatArray(outputSize * outputSize * outputSize * 3)
    fun coordinate(index: Int, channel: Int): Float =
        ((index.toFloat() / (outputSize - 1) - minimum[channel]) / (maximum[channel] - minimum[channel]))
            .coerceIn(0f, 1f) * (size - 1)
    fun sample(red: Int, green: Int, blue: Int, channel: Int): Float =
        values[(red + size * (green + size * blue)) * 3 + channel]
    var packed = 0
    for (blue in 0 until outputSize) for (green in 0 until outputSize) for (red in 0 until outputSize) {
        val redPos = coordinate(red, 0)
        val greenPos = coordinate(green, 1)
        val bluePos = coordinate(blue, 2)
        val redLow = floor(redPos).toInt()
        val greenLow = floor(greenPos).toInt()
        val blueLow = floor(bluePos).toInt()
        for (channel in 0..2) {
            var total = 0f
            for (blueStep in 0..1) for (greenStep in 0..1) for (redStep in 0..1) {
                val weight = lutWeight(redStep, redPos - redLow) * lutWeight(greenStep, greenPos - greenLow) * lutWeight(blueStep, bluePos - blueLow)
                total += weight * sample(
                    (redLow + redStep).coerceAtMost(size - 1), (greenLow + greenStep).coerceAtMost(size - 1),
                    (blueLow + blueStep).coerceAtMost(size - 1), channel,
                )
            }
            output[packed++] = total
        }
    }
    return MonitorLUTCube.fromRGB(outputSize, output)
}

private val cubeWhitespace = Regex("\\s+")
private fun lutWeight(step: Int, fraction: Float): Float = if (step == 0) 1f - fraction else fraction

internal fun previewLook(cube: MonitorLUTCube): FeedEffectsRenderPlan = with(FeedEffectsRenderPlan.IDENTITY) {
    FeedEffectsRenderPlan(cube, null, null, false, peakingColor, peakingRatioThreshold, peakingNoiseGate,
        false, zebraHighlightCode, zebraHighlightColor, false, zebraMidtoneCode, zebraMidtoneHalf,
        zebraMidtoneColor, false, splitVertical)
}
