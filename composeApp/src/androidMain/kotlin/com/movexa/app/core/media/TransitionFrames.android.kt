package com.movexa.app.core.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings
import com.movexa.app.core.editing.TransitionKind
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Real transitions between two clips.
 *
 * Media3 1.5 has no transition primitive and its video compositor does not run
 * in the export path, so a transition is drawn as an overlay: the incoming
 * clip's opening is cut into stills and laid over the outgoing clip's tail,
 * while the incoming clip's own playback starts after them.
 *
 * That is how the editor's dissolve, wipe, slide and zoom reach the finished
 * file.
 */
internal class TransitionFrameOverlay(
    private val frames: List<Bitmap>,
    /** When the transition starts on the composition's timeline. */
    private val startUs: Long,
    private val endUs: Long,
    /** The layer's opacity at a given point through the transition. */
    private val alphaAt: (Float) -> Float,
    /** Where the layer sits at a given point through the transition. */
    private val anchorAt: (Float) -> Pair<Float, Float> = { ZERO_ANCHOR },
    /** The layer's size at a given point through the transition. */
    private val scaleAt: (Float) -> Pair<Float, Float> = { UNIT_SCALE },
) : BitmapOverlay() {

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        if (frames.isEmpty()) return TRANSPARENT
        if (presentationTimeUs < startUs || presentationTimeUs >= endUs) return TRANSPARENT
        val index = (progress(presentationTimeUs) * frames.size).toInt()
        return frames[index.coerceIn(0, frames.lastIndex)]
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        if (presentationTimeUs < startUs || presentationTimeUs >= endUs) {
            return baseSettings.withAlpha(0f)
        }
        val t = progress(presentationTimeUs)
        val anchor = anchorAt(t)
        val size = scaleAt(t)
        return baseSettings
            .withAlpha(alphaAt(t))
            .withAnchor(anchor.first, anchor.second)
            .withScale(size.first, size.second)
    }

    private val baseSettings = OverlaySettings.Builder()
        .setBackgroundFrameAnchor(0f, 0f)
        .setOverlayFrameAnchor(0f, 0f)
        .setScale(1f, 1f)
        .build()

    private fun progress(presentationTimeUs: Long): Float {
        val span = (endUs - startUs).toFloat()
        if (span <= 0f) return 1f
        return ((presentationTimeUs - startUs).toFloat() / span).coerceIn(0f, 1f)
    }

    private companion object {
        val TRANSPARENT: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val ZERO_ANCHOR = 0f to 0f
        val UNIT_SCALE = 1f to 1f
    }
}

/**
 * Cuts the frames of a clip's opening into stills.
 *
 * [width] and [height] are the size each frame is scaled to; a transition
 * that slides needs a frame wider than the canvas.
 */
internal fun extractTransitionFrames(
    path: String,
    sourceStartMs: Long,
    sourceEndMs: Long,
    width: Int,
    height: Int,
    kind: TransitionKind,
): List<Bitmap> {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(path)
        val span = (sourceEndMs - sourceStartMs).coerceAtLeast(1L)
        val count = TRANSITION_FRAME_COUNT
        val frames = mutableListOf<Bitmap>()

        for (index in 0 until count) {
            val progress = index.toFloat() / (count - 1).coerceAtLeast(1).toFloat()
            val timeUs = (sourceStartMs + (span * progress).toLong()) * 1000L
            val bitmap = retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST,
                max(1, width),
                max(1, height),
            ) ?: continue

            frames += if (kind == TransitionKind.WIPE) {
                wipeEdge(bitmap, progress)
            } else {
                bitmap
            }
        }
        return frames
    } catch (error: Throwable) {
        return emptyList()
    } finally {
        runCatching { retriever.release() }
    }
}

/**
 * Masks a frame so only the part behind a moving edge shows.
 *
 * The edge sweeps from the left to the right, which is what a wipe looks like.
 */
private fun wipeEdge(bitmap: Bitmap, progress: Float): Bitmap {
    val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val edge = (bitmap.width * progress).roundToInt()
    canvas.save()
    canvas.clipRect(0, 0, edge, bitmap.height)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    canvas.restore()
    return output
}

/** How many stills a transition is cut into. */
private const val TRANSITION_FRAME_COUNT = 10

/**
 * The size the transition frames are cut at.
 *
 * A wipe and a dissolve are covered by the canvas itself; a slide needs a
 * frame wider than the canvas so it can move in from the side.
 */
internal fun transitionFrameSize(
    kind: TransitionKind,
    canvasWidth: Int,
    canvasHeight: Int,
): Pair<Int, Int> {
    val scale = if (kind == TransitionKind.SLIDE) 1.4f else 1f
    return (canvasWidth * scale).roundToInt().coerceAtLeast(16) to
        (canvasHeight * scale).roundToInt().coerceAtLeast(16)
}

/** Copies the alpha of an overlay setting. */
internal fun OverlaySettings.withAlpha(alpha: Float): OverlaySettings = OverlaySettings.Builder()
    .setAlphaScale(alpha.coerceIn(0f, 1f))
    .setBackgroundFrameAnchor(backgroundFrameAnchor.first, backgroundFrameAnchor.second)
    .setOverlayFrameAnchor(overlayFrameAnchor.first, overlayFrameAnchor.second)
    .setScale(scale.first, scale.second)
    .setRotationDegrees(rotationDegrees)
    .build()

/** Copies the anchor of an overlay setting. */
internal fun OverlaySettings.withAnchor(x: Float, y: Float): OverlaySettings = OverlaySettings.Builder()
    .setAlphaScale(alphaScale)
    .setBackgroundFrameAnchor(x.coerceIn(-1f, 1f), y.coerceIn(-1f, 1f))
    .setOverlayFrameAnchor(overlayFrameAnchor.first, overlayFrameAnchor.second)
    .setScale(scale.first, scale.second)
    .setRotationDegrees(rotationDegrees)
    .build()

/** Copies the size of an overlay setting. */
internal fun OverlaySettings.withScale(x: Float, y: Float): OverlaySettings = OverlaySettings.Builder()
    .setAlphaScale(alphaScale)
    .setBackgroundFrameAnchor(backgroundFrameAnchor.first, backgroundFrameAnchor.second)
    .setOverlayFrameAnchor(overlayFrameAnchor.first, overlayFrameAnchor.second)
    .setScale(x.coerceIn(0.05f, 8f), y.coerceIn(0.05f, 8f))
    .setRotationDegrees(rotationDegrees)
    .build()
