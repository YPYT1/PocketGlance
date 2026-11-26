package com.opencapture.pocketglance.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewLutTest {
    @Test
    fun catalogLineParsesCategoryAndTags() {
        val lut = parseBuiltInLutCatalogLine("look-001.rgba\t人像合集\t元气\t人像,风景")
        assertEquals("built-in-luts/look-001.rgba", lut!!.id)
        assertEquals("元气", lut.name)
        assertEquals("人像合集", lut.category)
        assertEquals(setOf("人像", "风景"), lut.tags)
        assertTrue(lut.isBuiltIn)
    }

    @Test
    fun categoryLabelDropsCountAndDateBatches() {
        assertEquals("风景景观专用", lutCategoryLabel("风景景观专用17款"))
        assertEquals("阿莱色调", lutCategoryLabel("阿莱色调5款"))
        assertEquals("阿莱色调", lutCategoryLabel("阿莱色调（5款）"))
        assertEquals("人像合集", lutCategoryLabel("2026人像合集"))
        assertEquals("胶片合集", lutCategoryLabel("2026胶片"))
        assertEquals("人像合集", lutCategoryLabel("人像"))
        assertTrue(isLutDateOrBatchCategory("202602更新"))
        assertTrue(isLutDateOrBatchCategory("26年7月更新"))
        assertTrue(isLutDateOrBatchCategory("8月上新第一弹"))
        assertTrue(isLutDateOrBatchCategory("上新第二弹"))
        assertFalse(isLutDateOrBatchCategory("风景景观专用"))
        assertFalse(isLutDateOrBatchCategory("人像合集"))
        assertFalse(isLutDateOrBatchCategory("阿莱色调"))
    }

    @Test
    fun redFastestIdentityAndDomainAreMappedCorrectly() {
        val cube = parsePreviewCube(identity(size = 3, domain = true).reader())
        val (red, green, blue) = cube.map(0.25f, 0.5f, 0.75f)
        assertEquals(0f, red, 0.02f)
        assertEquals(0.5f, green, 0.02f)
        assertEquals(1f, blue, 0.02f)
    }

    @Test
    fun sixtyFivePointCubeFitsTheGlesAtlas() {
        val cube = parsePreviewCube(identity(size = 65).reader())
        assertEquals(64, cube.size)
        assertEquals(Triple(1f, 0f, 0f), cube.map(1f, 0f, 0f))
        assertEquals(Triple(0f, 1f, 0f), cube.map(0f, 1f, 0f))
    }

    @Test
    fun incompleteOrInvalidCubeFailsBeforeDisplay() {
        assertFailsWith<IllegalArgumentException> { parsePreviewCube("LUT_3D_SIZE 2\n1 1 1".reader()) }
        assertFailsWith<IllegalArgumentException> { parsePreviewCube("LUT_3D_SIZE 9000".reader()) }
    }

    @Test
    fun optionalExternalSample() {
        val path = System.getenv("POCKETGLANCE_CUBE_SAMPLE") ?: return
        val cube = File(path).bufferedReader().use(::parsePreviewCube)
        assertTrue(cube.size in 2..64)
    }

    private fun identity(size: Int, domain: Boolean = false): String = buildString {
        appendLine("TITLE \"Identity\"")
        appendLine("LUT_3D_SIZE $size")
        if (domain) {
            appendLine("DOMAIN_MIN 0.25 0.25 0.25")
            appendLine("DOMAIN_MAX 0.75 0.75 0.75")
        }
        for (blue in 0 until size) for (green in 0 until size) for (red in 0 until size) {
            val scale = (size - 1).toFloat()
            appendLine("${red / scale} ${green / scale} ${blue / scale}")
        }
    }
}
