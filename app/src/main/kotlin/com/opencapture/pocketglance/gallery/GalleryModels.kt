package com.opencapture.pocketglance.gallery

import com.opencapture.opencine.media.MediaAssetKind
import com.opencapture.opencine.vocabulary.MediaFile
import com.opencapture.opencine.vocabulary.MediaHTTP
import com.opencapture.opencine.vocabulary.MediaListCommand

enum class MediaKind {
    Image,
    Video,
    Motion,
}

enum class GalleryStorage(val label: String) {
    Internal("机身内存"),
    Card("存储卡"),
}

internal data class GalleryItem(
    val id: String,
    val title: String,
    val kind: MediaKind,
    val storage: GalleryStorage,
    val file: MediaFile,
) {
    fun url(path: String): String = MediaHTTP.pathUrlString(
        if (storage == GalleryStorage.Internal) MediaListCommand.INTERNAL_STORAGE else MediaListCommand.SD_STORAGE,
        android.net.Uri.encode(path, "/"),
    )

    companion object {
        fun from(id: String, file: MediaFile, kind: MediaAssetKind?): GalleryItem? {
            val type = when (kind) {
                MediaAssetKind.VIDEO -> MediaKind.Video
                MediaAssetKind.PHOTO -> MediaKind.Image
                null -> return null
            }
            val storage = when (MediaListCommand.storeOf(file)) {
                MediaListCommand.INTERNAL_STORAGE -> GalleryStorage.Internal
                MediaListCommand.SD_STORAGE -> GalleryStorage.Card
                else -> return null
            }
            return GalleryItem(id, file.filename, type, storage, file)
        }
    }
}

internal data class GalleryState(
    val items: List<GalleryItem> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    val message: String? = null,
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
