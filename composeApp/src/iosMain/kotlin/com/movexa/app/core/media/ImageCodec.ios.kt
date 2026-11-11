package com.movexa.app.core.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap {
    val skiaImage = Image.makeFromEncoded(bytes)
    return skiaImage.toComposeImageBitmap()
}

actual fun encodeImageBitmapToJpeg(bitmap: ImageBitmap, quality: Int): ByteArray {
    val skiaBitmap = bitmap.asSkiaBitmap()
    val skiaImage = Image.makeFromBitmap(skiaBitmap)
    val data = skiaImage.encodeToData(EncodedImageFormat.JPEG, quality)
        ?: error("Unable to encode image")
    return data.bytes
}
