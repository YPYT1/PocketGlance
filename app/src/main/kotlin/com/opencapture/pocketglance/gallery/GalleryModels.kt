package com.opencapture.pocketglance.gallery

enum class MediaKind {
    Image,
    Video,
    Motion,
}

data class GalleryItem(
    val id: String,
    val title: String,
    val kind: MediaKind,
    val thumbHint: String = "",
)

object MediaKindClassifier {
    fun classify(fileName: String, mime: String? = null, motionFlag: Boolean = false): MediaKind {
        if (motionFlag) return MediaKind.Motion
        val lower = fileName.lowercase()
        val mimeLower = mime?.lowercase().orEmpty()
        if (mimeLower.contains("gif") || lower.endsWith(".gif") ||
            lower.contains("live") || lower.contains("motion") ||
            mimeLower.contains("image/webp") && lower.contains("anim")
        ) {
            return MediaKind.Motion
        }
        if (mimeLower.startsWith("video/") ||
            lower.endsWith(".mp4") || lower.endsWith(".mov") ||
            lower.endsWith(".lrf") || lower.endsWith(".xrf") || lower.endsWith(".m4v")
        ) {
            return MediaKind.Video
        }
        return MediaKind.Image
    }
}
