package com.snapzy.app.core.media

/** Save a JPEG image into the device's photo library / gallery. */
expect fun saveImageToGallery(bytes: ByteArray, displayName: String, onComplete: (Boolean) -> Unit)

/** Save a video file (by local path) into the device's photo library / gallery. */
expect fun saveVideoToGallery(filePath: String, displayName: String, onComplete: (Boolean) -> Unit)

/** Open the OS share sheet for a JPEG image. */
expect fun shareImageFile(bytes: ByteArray, displayName: String)

/** Open the OS share sheet for a video file (by local path). */
expect fun shareVideoFile(filePath: String)
