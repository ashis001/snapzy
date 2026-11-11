package com.movexa.app.core.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings
import com.movexa.app.core.editing.Overlay
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A picture-in-picture layer, drawn from a sequence of frames.
 *
 * Media3 1.5 cannot composite a second *video* into an export, so the layer
 * is rendered as a series of stills instead: the clip's frames are pulled out
 * ahead of the render and handed to Media3 one per frame. The result is a
 * moving picture-in-picture that survives into the finished file.
 */
internal class PipFrameOverlay(
    private val frames: List<Bitmap>,
    /** How long each extracted frame is held on screen, in milliseconds. */
    private val frameIntervalMs: Long,
    /** When the layer appears on the timeline, in milliseconds. */
    private val startMs: Long,
    /** When the layer disappears; `null` means it runs to the end. */
    private val endMs: Long?,
    private val base: OverlaySettings,
) : BitmapOverlay() {

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        if (frames.isEmpty()) return TRANSPARENT
        val elapsed = presentationTimeUs / 1000 - startMs
        if (elapsed < 0) return TRANSPARENT
        val index = (elapsed / frameIntervalMs).toInt()
        return frames[index.coerceIn(0, frames.lastIndex)]
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val elapsed = presentationTimeUs / 1000 - startMs
        val visible = elapsed >= 0 && (endMs == null || elapsed < endMs)
        return base.withAlpha(if (visible) base.alphaScale else 0f)
    }

    private companion object {
        val TRANSPARENT: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}

/**
 * Pulls the frames of a picture-in-picture clip out of the source file.
 *
 * The frames are scaled to the size the layer occupies on the canvas, so the
 * export never holds a full-resolution copy of the clip in memory.
 */
internal fun extractPipFrames(
    path: String,
    sourceStartMs: Long,
    sourceEndMs: Long,
    scale: Float,
    framesPerSecond: Int = 12,
    maxFrames: Int = 96,
): Pair<List<Bitmap>, Long> {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(path)
        val rawDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val durationMs = rawDuration?.toLongOrNull() ?: 0L
        if (durationMs <= 0L) return emptyList<Bitmap>() to 0L

        // The frames are cut at the size the layer occupies inside its own
        // clip, so the overlay needs no further scaling.
        val videoWidth = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0
        val videoHeight = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0
        val boxWidth = (videoWidth * scale).roundToInt().coerceAtLeast(16)
        val boxHeight = (videoHeight * scale).roundToInt().coerceAtLeast(16)

        val from = sourceStartMs.coerceIn(0L, durationMs)
        val to = sourceEndMs.coerceIn(from, durationMs)
        val window = (to - from).coerceAtLeast(1L)

        val interval = max(1L, 1000L / framesPerSecond)
        val count = minOf(maxFrames.toLong(), (window / interval).coerceAtLeast(1L))

        val frames = mutableListOf<Bitmap>()
        for (index in 0 until count) {
            val timeUs = (from + index * interval) * 1000L
            val bitmap = retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST,
                max(1, boxWidth),
                max(1, boxHeight),
            )
            if (bitmap != null) frames += bitmap
        }
        return frames to interval
    } catch (error: Throwable) {
        return emptyList<Bitmap>() to 0L
    } finally {
        runCatching { retriever.release() }
    }
}

/**
 * The settings that place a picture-in-picture layer on the canvas.
 *
 * The extracted frames are already the size of the layer's box, so the scale
 * is the user's own fraction of the canvas.
 */
internal fun pipOverlaySettings(pip: Overlay.Pip): OverlaySettings = OverlaySettings.Builder()
    .setAlphaScale(pip.opacity.coerceIn(0f, 1f))
    .setBackgroundFrameAnchor(
        (pip.position.x * 2f - 1f).coerceIn(-1f, 1f),
        (1f - pip.position.y * 2f).coerceIn(-1f, 1f),
    )
    .setOverlayFrameAnchor(0f, 0f)
    // The frames are already extracted at the size the layer occupies, so the
    // scale stays at one.
    .setScale(1f, 1f)
    .setRotationDegrees(pip.rotation)
    .build()


