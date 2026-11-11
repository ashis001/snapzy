package com.movexa.app.core.media

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * Uses ACTION_GET_CONTENT (DocumentsUI, built into AOSP) rather than the
 * Android Photo Picker (ActivityResultContracts.PickVisualMedia): the Photo
 * Picker depends on a Play services / Mainline module that some system
 * images (including the emulator image this app was tested on) don't ship,
 * which crashes with ActivityNotFoundException. GetContent has no such
 * dependency and needs no runtime storage permission either.
 */
@Composable
actual fun rememberImagePicker(onResult: (PickedMedia.Image?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) {
            currentOnResult.value(null)
            return@rememberLauncherForActivityResult
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        currentOnResult.value(bytes?.let { PickedMedia.Image(it) })
    }
    return remember { { launcher.launch("image/*") } }
}

@Composable
actual fun rememberVideoPicker(onResult: (PickedMedia.Video?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) {
            currentOnResult.value(null)
            return@rememberLauncherForActivityResult
        }
        val tempFile = File(context.cacheDir, "snapzy_picked_${System.currentTimeMillis()}.mp4")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        val retriever = MediaMetadataRetriever()
        val durationMs = try {
            retriever.setDataSource(tempFile.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
        currentOnResult.value(PickedMedia.Video(tempFile.absolutePath, durationMs))
    }
    return remember { { launcher.launch("video/*") } }
}
