@file:OptIn(ExperimentalForeignApi::class)

package com.movexa.app.core.media

import androidx.compose.ui.graphics.Color
import com.movexa.app.core.editing.Overlay
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFRangeMake
import platform.CoreFoundation.CFAttributedStringCreateMutable
import platform.CoreFoundation.CFAttributedStringSetAttribute
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorCreate
import platform.CoreGraphics.CGColorRef
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextBeginPath
import platform.CoreGraphics.CGContextClosePath
import platform.CoreGraphics.CGContextMoveToPoint
import platform.CoreGraphics.CGContextSetLineCap
import platform.CoreGraphics.CGContextSetLineJoin
import platform.CoreGraphics.CGContextSetLineWidth
import platform.CoreGraphics.CGContextSetStrokeColorWithColor
import platform.CoreGraphics.CGContextSetTextPosition
import platform.CoreGraphics.CGContextStrokePath
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGLineCap
import platform.CoreGraphics.CGLineJoin
import platform.CoreImage.CIImage
import platform.CoreText.CTFontCreateWithName
import platform.CoreText.CTFontGetAscent
import platform.CoreText.CTFontGetDescent
import platform.CoreText.CTLineCreateWithAttributedString
import platform.CoreText.CTLineDraw
import platform.CoreText.CTLineGetTypographicBounds
import platform.CoreText.CTLineRef
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the editor's overlays into the exported frames.
 *
 * Text and stickers are rendered once into a transparent bitmap (Core Text for
 * the glyphs, Core Graphics for the strokes) and then placed on the canvas
 * with the overlay's position, scale, rotation and opacity. Freehand drawings
 * are stored in canvas-normalised coordinates — the same space the preview
 * draws them in — so they are rasterised at their exact canvas position.
 */
