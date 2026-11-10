@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.snapzy.app.core.media

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

actual fun trimAndExportVideo(
    sourcePath: String,
    startMs: Long,
    endMs: Long,
    muted: Boolean,
    onComplete: (outputPath: String?) -> Unit,
) {
    val context = AndroidAppContext.context
    val outputFile = File(context.cacheDir, "snapzy_export_${System.currentTimeMillis()}.mp4")

    val mediaItem = MediaItem.Builder()
        .setUri(Uri.fromFile(File(sourcePath)))
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build()
        )
        .build()

    val editedMediaItem = EditedMediaItem.Builder(mediaItem)
        .setRemoveAudio(muted)
        .build()

    val transformer = Transformer.Builder(context)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                onComplete(outputFile.absolutePath)
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                onComplete(null)
            }
        })
        .build()

    transformer.start(editedMediaItem, outputFile.absolutePath)
}
