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

/**
 * 侧栏筛选顺序（收藏／全部另算）。
 * 前 13 项按产品约定置顶，其余分类排在后面。
 */
internal val LutTagFilters: List<String> =
    listOf(
        "阿莱",
        "人像",
        "风景",
        "胶片",
        "电影",
        "夜景",
        "季节",
        "美食",
        "艺术",
        "航拍",
        "富士",
        "肤色",
        "Pocket4",
        // 其余
        "天空",
        "日系",
        "视觉",
        "官方",
        "口播",
        "通用",
    )

/** 合集／分类在侧栏中的排序权重；未列出的排最后。 */
internal fun lutFilterSortKey(label: String): Int {
    val index = LutTagFilters.indexOf(label)
    return if (index >= 0) index else LutTagFilters.size
}

private val lutCountSuffix = Regex("[（(]?\\s*\\d+\\s*款\\s*[）)]?")
private val lutLeadingIndex = Regex("^\\d+[\\.、．\\s]+")
private val lutDateOrBatchToken =
    Regex(
        "20\\d{2}\\d{2}|20\\d{2}\\s*年|20\\d{2}|\\d{2}\\s*年|\\d{1,2}\\s*月|提前上新|上新|第[一二三四五六七八九十\\d]+弹|更新|LUT",
        RegexOption.IGNORE_CASE,
    )
/** 旧合集名 / 温影长目录 → ≤4 字分类。 */
private val lutThemePackNames =
    mapOf(
        "人像" to "人像",
        "人像合集" to "人像",
        "风景" to "风景",
        "风景合集" to "风景",
        "风景景观专用" to "风景",
        "天空" to "天空",
        "天空合集" to "天空",
        "胶片" to "胶片",
        "胶片合集" to "胶片",
        "夜景" to "夜景",
        "夜景合集" to "夜景",
        "美食" to "美食",
        "美食合集" to "美食",
        "艺术" to "艺术",
        "艺术合集" to "艺术",
        "季节" to "季节",
        "季节合集" to "季节",
        "阿莱" to "阿莱",
        "阿莱色调" to "阿莱",
        "电影" to "电影",
        "日系" to "日系",
        "富士" to "富士",
        "航拍" to "航拍",
        "肤色" to "肤色",
        "口播" to "口播",
        "官方" to "官方",
        "视觉" to "视觉",
        "通用" to "通用",
        "Pocket4" to "Pocket4",
    )

/** 分类展示名：去「17款」、备注括号、年份批次，统一到 ≤4 字主题名。 */
internal fun lutCategoryLabel(raw: String): String {
    var text = raw.replace(Regex("\\s+"), " ").trim()
    text = text.replace(lutCountSuffix, "")
    text = text.replace(Regex("[（(][^）)]*[）)]"), "")
    text = text.replace(lutLeadingIndex, "")
    text = text.trim(' ', '-', '_', '·')
    while (true) {
        val next = text.replace(lutDateOrBatchToken, " ").replace(Regex("\\s+"), " ").trim(' ', '-', '_', '·', '/')
        if (next == text) break
        text = next
    }
    if (text.isEmpty() || text.matches(Regex("\\d+")) || text in setOf("滤镜文件", "导入教程", "滤镜", "还原", "风格")) {
        return ""
    }
    lutThemePackNames[text]?.let { return it }
    val stripped = text.replace(Regex("(合集|专用|色调|系列)$"), "").trim()
    lutThemePackNames[stripped]?.let { return it }
    return if (text.length <= 4) text else text.take(4)
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
