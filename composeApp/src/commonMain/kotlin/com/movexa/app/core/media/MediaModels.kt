package com.movexa.app.core.media

/** Result of a system media pick. Video keeps its bytes on disk rather than in memory. */
sealed class PickedMedia {
    data class Image(val bytes: ByteArray) : PickedMedia()
    data class Video(val filePath: String, val durationMs: Long) : PickedMedia()
}