internal class IosOverlayRenderer(
    overlays: List<Overlay>,
    private val canvasWidth: Int,
    private val canvasHeight: Int,
) {
    private val layers: List<Layer> = buildLayers(overlays)

    /** True when the project has nothing to draw on top of the video. */
    val isEmpty: Boolean get() = layers.isEmpty()

    /** Composites every overlay that is visible at [timeMs] over [base]. */
    fun composite(base: CIImage, timeMs: Long): CIImage {
        var result = base
        layers.forEach { layer ->
            if (!layer.isVisibleAt(timeMs)) return@forEach
            var image = layer.image.imageByApplyingTransform(layer.transform.toCGAffineTransform())
            if (layer.opacity < 0.999f) {
                image = IosColorGrade.applyOpacity(image, layer.opacity)
            }
            result = image.imageByCompositingOverImage(result)
        }
        return result
    }

    private fun buildLayers(overlays: List<Overlay>): List<Layer> {
        val drawings = mutableListOf<Layer>()
        val others = mutableListOf<Layer>()

        overlays.forEach { overlay ->
            when (overlay) {
                is Overlay.Text -> renderText(overlay)?.let { others += it }
                is Overlay.Sticker -> renderSticker(overlay)?.let { others += it }
                is Overlay.Drawing -> renderDrawing(overlay)?.let { drawings += it }
                // PIP is composited by the render loop, which owns the second decoder.
                is Overlay.Pip -> Unit
            }
        }

        // Drawings sit under the text and the stickers, exactly like the preview.
        return drawings + others
    }

    /* ------------------------------------------------------------- text --- */

    private fun renderText(overlay: Overlay.Text): Layer? {
        if (overlay.text.isBlank()) return null
        val fontSize = fontSizePx(overlay.fontSizeSp * overlay.scale)
        val colour = overlay.color

        val fill = attributedLine(overlay.text, fontSize, colour, bold = overlay.bold) ?: return null
        val shadow = if (overlay.shadow) {
            attributedLine(overlay.text, fontSize, Color.Black, bold = overlay.bold)
        } else {
            null
        }
        val outline = if (overlay.outline) {
            attributedLine(overlay.text, fontSize, Color.Black, bold = overlay.bold)
        } else {
            null
        }
        val metrics = lineMetrics(fill) ?: return null

        val stroke = max(1.0, fontSize * 0.05)
        val padding = ceil(if (overlay.outline || overlay.shadow) stroke * 2.0 + fontSize * 0.1 else 2.0)
        val width = (metrics.width + padding * 2).roundToInt().coerceAtLeast(1)
        val height = (metrics.ascent + metrics.descent + padding * 2).roundToInt().coerceAtLeast(1)

        val image = renderBitmap(width, height) { context ->
            val baseline = padding + metrics.ascent
            if (shadow != null) {
                drawLine(context, shadow, padding + stroke, baseline - stroke)
            }
            if (outline != null) {
                val offsets = listOf(
                    stroke to 0.0, -stroke to 0.0, 0.0 to stroke, 0.0 to -stroke,
                    stroke to stroke, -stroke to -stroke, stroke to -stroke, -stroke to stroke,
                )
                offsets.forEach { (dx, dy) ->
                    drawLine(context, outline, padding + dx, baseline + dy)
                }
            }
            drawLine(context, fill, padding, baseline)
        } ?: return null

        val centreX = overlay.position.x.toDouble() * canvasWidth
        val centreY = overlay.position.y.toDouble() * canvasHeight
        val transform = placeCentred(image, overlay.scale, overlay.rotation.toDouble(), centreX, centreY)
        return Layer(overlay, image, transform, overlay.opacity)
    }

    /* ---------------------------------------------------------- sticker --- */

    private fun renderSticker(overlay: Overlay.Sticker): Layer? {
        if (overlay.emoji.isBlank()) return null
        val base = shortSide() * 0.25
        val fontSize = base * 0.8
        val line = attributedLine(overlay.emoji, fontSize, overlay.color, bold = false, fontName = EMOJI_FONT)
            ?: return null
        val metrics = lineMetrics(line) ?: return null

        val size = max(base, max(metrics.width, metrics.ascent + metrics.descent) * 1.15)
            .roundToInt().coerceAtLeast(1)
        val image = renderBitmap(size, size) { context ->
            val x = (size - metrics.width) / 2.0
            val y = (size - (metrics.ascent + metrics.descent)) / 2.0
            drawLine(context, line, x, y + metrics.ascent)
        } ?: return null

        val centreX = overlay.position.x.toDouble() * canvasWidth
        val centreY = overlay.position.y.toDouble() * canvasHeight
        val transform = placeCentred(image, overlay.scale, overlay.rotation.toDouble(), centreX, centreY)
        return Layer(overlay, image, transform, overlay.opacity)
    }

    /* ---------------------------------------------------------- drawing --- */

    private fun renderDrawing(overlay: Overlay.Drawing): Layer? {
        if (overlay.points.size < 2) return null

        val strokeWidth = max(1.0, overlay.strokeWidth * dpScale())
        val xs = overlay.points.map { it.x.coerceIn(0f, 1f) * canvasWidth }
        val ys = overlay.points.map { it.y.coerceIn(0f, 1f) * canvasHeight }
        val minX = xs.min() - strokeWidth
        val maxX = xs.max() + strokeWidth
        val minY = ys.min() - strokeWidth
        val maxY = ys.max() + strokeWidth

        val width = (maxX - minX).roundToInt().coerceAtLeast(1)
        val height = (maxY - minY).roundToInt().coerceAtLeast(1)

        val image = renderBitmap(width, height) { context ->
            CGContextSetLineWidth(context, strokeWidth)
            CGContextSetLineCap(context, CGLineCap.kCGLineCapRound)
            CGContextSetLineJoin(context, CGLineJoin.kCGLineJoinRound)
            CGContextSetStrokeColorWithColor(
                context,
                colorOf(
                    overlay.color.red.toDouble(),
                    overlay.color.green.toDouble(),
                    overlay.color.blue.toDouble(),
                    overlay.opacity.coerceIn(0f, 1f).toDouble(),
                ),
            )
            CGContextBeginPath(context)
            overlay.points.forEachIndexed { index, point ->
                val x = point.x.coerceIn(0f, 1f) * canvasWidth - minX
                val y = point.y.coerceIn(0f, 1f) * canvasHeight - minY
                if (index == 0) CGContextMoveToPoint(context, x, y)
                else CGContextAddLineToPoint(context, x, y)
            }
            CGContextClosePath(context)
            CGContextStrokePath(context)
        } ?: return null

        // The drawing already lives in canvas coordinates, so it is placed
        // as-is: its bottom-left corner lands on the canvas point the stroke
        // was drawn from.
        val transform = Affine.translation(minX, canvasHeight - maxY)
        return Layer(overlay, image, transform, 1f)
    }

    /* ---------------------------------------------------------- helpers --- */

    /**
     * The transform that centres [image] on the canvas and applies the
     * overlay's scale and rotation.
     */
    private fun placeCentred(
        image: CIImage,
        scale: Float,
        rotationDegrees: Double,
        centreX: Double,
        centreY: Double,
    ): Affine {
        val extent = image.extent.useContents { this }
        val width = extent.size.width
        val height = extent.size.height
        val localCentreX = width / 2.0
        val localCentreY = height / 2.0

        var matrix = Affine.IDENTITY
        if (rotationDegrees % 360.0 != 0.0) {
            val radians = -rotationDegrees * kotlin.math.PI / 180.0
            matrix = Affine.translation(localCentreX, localCentreY)
                .then(Affine.rotation(radians))
                .then(Affine.translation(-localCentreX, -localCentreY))
                .then(matrix)
        }
        val effectiveScale = scale.toDouble().coerceAtLeast(0.01)
        if (effectiveScale != 1.0) {
            matrix = Affine.translation(localCentreX, localCentreY)
                .then(Affine.scale(effectiveScale, effectiveScale))
                .then(Affine.translation(-localCentreX, -localCentreY))
                .then(matrix)
        }

        val targetX = centreX - width * effectiveScale / 2.0
        val targetY = (canvasHeight - centreY) - height * effectiveScale / 2.0
        return Affine.translation(targetX, targetY).then(matrix)
    }

    /** `sp` -> pixels, using the canvas' short side as the reference. */
    private fun fontSizePx(sp: Float): Double = sp * dpScale()

    /** The density-independent-pixel scale used for the canvas. */
    private fun dpScale(): Double = shortSide() / REFERENCE_DP

    private fun shortSide(): Double = min(canvasWidth, canvasHeight).toDouble()

    /** Creates a Core Text line for [text]. */
    private fun attributedLine(
        text: String,
        fontSize: Double,
        colour: Color,
        bold: Boolean,
        fontName: String = if (bold) BOLD_FONT else REGULAR_FONT,
    ): TextLine? {
        val font = CTFontCreateWithName(
            CFStringCreateWithCString(null, fontName, kCFStringEncodingUTF8),
            fontSize,
            null,
        ) ?: return null

        val attributed = CFAttributedStringCreateMutable(null, 0) ?: return null
        val range = CFRangeMake(0, text.length.toLong())
        CFAttributedStringSetAttribute(
            attributed,
            range,
            CFStringCreateWithCString(null, FONT_ATTRIBUTE, kCFStringEncodingUTF8),
            font,
        )
        CFAttributedStringSetAttribute(
            attributed,
            range,
            CFStringCreateWithCString(null, COLOUR_ATTRIBUTE, kCFStringEncodingUTF8),
            colorOf(
                colour.red.toDouble(),
                colour.green.toDouble(),
                colour.blue.toDouble(),
                1.0,
            ),
        )
        val line = CTLineCreateWithAttributedString(attributed) ?: return null
        val width = CTLineGetTypographicBounds(line, null, null, null)
        return TextLine(line, font, width, CTFontGetAscent(font), CTFontGetDescent(font))
    }

    /** The width and vertical metrics of a Core Text line. */
    private fun lineMetrics(text: TextLine): LineMetrics? {
        if (text.width <= 0.0 && text.ascent <= 0.0) return null
        return LineMetrics(text.width, text.ascent, text.descent)
    }

    /** Draws a Core Text line with its baseline at ([x], [y]). */
    private fun drawLine(context: CGContextRef?, text: TextLine, x: Double, y: Double) {
        CGContextSetTextPosition(context, x, y)
        CTLineDraw(text.line, context)
    }

    /**
     * Renders into a transparent bitmap and hands back a Core Image.
     *
     * The bitmap context is bottom-left origin, so the drawing code can use
     * ordinary Core Graphics coordinates.
     */
    private fun renderBitmap(
        width: Int,
        height: Int,
        draw: (CGContextRef?) -> Unit,
    ): CIImage? {
        val space = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            null,
            width.toULong(),
            height.toULong(),
            8uL,
            (width * 4).toULong(),
            space,
            CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
        ) ?: return null

        draw(context)
        val cgImage = CGBitmapContextCreateImage(context) ?: return null
        return CIImage(cgImage)
    }

    /** A Core Graphics colour with the given components. */
    private fun colorOf(red: Double, green: Double, blue: Double, alpha: Double): CGColorRef? {
        val components = doubleArrayOf(red, green, blue, alpha)
        return components.usePinned { pinned ->
            CGColorCreate(CGColorSpaceCreateDeviceRGB(), pinned.addressOf(0))
        }
    }

    private class LineMetrics(val width: Double, val ascent: Double, val descent: Double)

    /** A Core Text line with its font and metrics. */
    private class TextLine(
        val line: CTLineRef,
        val font: platform.CoreText.CTFontRef,
        val width: Double,
        val ascent: Double,
        val descent: Double,
    )

    private companion object {
        const val REFERENCE_DP = 360.0
        const val REGULAR_FONT = "Helvetica"
        const val BOLD_FONT = "Helvetica-Bold"
        const val EMOJI_FONT = "Apple Color Emoji"
        const val FONT_ATTRIBUTE = "NSFont"
        const val COLOUR_ATTRIBUTE = "CTForegroundColor"
    }
}

/** One overlay, already rasterised and placed. */
private class Layer(
    val overlay: Overlay,
    val image: CIImage,
    val transform: Affine,
    val opacity: Float,
) {
    fun isVisibleAt(timeMs: Long): Boolean {
        if (timeMs < overlay.startMs) return false
        val end = overlay.endMs
        if (end != Long.MAX_VALUE && timeMs >= end) return false
        return true
    }
}
