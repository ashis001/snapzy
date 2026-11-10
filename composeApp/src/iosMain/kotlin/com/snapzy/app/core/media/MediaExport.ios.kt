@file:OptIn(ExperimentalForeignApi::class)

package com.snapzy.app.core.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIActivityViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

private fun writeBytesToTempFile(bytes: ByteArray, extension: String): String {
    val path = NSTemporaryDirectory() + NSUUID().UUIDString() + "." + extension
    val file = fopen(path, "wb") ?: error("Unable to open temp file for write: $path")
    if (bytes.isNotEmpty()) {
        bytes.usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1u, bytes.size.convert(), file)
        }
    }
    fclose(file)
    return path
}

actual fun saveImageToGallery(bytes: ByteArray, displayName: String, onComplete: (Boolean) -> Unit) {
    val url = NSURL.fileURLWithPath(writeBytesToTempFile(bytes, "jpg"))
    PHPhotoLibrary.sharedPhotoLibrary().performChanges(
        changeBlock = { PHAssetChangeRequest.creationRequestForAssetFromImageAtFileURL(url) },
        completionHandler = { success, _ ->
            dispatch_async(dispatch_get_main_queue()) { onComplete(success) }
        },
    )
}

actual fun saveVideoToGallery(filePath: String, displayName: String, onComplete: (Boolean) -> Unit) {
    val url = NSURL.fileURLWithPath(filePath)
    PHPhotoLibrary.sharedPhotoLibrary().performChanges(
        changeBlock = { PHAssetChangeRequest.creationRequestForAssetFromVideoAtFileURL(url) },
        completionHandler = { success, _ ->
            dispatch_async(dispatch_get_main_queue()) { onComplete(success) }
        },
    )
}

actual fun shareImageFile(bytes: ByteArray, displayName: String) {
    val url = NSURL.fileURLWithPath(writeBytesToTempFile(bytes, "jpg"))
    val activityVC = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
    presentFromTopViewController(activityVC)
}

actual fun shareVideoFile(filePath: String) {
    val url = NSURL.fileURLWithPath(filePath)
    val activityVC = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
    presentFromTopViewController(activityVC)
}
