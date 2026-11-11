package com.movexa.app.core.media

import android.content.ContentValues
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

private const val FILE_PROVIDER_AUTHORITY = "com.movexa.app.fileprovider"

actual fun saveImageToGallery(bytes: ByteArray, displayName: String, onComplete: (Boolean) -> Unit) {
    val context = AndroidAppContext.context
    val success = try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Movexa")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
    onComplete(success)
}

actual fun saveVideoToGallery(filePath: String, displayName: String, onComplete: (Boolean) -> Unit) {
    val context = AndroidAppContext.context
    val success = try {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Movexa")
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                File(filePath).inputStream().use { input -> input.copyTo(out) }
            }
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
    onComplete(success)
}

actual fun shareImageFile(bytes: ByteArray, displayName: String) {
    val context = AndroidAppContext.context
    val cacheFile = File(context.cacheDir, displayName)
    cacheFile.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, cacheFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

actual fun shareVideoFile(filePath: String) {
    val context = AndroidAppContext.context
    val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, File(filePath))
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
