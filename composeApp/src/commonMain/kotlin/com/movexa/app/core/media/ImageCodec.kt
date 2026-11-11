package com.movexa.app.core.media

import androidx.compose.ui.graphics.ImageBitmap

/** Decode encoded image bytes (JPEG/PNG) into a renderable, editable bitmap. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap

/** Encode a composed bitmap back to JPEG bytes for saving/export. */
expect fun encodeImageBitmapToJpeg(bitmap: ImageBitmap, quality: Int = 92): ByteArray
