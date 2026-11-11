@file:OptIn(ExperimentalForeignApi::class)

package com.movexa.app.core.media

import com.movexa.app.core.editing.EditProject
import com.movexa.app.core.editing.TransitionKind
import com.movexa.app.core.editing.VideoClip
import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.naturalSize
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.tracksWithMediaType
import platform.CoreGraphics.CGAffineTransform
import platform.CoreGraphics.CGAffineTransformMake
import platform.Foundation.NSURL
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Geometry helpers for the iOS render pipeline.
 *
 * The maths here is the Core Graphics convention: a point is a column vector,
 * `p' = M · p`, and matrices compose right-to-left (`a.then(b)` applies `b`
 * first). The transform chain that takes a decoded frame to the canvas was
 * validated against Core Image on a rotated sample video.
 */
internal data class Affine(
    val a: Double,
    val b: Double,
    val c: Double,
    val d: Double,
    val tx: Double,
    val ty: Double,
) {
    /** `this ∘ other` — `other` is applied to the point first. */
    fun then(other: Affine): Affine = Affine(
        a = a * other.a + c * other.b,
        b = b * other.a + d * other.b,
        c = a * other.c + c * other.d,
        d = b * other.c + d * other.d,
        tx = a * other.tx + c * other.ty + tx,
        ty = b * other.tx + d * other.ty + ty,
    )

    fun apply(x: Double, y: Double): Pair<Double, Double> =
        (a * x + c * y + tx) to (b * x + d * y + ty)

    /** The matrix as Core Graphics wants it. */
    fun toCGAffineTransform(): CValue<CGAffineTransform> =
        CGAffineTransformMake(a, b, c, d, tx, ty)

    companion object {
        val IDENTITY = Affine(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)

        fun translation(x: Double, y: Double) = Affine(1.0, 0.0, 0.0, 1.0, x, y)

        fun scale(sx: Double, sy: Double) = Affine(sx, 0.0, 0.0, sy, 0.0, 0.0)

        fun rotation(radians: Double) = Affine(
            a = kotlin.math.cos(radians),
            b = kotlin.math.sin(radians),
            c = -kotlin.math.sin(radians),
            d = kotlin.math.cos(radians),
            tx = 0.0,
            ty = 0.0,
        )
    }
}

/** The axis-aligned bounds of a rectangle after a transform. */
internal data class TransformedBounds(
    val minX: Double,
    val minY: Double,
    val width: Double,
    val height: Double,
)

/** Bounds of the rectangle `(0, 0, width, height)` once [matrix] is applied. */
internal fun boundsOf(matrix: Affine, width: Double, height: Double): TransformedBounds {
    val corners = listOf(
        matrix.apply(0.0, 0.0),
        matrix.apply(width, 0.0),
        matrix.apply(0.0, height),
        matrix.apply(width, height),
    )
    val minX = corners.minOf { it.first }
    val maxX = corners.maxOf { it.first }
    val minY = corners.minOf { it.second }
    val maxY = corners.maxOf { it.second }
    return TransformedBounds(minX, minY, maxX - minX, maxY - minY)
}

/** The rotation the camera baked into the file, as a plain matrix. */
internal fun AVAssetTrack.preferredTransformValue(): CGAffineTransform =
    preferredTransform.useContents { this }

/** The width of the decoded frame, before the camera's rotation. */
internal fun AVAssetTrack.naturalWidth(): Double = naturalSize.useContents { this.width }

/** The height of the decoded frame, before the camera's rotation. */
internal fun AVAssetTrack.naturalHeight(): Double = naturalSize.useContents { this.height }

/**
 * The size a clip is displayed at once its `preferredTransform` (the rotation
 * the camera baked into the file) has been applied.
 */
internal fun orientedSize(
    naturalWidth: Double,
    naturalHeight: Double,
    preferred: CGAffineTransform,
): Pair<Double, Double> {
    val matrix = Affine.from(preferred)
    val bounds = boundsOf(matrix, naturalWidth, naturalHeight)
    return bounds.width to bounds.height
}

/** Converts a Core Graphics transform into the pipeline's own matrix type. */
internal fun Affine.Companion.from(transform: CGAffineTransform): Affine =
    Affine(transform.a, transform.b, transform.c, transform.d, transform.tx, transform.ty)

