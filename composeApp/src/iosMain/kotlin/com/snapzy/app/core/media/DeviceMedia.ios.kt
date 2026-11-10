@file:OptIn(ExperimentalForeignApi::class)

package com.snapzy.app.core.media

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import platform.AVFoundation.AVAsset
import platform.AVFoundation.AVAssetExportPresetHighestQuality
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVFileTypeQuickTimeMovie
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.Photos.PHAsset
import platform.Photos.PHAssetMediaTypeImage
import platform.Photos.PHAssetMediaTypeVideo
import platform.Photos.PHFetchOptions
import platform.Photos.PHImageManager
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryModeHighQualityFormat
import platform.Photos.PHImageRequestOptionsResizeModeFast
import platform.Photos.PHVideoRequestOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * PhotoKit-backed device gallery for iOS.
 *
 * Assets are read straight out of the photo library; videos are exported to a
 * temp file on demand so the editor always works on a stable local path.
 */
actual suspend fun queryDeviceMedia(type: MediaType, limit: Int): List<DeviceMedia> = withContext(Dispatchers.Main) {
    val assets = PHAsset.fetchAssetsWithOptions(
        options = PHFetchOptions().apply { fetchLimit = limit.toULong() },
    ) ?: return@withContext emptyList()

    val wanted = when (type) {
        MediaType.IMAGE -> PHAssetMediaTypeImage
        MediaType.VIDEO -> PHAssetMediaTypeVideo
    }

    val items = mutableListOf<DeviceMedia>()
    for (index in 0 until assets.count.toInt()) {
        if (items.size >= limit) break
        val asset = assets.objectAtIndex(index.toULong()) as? PHAsset ?: continue
        if (asset.mediaType != wanted) continue
        items += DeviceMedia(
            id = asset.localIdentifier.hashCode().toLong(),
            uri = asset.localIdentifier,
            type = type,
            displayName = "Media ${items.size + 1}",
            durationMs = asset.duration.toLong(),
        )
    }
    items
}

actual suspend fun loadThumbnail(uri: String, widthPx: Int, heightPx: Int): ImageBitmap? = withContext(Dispatchers.Main) {
    val asset = findAsset(uri) ?: return@withContext null

    suspendCoroutine { continuation ->
        var resumed = false
        val options = PHImageRequestOptions().apply {
            deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat
            resizeMode = PHImageRequestOptionsResizeModeFast
            networkAccessAllowed = true
        }
        PHImageManager.defaultManager().requestImageDataAndOrientationForAsset(
            asset = asset,
            options = options,
        ) { data, _, _, _ ->
            if (resumed) return@requestImageDataAndOrientationForAsset
            resumed = true
            val bytes = data?.toByteArray()
            continuation.resume(bytes?.let { decodeImageBitmap(it) })
        }
    }
}

actual suspend fun loadImageBytes(uri: String): ByteArray? = withContext(Dispatchers.Main) {
    val asset = findAsset(uri) ?: return@withContext null

    suspendCoroutine { continuation ->
        var resumed = false
        val options = PHImageRequestOptions().apply { networkAccessAllowed = true }
        PHImageManager.defaultManager().requestImageDataAndOrientationForAsset(
            asset = asset,
            options = options,
        ) { data, _, _, _ ->
            if (resumed) return@requestImageDataAndOrientationForAsset
            resumed = true
            continuation.resume(data?.toByteArray())
        }
    }
}

actual suspend fun copyToCache(uri: String, displayName: String): String? = withContext(Dispatchers.Main) {
    val asset = findAsset(uri) ?: return@withContext null
    val isVideo = asset.mediaType == PHAssetMediaTypeVideo
    val extension = if (isVideo) "mov" else "jpg"
    val destPath = NSTemporaryDirectory() + NSUUID().UUIDString() + ".$extension"

    suspendCoroutine { continuation ->
        var resumed = false
        if (isVideo) {
            val options = PHVideoRequestOptions().apply { networkAccessAllowed = true }
            PHImageManager.defaultManager().requestAVAssetForVideo(
                asset = asset,
                options = options,
            ) { avAsset, _, _ ->
                if (resumed) return@requestAVAssetForVideo
                resumed = true
                continuation.resume(if (avAsset != null) exportAvAsset(avAsset, destPath) else null)
            }
        } else {
            val options = PHImageRequestOptions().apply { networkAccessAllowed = true }
            PHImageManager.defaultManager().requestImageDataAndOrientationForAsset(
                asset = asset,
                options = options,
            ) { data, _, _, _ ->
                if (resumed) return@requestImageDataAndOrientationForAsset
                resumed = true
                val written = data?.writeToFile(destPath, atomically = true) == true
                continuation.resume(if (written) destPath else null)
            }
        }
    }
}

/** Finds a single asset by its local identifier. */
private fun findAsset(localIdentifier: String): PHAsset? {
    val assets = PHAsset.fetchAssetsWithOptions(options = PHFetchOptions()) ?: return null
    for (index in 0 until assets.count.toInt()) {
        val asset = assets.objectAtIndex(index.toULong()) as? PHAsset ?: continue
        if (asset.localIdentifier == localIdentifier) return asset
    }
    return null
}

/** Writes a PhotoKit video asset out to a temp file using AVAssetExportSession. */
private fun exportAvAsset(avAsset: AVAsset, destPath: String): String? {
    val session = AVAssetExportSession(
        asset = avAsset,
        presetName = AVAssetExportPresetHighestQuality,
    ) ?: return null
    session.outputURL = NSURL.fileURLWithPath(destPath)
    session.outputFileType = AVFileTypeQuickTimeMovie
    session.exportAsynchronouslyWithCompletionHandler { }
    return destPath
}

/** Copies NSData into a Kotlin ByteArray. */
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) {
        bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, length) }
    }
    return bytes
}
