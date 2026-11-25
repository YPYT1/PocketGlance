package com.opencapture.pocketglance.gallery

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaKindClassifierTest {
    @Test
    fun classifiesCommonExtensions() {
        assertEquals(MediaKind.Image, MediaKindClassifier.classify("DSC_001.jpg"))
        assertEquals(MediaKind.Video, MediaKindClassifier.classify("clip.mp4"))
        assertEquals(MediaKind.Motion, MediaKindClassifier.classify("anim.gif"))
        assertEquals(MediaKind.Motion, MediaKindClassifier.classify("shot.dng", motionFlag = true))
        assertEquals(MediaKind.Video, MediaKindClassifier.classify("proxy.lrf", mime = "video/mp4"))
    }
}