/**
 * The transform that takes a decoded frame to its place on the canvas.
 *
 * * the source's own `preferredTransform` is applied first, so a portrait clip
 *   recorded as a landscape buffer ends up upright;
 * * the user's horizontal flip and rotation are applied on top of that, about
 *   the middle of the oriented frame;
 * * finally the frame is scaled to *fit* the canvas (letterboxed, never
 *   cropped) and centred, which is what Media3's `LAYOUT_SCALE_TO_FIT` does on
 *   Android.
 */
internal fun canvasTransform(
    preferred: CGAffineTransform,
    naturalWidth: Double,
    naturalHeight: Double,
    rotationDegrees: Int,
    flipped: Boolean,
    canvasWidth: Double,
    canvasHeight: Double,
): Affine {
    val source = Affine.from(preferred)
    val oriented = boundsOf(source, naturalWidth, naturalHeight)
    val orientedWidth = oriented.width
    val orientedHeight = oriented.height

    // Normalise the oriented frame so its top-left corner sits at the origin.
    var matrix = Affine.translation(-oriented.minX, -oriented.minY).then(source)

    val centerX = orientedWidth / 2.0
    val centerY = orientedHeight / 2.0
    if (flipped) {
        matrix = Affine.translation(centerX, centerY)
            .then(Affine.scale(-1.0, 1.0))
            .then(Affine.translation(-centerX, -centerY))
            .then(matrix)
    }
    if (rotationDegrees % 360 != 0) {
        // Core Image rotates counter-clockwise for positive angles; the editor
        // asks for clockwise degrees.
        val radians = -rotationDegrees.toDouble() * kotlin.math.PI / 180.0
        matrix = Affine.translation(centerX, centerY)
            .then(Affine.rotation(radians))
            .then(Affine.translation(-centerX, -centerY))
            .then(matrix)
    }

    val placed = boundsOf(matrix, naturalWidth, naturalHeight)
    val scale = min(canvasWidth / placed.width, canvasHeight / placed.height)
    val offsetX = (canvasWidth - placed.width * scale) / 2.0
    val offsetY = (canvasHeight - placed.height * scale) / 2.0

    return Affine.translation(offsetX, offsetY)
        .then(Affine.scale(scale, scale))
        .then(Affine.translation(-placed.minX, -placed.minY))
        .then(matrix)
}

/**
 * The transform that places a picture-in-picture clip on the canvas.
 *
 * The PIP keeps its own aspect ratio: its width is a fraction of the canvas
 * width (the overlay's `scale`) and its height follows from that.
 */
internal fun pipTransform(
    preferred: CGAffineTransform,
    naturalWidth: Double,
    naturalHeight: Double,
    rotationDegrees: Double,
    scale: Double,
    canvasWidth: Double,
    canvasHeight: Double,
    centreX: Double,
    centreY: Double,
): Affine {
    val source = Affine.from(preferred)
    val oriented = boundsOf(source, naturalWidth, naturalHeight)
    var matrix = Affine.translation(-oriented.minX, -oriented.minY).then(source)

    val localCentreX = oriented.width / 2.0
    val localCentreY = oriented.height / 2.0
    if (rotationDegrees % 360.0 != 0.0) {
        val radians = -rotationDegrees * kotlin.math.PI / 180.0
        matrix = Affine.translation(localCentreX, localCentreY)
            .then(Affine.rotation(radians))
            .then(Affine.translation(-localCentreX, -localCentreY))
            .then(matrix)
    }

    val placed = boundsOf(matrix, naturalWidth, naturalHeight)
    val targetWidth = canvasWidth * scale.coerceAtLeast(0.01)
    val targetHeight = targetWidth * (placed.height / placed.width)
    val fit = min(targetWidth / placed.width, targetHeight / placed.height)

    val imageCentreX = placed.minX + placed.width / 2.0
    val imageCentreY = placed.minY + placed.height / 2.0
    val targetCentreX = centreX
    val targetCentreY = canvasHeight - centreY

    return Affine.translation(targetCentreX - imageCentreX * fit, targetCentreY - imageCentreY * fit)
        .then(Affine.scale(fit, fit))
        .then(matrix)
}

