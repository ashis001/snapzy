@file:OptIn(ExperimentalForeignApi::class)

package com.snapzy.app.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.duration
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter.Companion.imagesFilter
import platform.PhotosUI.PHPickerFilter.Companion.videosFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = this.length.toInt()
    val byteArray = ByteArray(size)
    if (size > 0) {
        byteArray.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
    return byteArray
}

private class ImagePickerDelegate(
    private val onPicked: (ByteArray?) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        val provider = result?.itemProvider
        if (provider == null || !provider.hasItemConformingToTypeIdentifier(UTTypeImage.identifier)) {
            onPicked(null)
            return
        }
        provider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, _ ->
            val bytes = data?.toByteArray()
            dispatch_async(dispatch_get_main_queue()) { onPicked(bytes) }
        }
    }
}

private class VideoPickerDelegate(
    private val onPicked: (String?, Long) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        val provider = result?.itemProvider
        if (provider == null || !provider.hasItemConformingToTypeIdentifier(UTTypeMovie.identifier)) {
            onPicked(null, 0L)
            return
        }
        provider.loadFileRepresentationForTypeIdentifier(UTTypeMovie.identifier) { url, _ ->
            if (url == null) {
                dispatch_async(dispatch_get_main_queue()) { onPicked(null, 0L) }
                return@loadFileRepresentationForTypeIdentifier
            }
            val destPath = NSTemporaryDirectory() + NSUUID().UUIDString() + ".mov"
            val destUrl = NSURL.fileURLWithPath(destPath)
            NSFileManager.defaultManager.copyItemAtURL(url, toURL = destUrl, error = null)
            val asset = AVURLAsset(destUrl, options = null)
            val durationMs = (CMTimeGetSeconds(asset.duration) * 1000).toLong()
            dispatch_async(dispatch_get_main_queue()) { onPicked(destPath, durationMs) }
        }
    }
}

@Composable
actual fun rememberImagePicker(onResult: (PickedMedia.Image?) -> Unit): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    val delegate = remember {
        ImagePickerDelegate { bytes -> currentOnResult.value(bytes?.let { PickedMedia.Image(it) }) }
    }
    return remember {
        {
            val config = PHPickerConfiguration()
            config.filter = imagesFilter()
            config.selectionLimit = 1L
            val controller = PHPickerViewController(configuration = config)
            controller.delegate = delegate
            presentFromTopViewController(controller)
        }
    }
}

@Composable
actual fun rememberVideoPicker(onResult: (PickedMedia.Video?) -> Unit): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    val delegate = remember {
        VideoPickerDelegate { path, duration ->
            currentOnResult.value(path?.let { PickedMedia.Video(it, duration) })
        }
    }
    return remember {
        {
            val config = PHPickerConfiguration()
            config.filter = videosFilter()
            config.selectionLimit = 1L
            val controller = PHPickerViewController(configuration = config)
            controller.delegate = delegate
            presentFromTopViewController(controller)
        }
    }
}
