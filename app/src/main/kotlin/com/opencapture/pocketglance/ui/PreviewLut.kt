package com.opencapture.pocketglance.ui

import com.opencapture.opencine.media.FeedEffectsRenderPlan
import com.opencapture.opencine.media.MonitorLUTCube
import java.io.Reader
import kotlin.math.floor

internal data class PreviewLut(
    val id: String,
    val name: String,
    /** Built-in pack / 合集；`null` = 用户导入. */
    val category: String? = null,
    val tags: Set<String> = emptySet(),
) {
    val isBuiltIn: Boolean get() = category != null
}

/** 筛选芯片顺序（收藏／全部另算）。 */
internal val LutTagFilters: List<String> =
    listOf("人像", "风景", "天空", "胶片", "夜景", "美食", "艺术", "季节", "Pocket4")

private val lutCountSuffix = Regex("[（(]?\\s*\\d+\\s*款\\s*[）)]?")
private val lutDateOrBatchToken =
    Regex(
        "20\\d{2}\\d{2}|20\\d{2}\\s*年|20\\d{2}|\\d{2}\\s*年|\\d{1,2}\\s*月|提前上新|上新|第[一二三四五六七八九十\\d]+弹|更新|LUT",
        RegexOption.IGNORE_CASE,
    )
private val lutThemePackNames =
    mapOf(
        "人像" to "人像合集",
        "风景" to "风景合集",
        "天空" to "天空合集",
        "胶片" to "胶片合集",
        "夜景" to "夜景合集",
        "美食" to "美食合集",
        "艺术" to "艺术合集",
        "季节" to "季节合集",
    )

/** 分类展示名：去「17款」、备注括号，并剥掉年份／上新批次词，留下主题合集名。 */
internal fun lutCategoryLabel(raw: String): String {
    var text = raw.replace(Regex("\\s+"), " ").trim()
    text = text.replace(lutCountSuffix, "")
    text = text.replace(Regex("[（(][^）)]*[）)]"), "")
    text = text.trim(' ', '-', '_', '·')
    while (true) {
        val next = text.replace(lutDateOrBatchToken, " ").replace(Regex("\\s+"), " ").trim(' ', '-', '_', '·', '/')
        if (next == text) break
        text = next
    }
    if (text.isEmpty() || text.matches(Regex("\\d+")) || text in setOf("滤镜文件", "导入教程", "滤镜")) {
        return ""
    }
    return lutThemePackNames[text] ?: text
}

/** 剥干净后没有主题名 → 纯日期／上新批次，不当侧栏分类。 */
internal fun isLutDateOrBatchCategory(raw: String): Boolean = lutCategoryLabel(raw).isEmpty()

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

internal fun parseBuiltInLutCatalogLine(line: String): PreviewLut? {
    val fields = line.split('\t')
    if (fields.size < 3) return null
    val tags =
        fields.getOrNull(3)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
    return PreviewLut(
        id = "built-in-luts/${fields[0]}",
        name = fields[2],
        category = fields[1],
        tags = tags,
    )
}