/* ------------------------------------------------------------------------- */
/*                              Timeline layout                               */
/* ------------------------------------------------------------------------- */

/**
 * Length of a fade-through-black transition.
 *
 * The neighbouring clips overlap by this much, so the transition sits centred
 * on the cut and the finished video is that much shorter than the sum of the
 * clips.
 */
internal const val TRANSITION_DURATION_MS = 400L

/** One clip placed on the timeline. */
internal class TimelineClip(
    val clip: VideoClip,
    val index: Int,
    val startMs: Long,
    val durationMs: Long,
) {
    val endMs: Long get() = startMs + durationMs

    /** Where this clip's normal (non-transition) part ends. */
    var normalEndMs: Long = startMs + durationMs
}

/**
 * A fade-through-black window centred on the boundary between two clips.
 *
 * The first half fades the outgoing clip out, the second half fades the
 * incoming clip in, so the two halves meet on black.
 */
internal class TimelineTransition(
    /** The cut the transition is centred on. */
    val boundaryMs: Long,
    /** First half: the outgoing clip fades out. */
    val startMs: Long,
    /** Second half: the incoming clip fades in. */
    val endMs: Long,
    val outgoingIndex: Int,
    val incomingIndex: Int,
)

/** The whole edit laid out on a timeline. */
internal class RenderTimeline(
    val clips: List<TimelineClip>,
    val transitions: List<TimelineTransition>,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val frameRate: Int,
) {
    val totalDurationMs: Long get() = clips.lastOrNull()?.endMs ?: 0L

    val totalFrames: Int
        get() = if (totalDurationMs <= 0L) 0
        else ((totalDurationMs * frameRate) / 1000L).toInt().coerceAtLeast(1)

    /**
     * The clip visible at [timeMs], plus the fade multiplier that applies.
     *
     * Inside a transition the outgoing clip fades out over the first half and
     * the incoming clip fades in over the second half, so the two meet on
     * black.
     */
    fun visibleAt(timeMs: Long): Pair<TimelineClip, Float>? {
        val transition = transitions.firstOrNull { timeMs >= it.startMs && timeMs < it.endMs }
        if (transition != null) {
            val half = ((transition.endMs - transition.startMs) / 2).coerceAtLeast(1L)
            return if (timeMs < transition.boundaryMs) {
                val clip = clips.getOrNull(transition.outgoingIndex) ?: return null
                val elapsed = timeMs - transition.startMs
                clip to (1f - (elapsed.toFloat() / half.toFloat())).coerceIn(0f, 1f)
            } else {
                val clip = clips.getOrNull(transition.incomingIndex) ?: return null
                val elapsed = timeMs - transition.boundaryMs
                clip to (elapsed.toFloat() / half.toFloat()).coerceIn(0f, 1f)
            }
        }
        val clip = clips.firstOrNull { timeMs >= it.startMs && timeMs < it.normalEndMs }
            ?: clips.lastOrNull()?.takeIf { timeMs >= it.endMs }
            ?: return null
        return clip to 1f
    }
}

/**
 * Lays the clips out on a timeline.
 *
 * `FADE` and `DISSOLVE` overlap the neighbouring clips by half the transition
 * length, so the transition sits centred on the cut and the finished video is
 * that much shorter than the sum of the clips.
 */
internal fun buildTimeline(
    project: EditProject,
    canvasWidth: Int,
    canvasHeight: Int,
    frameRate: Int,
): RenderTimeline {
    val overlap = when (project.transition) {
        TransitionKind.FADE, TransitionKind.DISSOLVE -> TRANSITION_DURATION_MS / 2
        else -> 0L
    }

    val clips = mutableListOf<TimelineClip>()
    val transitions = mutableListOf<TimelineTransition>()
    var cursor = 0L
    project.clips.forEachIndexed { index, clip ->
        val duration = clip.timelineDurationMs.coerceAtLeast(1L)
        if (index > 0 && overlap > 0L) {
            // The incoming clip starts `overlap` earlier, so the transition
            // window is the overlap and its centre is the cut.
            transitions += TimelineTransition(
                boundaryMs = cursor - overlap / 2,
                startMs = cursor - overlap,
                endMs = cursor,
                outgoingIndex = index - 1,
                incomingIndex = index,
            )
            cursor -= overlap
        }
        clips += TimelineClip(clip, index, cursor, duration)
        cursor += duration
    }

    // A clip's normal part ends where the next clip's transition starts.
    clips.forEachIndexed { index, clip ->
        clip.normalEndMs = if (index < clips.size - 1) clips[index + 1].startMs else clip.endMs
    }

    return RenderTimeline(clips, transitions, canvasWidth, canvasHeight, frameRate)
}

