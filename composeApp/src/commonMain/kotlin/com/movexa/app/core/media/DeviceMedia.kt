package com.movexa.app.core.media

import androidx.compose.ui.graphics.ImageBitmap

/** What kind of media a gallery item is. */
enum class MediaType { IMAGE, VIDEO }

/** One item in the device gallery. */
data class DeviceMedia(
    val id: Long,
    /** `content://` URI on Android, `ph://` / file URL on iOS. */
    val uri: String,
    val type: MediaType,
    val displayName: String,
    val durationMs: Long = 0L,
    val dateAddedMs: Long = 0L,
    val sizeBytes: Long = 0L,
)

/**
 * Reads the device gallery, newest first.
 *
 * Implemented over MediaStore on Android and the PhotoKit asset collection on
 * iOS. Falls back to an empty list rather than throwing when the OS denies
 * access, so the picker can show its empty state.
 */
expect suspend fun queryDeviceMedia(type: MediaType, limit: Int = 300): List<DeviceMedia>

/**
 * Loads a thumbnail for a gallery cell, downscaled to fit [widthPx] x [heightPx].
 * Returns `null` when the item cannot be decoded.
 */
expect suspend fun loadThumbnail(uri: String, widthPx: Int, heightPx: Int): ImageBitmap?

/**
 * Reads the *original* bytes of a gallery item.
 *
 * The picker uses this to hand the editor a full-resolution file rather than
 * a re-encoded thumbnail, which would crop and compress the user's photo.
 */
expect suspend fun loadImageBytes(uri: String): ByteArray?

/**
 * Copies a gallery item into the app's cache directory and returns the local
 * file path, so the editor works on a stable path it can read repeatedly.
 */
expect suspend fun copyToCache(uri: String, displayName: String): String?
