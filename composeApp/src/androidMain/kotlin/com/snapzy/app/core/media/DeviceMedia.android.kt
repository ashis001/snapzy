package com.snapzy.app.core.media

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MediaStore-backed device gallery.
 *
 * `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` are runtime permissions on API 33+.
 * When they are not granted the query returns an empty list, which the picker
 * renders as its empty state instead of crashing.
 */
private fun Context.hasMediaPermission(type: MediaType): Boolean {
    val permission = if (type == MediaType.IMAGE) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_MEDIA_VIDEO
    }
    return androidx.core.content.ContextCompat.checkSelfPermission(this, permission) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
}

actual suspend fun queryDeviceMedia(type: MediaType, limit: Int): List<DeviceMedia> = withContext(Dispatchers.IO) {
    val context = AndroidAppContext.context
    if (!context.hasMediaPermission(type)) return@withContext emptyList()

    val collection = when (type) {
        MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    val projection = mutableListOf<String>().apply {
        add(MediaStore.MediaColumns._ID)
        add(MediaStore.MediaColumns.DISPLAY_NAME)
        add(MediaStore.MediaColumns.DATE_ADDED)
        add(MediaStore.MediaColumns.SIZE)
        if (type == MediaType.VIDEO) add(MediaStore.Video.VideoColumns.DURATION)
    }

    val items = try {
        val collected = mutableListOf<DeviceMedia>()
        context.contentResolver.query(
            collection,
            projection.toTypedArray(),
            null,
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val durationCol = if (type == MediaType.VIDEO) {
                cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
            } else -1

            while (cursor.moveToNext() && collected.size < limit) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                collected += DeviceMedia(
                    id = id,
                    uri = uri.toString(),
                    type = type,
                    displayName = cursor.getString(nameCol) ?: "media",
                    durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L,
                    dateAddedMs = cursor.getLong(dateCol) * 1000L,
                    sizeBytes = cursor.getLong(sizeCol),
                )
            }
        }
        collected
    } catch (e: Exception) {
        emptyList()
    }

    items
}

actual suspend fun loadThumbnail(uri: String, widthPx: Int, heightPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    val context = AndroidAppContext.context
    try {
        val parsed = Uri.parse(uri)
        val resolver = context.contentResolver

        if (parsed.scheme == "file") {
            val retriever = MediaMetadataRetriever()
            val bitmap = try {
                retriever.setDataSource(parsed.path ?: return@withContext null)
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                retriever.release()
            }
            return@withContext bitmap?.scaled(widthPx, heightPx)?.asImageBitmap()
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > widthPx * 2 || bounds.outHeight / sample > heightPx * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, options) }
        bitmap?.scaled(widthPx, heightPx)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

actual suspend fun loadImageBytes(uri: String): ByteArray? = withContext(Dispatchers.IO) {
    val context = AndroidAppContext.context
    try {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") {
            File(parsed.path ?: return@withContext null).readBytes()
        } else {
            context.contentResolver.openInputStream(parsed)?.use { it.readBytes() }
        }
    } catch (e: Exception) {
        null
    }
}

actual suspend fun copyToCache(uri: String, displayName: String): String? = withContext(Dispatchers.IO) {
    val context = AndroidAppContext.context
    try {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") {
            val source = File(parsed.path ?: return@withContext null)
            val dest = File(context.cacheDir, "snapzy_${System.currentTimeMillis()}_$displayName")
            source.copyTo(dest, overwrite = true)
            return@withContext dest.absolutePath
        }
        val dest = File(context.cacheDir, "snapzy_${System.currentTimeMillis()}_$displayName")
        context.contentResolver.openInputStream(parsed)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.absolutePath
    } catch (e: Exception) {
        null
    }
}

/** Centre-crops and scales a bitmap to fit the requested box. */
private fun Bitmap.scaled(widthPx: Int, heightPx: Int): Bitmap {
    if (widthPx <= 0 || heightPx <= 0) return this
    val scale = maxOf(widthPx.toFloat() / width, heightPx.toFloat() / height)
    val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
    val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
    val x = ((scaledWidth - widthPx) / 2).coerceAtLeast(0)
    val y = ((scaledHeight - heightPx) / 2).coerceAtLeast(0)
    return Bitmap.createBitmap(scaled, x, y, widthPx, heightPx)
}