/** The source of one clip: its track and where it goes on the canvas. */
internal class ClipSource(
    val track: AVAssetTrack?,
    /** The transform the video composition's layer instruction uses. */
    val layerTransform: Affine,
)

/**
 * The transform an `AVVideoComposition` layer instruction needs.
 *
 * The video composition works in a top-left origin, y-down space while Core
 * Image works bottom-left origin, y-up, so the validated Core Image chain is
 * conjugated with the flip between the two.
 */
internal fun layerTransform(
    preferred: CGAffineTransform,
    naturalWidth: Double,
    naturalHeight: Double,
    rotationDegrees: Int,
    flipped: Boolean,
    canvasWidth: Double,
    canvasHeight: Double,
): Affine {
    val coreImage = canvasTransform(
        preferred = preferred,
        naturalWidth = naturalWidth,
        naturalHeight = naturalHeight,
        rotationDegrees = rotationDegrees,
        flipped = flipped,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
    )
    val flip = Affine.translation(0.0, naturalHeight).then(Affine.scale(1.0, -1.0))
    return flip.then(coreImage).then(flip)
}

/* ------------------------------------------------------------------------- */
/*                              Canvas geometry                               */
/* ------------------------------------------------------------------------- */

/**
 * The output size of the render.
 *
 * The short side of the chosen resolution is kept and the long side follows
 * the canvas ratio, exactly like the Android renderer: a 9:16 canvas at
 * 1080x1920 therefore renders 1080x1920. "Original" keeps the source's own
 * aspect at the requested height.
 */
internal fun canvasSize(
    project: EditProject,
    settings: ExportSettings,
    sourceAspect: Pair<Double, Double>?,
): Pair<Int, Int> {
    val ratio = project.canvas.aspect.ratio
    val shortSide = min(settings.width, settings.height)
    val (width, height) = if (ratio != null) {
        if (ratio < 1f) {
            shortSide.toDouble() to (shortSide.toDouble() / ratio.toDouble())
        } else {
            (shortSide.toDouble() * ratio.toDouble()) to shortSide.toDouble()
        }
    } else {
        val source = sourceAspect
        if (source != null && source.second > 0.0) {
            val h = settings.height.toDouble()
            (h * source.first / source.second) to h
        } else {
            settings.width.toDouble() to settings.height.toDouble()
        }
    }
    return evenRounded(width) to evenRounded(height)
}

/** H.264 wants even dimensions, so the size is rounded to the nearest even number. */
private fun evenRounded(value: Double): Int {
    val rounded = value.roundToInt().coerceAtLeast(2)
    return if (rounded % 2 == 0) rounded else rounded - 1
}

/** The oriented (display) size of the first usable clip, used for "Original" canvases. */
internal fun firstClipAspect(project: EditProject): Pair<Double, Double>? {
    val clip = project.clips.firstOrNull() ?: return null
    val asset = AVURLAsset(NSURL.fileURLWithPath(clip.path), options = null)
    val track = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack ?: return null
    val size = orientedSize(
        naturalWidth = track.naturalWidth(),
        naturalHeight = track.naturalHeight(),
        preferred = track.preferredTransformValue(),
    )
    return if (size.first > 0.0 && size.second > 0.0) size else null
}

/** True when the two sizes describe the same shape. */
internal fun sameAspect(a: Pair<Double, Double>?, b: Pair<Double, Double>): Boolean {
    if (a == null) return false
    val left = a.first / a.second
    val right = b.first / b.second
    return abs(left - right) < 0.01
}
