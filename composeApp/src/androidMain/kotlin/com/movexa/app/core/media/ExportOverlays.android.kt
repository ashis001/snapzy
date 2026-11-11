package com.movexa.app.core.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings
import com.movexa.app.core.designsystem.MovexaType
import com.movexa.app.core.editing.Overlay
import kotlin.math.abs
import kotlin.math.max

/**
 * Overlay rendering for the export.
 *
 * Media3 asks a `TextureOverlay` for its settings *per frame*, which is what
 * makes time-based effects possible: an overlay can fade in, fade out or
 * change its position while the video is being written.
 */

/**
 * The height of the on-screen preview canvas, in dp.
 *
 * Text and sticker sizes are stored in sp against the preview, so the export
 * needs the same reference to turn them into pixels on the finished frame.
 */
private const val REFERENCE_CANVAS_DP = 400f

/**
 * An overlay that only exists inside a time window.
 *
 * Outside the window its alpha is zero, so the layer is invisible; inside it
 * behaves exactly like a normal static overlay.
 */
internal class WindowedBitmapOverlay(
    private val bitmap: Bitmap,
    private val base: OverlaySettings,
    private val startUs: Long,
    private val endUs: Long,
) : BitmapOverlay() {

    override fun getBitmap(presentationTimeUs: Long): Bitmap = bitmap

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val visible = presentationTimeUs >= startUs && presentationTimeUs < endUs
        return base.withAlpha(if (visible) base.alphaScale else 0f)
    }
}

/**
 * A full-frame layer whose alpha ramps across a window.
 *
 * Used for the transition between two clips: the outgoing clip ramps up to
 * black over its last moment and the incoming one ramps back down over its
 * first, so the frame dips through black at the boundary.
 */
internal class FadingBitmapOverlay(
    private val bitmap: Bitmap,
    private val startUs: Long,
    private val endUs: Long,
    /** `true` fades from clear to solid, `false` from solid to clear. */
    private val fadeIn: Boolean,
) : BitmapOverlay() {

    override fun getBitmap(presentationTimeUs: Long): Bitmap = bitmap

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val span = (endUs - startUs).toFloat()
        val progress = if (span <= 0f) {
            1f
        } else {
            ((presentationTimeUs - startUs).toFloat() / span).coerceIn(0f, 1f)
        }
        val alpha = if (fadeIn) progress else 1f - progress
        return OverlaySettings.Builder()
            .setAlphaScale(alpha.coerceIn(0f, 1f))
            .setBackgroundFrameAnchor(0f, 0f)
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(1f, 1f)
            .build()
    }
}

/** Copies [OverlaySettings] with a different alpha. */
internal fun OverlaySettings.withAlpha(alpha: Float): OverlaySettings = OverlaySettings.Builder()
    .setAlphaScale(alpha.coerceIn(0f, 1f))
    .setBackgroundFrameAnchor(backgroundFrameAnchor.first, backgroundFrameAnchor.second)
    .setOverlayFrameAnchor(overlayFrameAnchor.first, overlayFrameAnchor.second)
    .setScale(scale.first, scale.second)
    .setRotationDegrees(rotationDegrees)
    .setHdrLuminanceMultiplier(hdrLuminanceMultiplier)
    .build()

/** A solid black frame the size of the canvas. */
internal fun blackFrame(width: Int, height: Int): Bitmap =
    Bitmap.createBitmap(max(1, width), max(1, height), Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.BLACK)
    }

/**
 * Renders a text layer into a bitmap.
 *
 * The size is derived from the preview reference height so the exported text
 * matches what the user saw while editing.
 */
internal fun renderTextBitmap(overlay: Overlay.Text, canvasWidth: Int, canvasHeight: Int): Bitmap {
    val fontPx = (overlay.fontSizeSp / REFERENCE_CANVAS_DP * canvasHeight).coerceAtLeast(8f)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = overlay.color.toArgbInt()
        textSize = fontPx
        typeface = if (overlay.bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        style = Paint.Style.FILL
    }

    val pad = (fontPx * 0.45f).toInt() + if (overlay.outline || overlay.shadow) 6 else 0
    val bounds = android.graphics.Rect()
    paint.getTextBounds(overlay.text, 0, overlay.text.length, bounds)
    val metrics = paint.fontMetrics
    val width = (bounds.width() + pad * 2).coerceAtLeast(4)
    val height = (bounds.height() + pad * 2).coerceAtLeast(4)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val x = (width / 2f) - paint.measureText(overlay.text) / 2f
    val y = (height / 2f) - (metrics.ascent + metrics.descent) / 2f

    if (overlay.shadow) {
        val shadow = Paint(paint).apply {
            color = Color.argb(160, 0, 0, 0)
            textSize = fontPx * 1.04f
        }
        canvas.drawText(overlay.text, x + fontPx * 0.06f, y + fontPx * 0.08f, shadow)
    }

    if (overlay.outline) {
        val outline = Paint(paint).apply {
            color = Color.argb(255, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = (fontPx * 0.09f).coerceAtLeast(2f)
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawText(overlay.text, x, y, outline)
    }

    canvas.drawText(overlay.text, x, y, paint)
    return bitmap
}

/**
 * Renders an emoji sticker into a bitmap sized against the canvas.
 *
 * The preview draws stickers with the display type, so the export uses the
 * same relative size.
 */
internal fun renderEmojiBitmap(
    emoji: String,
    tint: androidx.compose.ui.graphics.Color,
    canvasWidth: Int,
    canvasHeight: Int,
): Bitmap {
    val displaySp = MovexaType.display.fontSize.value.toFloat().coerceAtLeast(24f)
    val glyphPx = (displaySp / REFERENCE_CANVAS_DP * canvasHeight).coerceAtLeast(16f)
    val size = (glyphPx * 1.6f).toInt().coerceAtLeast(8)

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint.toArgbInt()
        textSize = glyphPx
        textAlign = Paint.Align.CENTER
    }
    val metrics = paint.fontMetrics
    val baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f
    canvas.drawText(emoji, size / 2f, baseline, paint)
    return bitmap
}

/**
 * Renders a freehand stroke into a canvas-sized bitmap.
 *
 * Stroke points are normalised to the canvas, so the bitmap is the same size
 * as the frame and the strokes land exactly where the preview showed them.
 */
internal fun renderStrokeBitmap(overlay: Overlay.Drawing, canvasWidth: Int, canvasHeight: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(
        max(1, canvasWidth),
        max(1, canvasHeight),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = overlay.color.toArgbInt().let { argb ->
            Color.argb(
                (Color.alpha(argb) * overlay.opacity.coerceIn(0f, 1f)).toInt(),
                Color.red(argb),
                Color.green(argb),
                Color.blue(argb),
            )
        }
        style = Paint.Style.STROKE
        strokeWidth = overlay.strokeWidth * (canvasHeight / REFERENCE_CANVAS_DP)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    val path = android.graphics.Path()
    overlay.points.forEachIndexed { index, point ->
        val x = point.x.coerceIn(0f, 1f) * canvasWidth
        val y = point.y.coerceIn(0f, 1f) * canvasHeight
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    canvas.drawPath(path, paint)
    return bitmap
}

/** Converts a Compose colour into an Android ARGB int. */
internal fun androidx.compose.ui.graphics.Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
