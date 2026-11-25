package com.opencapture.pocketglance.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PreviewLutTest {
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
