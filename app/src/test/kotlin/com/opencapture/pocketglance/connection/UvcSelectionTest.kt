package com.opencapture.pocketglance.connection

import com.opencapture.pocketglance.connection.UvcSelection.UsbInterfaceSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UvcSelectionTest {
    @Test
    fun picksHighestSixteenNineUnder1080p() {
        val selected = UvcSelection.selectResolution(
            listOf(
                UvcSize(3840, 2160), // 16:9 but 4K → excluded
                UvcSize(1920, 1080),
                UvcSize(1280, 720),
                UvcSize(640, 480),
            ),
        )
        assertEquals(UvcSize(1920, 1080), selected)
    }

    @Test
    fun prefersSixteenNineEvenWhenASmallerAreaThanFourByThree() {
        // 1280×720 (16:9) should beat 1440×1080 (4:3) despite the latter having a larger area.
        val selected = UvcSelection.selectResolution(
            listOf(UvcSize(1440, 1080), UvcSize(1280, 720)),
        )
        assertEquals(UvcSize(1280, 720), selected)
    }

    @Test
    fun fallsBackToLargestNonSixteenNineWhenNoWidescreenAvailable() {
        val selected = UvcSelection.selectResolution(
            listOf(UvcSize(640, 480), UvcSize(1600, 1000), UvcSize(320, 240)),
        )
        assertEquals(UvcSize(1600, 1000), selected)
    }

    @Test
    fun returnsNullWhenEverySizeExceeds1080p() {
        assertNull(UvcSelection.selectResolution(listOf(UvcSize(3840, 2160), UvcSize(4096, 2160))))
    }

    @Test
    fun rejectsEmptyAndDegenerateInput() {
        assertNull(UvcSelection.selectResolution(emptyList()))
        assertNull(UvcSelection.selectResolution(listOf(UvcSize(0, 0))))
    }

    @Test
    fun identifiesOnlyTheVideoClassInterfaceAsUvc() {
        // Pocket 4P host composite from docs/09: RNDIS control (0xE0), data (0x0A),
        // mass storage (0x08), vendor bulk (0xFF). None of those is UVC.
        assertFalse(UvcSelection.isVideoInterface(0xE0, 1, 3))
        assertFalse(UvcSelection.isVideoInterface(0x0A, 0, 0))
        assertFalse(UvcSelection.isVideoInterface(0x08, 6, 0x50))
        assertFalse(UvcSelection.isVideoInterface(0xFF, 0x43, 1))
        assertTrue(UvcSelection.isVideoInterface(0x0E, 1, 0)) // VideoControl
        assertTrue(UvcSelection.isVideoInterface(0x0E, 2, 0)) // VideoStreaming
    }

    @Test
    fun hasVideoInterfaceSeesAUvcInterfaceAmongOtherInterfaces() {
        val composite = listOf(
            UsbInterfaceSpec(0xE0, 1, 3), // RNDIS control
            UsbInterfaceSpec(0x0A, 0, 0), // RNDIS data
            UsbInterfaceSpec(0x08, 6, 0x50), // MSC
            UsbInterfaceSpec(0xFF, 0x43, 1), // vendor bulk
        )
        assertFalse(UvcSelection.hasVideoInterface(composite))
        assertTrue(UvcSelection.hasVideoInterface(composite + UsbInterfaceSpec(0x0E, 2, 0)))
    }

    @Test
    fun sixteenNineToleranceAcceptsStandardSizesAndRejectsFourByThree() {
        assertTrue(UvcSelection.isSixteenNine(1920, 1080))
        assertTrue(UvcSelection.isSixteenNine(1280, 720))
        assertTrue(UvcSelection.isSixteenNine(1600, 900))
        assertFalse(UvcSelection.isSixteenNine(640, 480))
        assertFalse(UvcSelection.isSixteenNine(1440, 1080))
    }
}
