package com.snapzy.app.core.designsystem

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.snapzy.app.core.editing.EditorTool

/**
 * Line icons for the editor toolbar, drawn with Compose primitives.
 *
 * Hand-drawn rather than pulled from an icon font so the whole editor ships
 * with no extra dependency and every glyph uses the same 24x24 grid, 1.7px
 * stroke and round caps. Icons are stroked (not filled) to match the spec's
 * "line icons for toolbar, filled for primary actions" rule.
 */
fun DrawScope.drawToolIcon(tool: EditorTool, tint: Color, strokeWidth: Float = 2.0f) {
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
        color = tint,
        start = Offset(x1, y1),
        end = Offset(x2, y2),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )

    fun circle(cx: Float, cy: Float, r: Float) = drawCircle(
        color = tint,
        radius = r,
        center = Offset(cx, cy),
        style = Stroke(width = strokeWidth),
    )

    fun circleFilled(cx: Float, cy: Float, r: Float) = drawCircle(
        color = tint,
        radius = r,
        center = Offset(cx, cy),
    )

    fun rect(l: Float, t: Float, r: Float, b: Float, radius: Float = 0f) = drawRoundRect(
        color = tint,
        topLeft = Offset(l, t),
        size = Size(r - l, b - t),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = strokeWidth),
    )

    fun poly(vararg pts: Float) {
        val path = Path()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i + 1 < pts.size) {
            path.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        path.close()
        drawPath(path = path, color = tint, style = Stroke(width = strokeWidth, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }

    fun polyFilled(vararg pts: Float) {
        val path = Path()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i + 1 < pts.size) {
            path.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        path.close()
        drawPath(path = path, color = tint)
    }

    fun arcBox(l: Float, t: Float, r: Float, b: Float, start: Float, sweep: Float) {
        drawArc(
            color = tint,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(l, t),
            size = Size(r - l, b - t),
            style = Stroke(width = strokeWidth),
        )
    }

    fun sparkle(cx: Float, cy: Float, s: Float) {
        polyFilled(
            cx, cy - s, cx + s * 0.28f, cy - s * 0.28f, cx + s, cy,
            cx + s * 0.28f, cy + s * 0.28f, cx, cy + s, cx - s * 0.28f, cy + s * 0.28f,
            cx - s, cy, cx - s * 0.28f, cy - s * 0.28f,
        )
    }

    when (tool) {
        EditorTool.TRIM -> {
            line(4f, 8f, 20f, 8f); line(4f, 16f, 20f, 16f)
            line(9f, 5f, 9f, 11f); line(15f, 13f, 15f, 19f)
        }
        EditorTool.SPLIT -> {
            rect(4f, 6f, 20f, 18f, 2f); line(12f, 6f, 12f, 18f)
        }
        EditorTool.SPEED -> {
            arcBox(4f, 7f, 20f, 19f, 180f, 180f); line(12f, 16f, 17f, 10f)
        }
        EditorTool.VOLUME -> {
            polyFilled(4f, 10f, 8f, 10f, 12f, 6f, 12f, 18f, 8f, 14f, 4f, 14f)
            arcBox(14f, 9f, 21f, 15f, -50f, 100f)
        }
        EditorTool.MUSIC -> {
            line(10f, 17f, 10f, 6f); line(10f, 6f, 18f, 4f); line(18f, 4f, 18f, 15f)
            circleFilled(7.6f, 17.4f, 2.2f); circleFilled(15.6f, 15.4f, 2.2f)
        }
        EditorTool.VOICE -> {
            rect(9f, 3f, 15f, 12f, 3f); arcBox(5f, 7f, 19f, 18f, 0f, 180f)
            line(12f, 16f, 12f, 21f)
        }
        EditorTool.REVERSE -> {
            arcBox(5f, 5f, 19f, 19f, 180f, 200f); polyFilled(4f, 4f, 9f, 5.5f, 4.5f, 10f)
        }
        EditorTool.FREEZE -> {
            line(12f, 3f, 12f, 21f); line(4.5f, 7.5f, 19.5f, 16.5f); line(19.5f, 7.5f, 4.5f, 16.5f)
        }
        EditorTool.SWAP -> {
            line(4f, 9f, 18f, 9f); polyFilled(18f, 9f, 14.5f, 6f, 14.5f, 12f)
            line(20f, 15f, 6f, 15f); polyFilled(6f, 15f, 9.5f, 12f, 9.5f, 18f)
        }
        EditorTool.APPLY_ALL -> {
            line(4f, 7f, 20f, 7f); line(4f, 12f, 20f, 12f); line(4f, 17f, 14f, 17f)
            poly(16f, 15f, 18.5f, 17.5f, 14.5f, 20f)
        }
        EditorTool.FILTER -> {
            poly(4f, 5f, 20f, 5f, 14f, 12.5f, 14f, 19.5f, 10f, 21f, 10f, 12.5f)
        }
        EditorTool.ADJUST -> {
            line(3f, 7f, 21f, 7f); circleFilled(9f, 7f, 2.4f)
            line(3f, 13f, 21f, 13f); circleFilled(15f, 13f, 2.4f)
            line(3f, 19f, 21f, 19f); circleFilled(7f, 19f, 2.4f)
        }
        EditorTool.EFFECTS -> {
            sparkle(12f, 12f, 7f); sparkle(19f, 6f, 3f)
        }
        EditorTool.TEXT -> {
            line(4f, 6f, 20f, 6f); line(12f, 6f, 12f, 19f)
        }
        EditorTool.STICKER -> {
            circle(12f, 12f, 8.5f); circleFilled(9f, 10f, 1.1f); circleFilled(15f, 10f, 1.1f)
            arcBox(8f, 12f, 16f, 18f, 20f, 140f)
        }
        EditorTool.DRAW -> {
            poly(4f, 20f, 4f, 16f, 16f, 4f, 20f, 8f, 8f, 20f)
            line(14f, 6f, 18f, 10f)
        }
        EditorTool.MOSAIC -> {
            rect(4f, 4f, 11f, 11f); rect(13f, 4f, 20f, 11f)
            rect(4f, 13f, 11f, 20f); rect(13f, 13f, 20f, 20f)
        }
        EditorTool.PIP -> {
            rect(3f, 5f, 21f, 19f, 2f); rect(12f, 12f, 19f, 17f, 1.5f)
        }
        EditorTool.MASK -> {
            circle(12f, 12f, 8f); circle(12f, 12f, 4f)
        }
        EditorTool.BLEND -> {
            circle(9.5f, 12f, 6f); circle(14.5f, 12f, 6f)
        }
        EditorTool.CHROMA -> {
            rect(3f, 3f, 9f, 9f); rect(15f, 3f, 21f, 9f)
            rect(3f, 15f, 9f, 21f); rect(15f, 15f, 21f, 21f)
        }
        EditorTool.TRACKING -> {
            line(3f, 8f, 3f, 3f); line(3f, 3f, 8f, 3f)
            line(16f, 3f, 21f, 3f); line(21f, 3f, 21f, 8f)
            line(21f, 16f, 21f, 21f); line(21f, 21f, 16f, 21f)
            line(8f, 21f, 3f, 21f); line(3f, 21f, 3f, 16f)
            circle(12f, 12f, 2.5f)
        }
        EditorTool.CROP -> {
            line(6f, 3f, 6f, 18f); line(6f, 3f, 18f, 3f)
            line(18f, 21f, 18f, 6f); line(18f, 21f, 6f, 21f)
        }
        EditorTool.ROTATE -> {
            arcBox(5f, 5f, 19f, 19f, 40f, 280f); polyFilled(18f, 3.5f, 20f, 8.5f, 15f, 7f)
        }
        EditorTool.RATIO -> {
            rect(8f, 3f, 16f, 21f, 2f); line(11f, 7f, 13f, 7f); line(11f, 12f, 13f, 12f); line(11f, 17f, 13f, 17f)
        }
        EditorTool.BACKGROUND -> {
            rect(3f, 4f, 21f, 20f, 2f); line(3f, 16f, 21f, 8f); line(8f, 20f, 21f, 11f)
        }
        EditorTool.TRANSITION -> {
            rect(3f, 7f, 10f, 17f, 1.5f); rect(14f, 7f, 21f, 17f, 1.5f)
            line(10.5f, 12f, 13.5f, 12f); polyFilled(13.5f, 12f, 10.8f, 9.6f, 10.8f, 14.4f)
        }
        EditorTool.ANIMATION -> {
            polyFilled(7f, 5f, 18f, 12f, 7f, 19f); line(3f, 8f, 3f, 16f); line(5f, 6.5f, 5f, 17.5f)
        }
        EditorTool.KEYFRAME -> {
            poly(12f, 4f, 16f, 12f, 12f, 20f, 8f, 12f); circleFilled(4.5f, 6f, 1.6f); circleFilled(19.5f, 18f, 1.6f)
        }
        EditorTool.BORDER -> {
            rect(3f, 3f, 21f, 21f, 2f); line(3f, 8f, 3f, 3f); line(3f, 3f, 8f, 3f)
        }
        EditorTool.FRAME -> {
            rect(3f, 3f, 21f, 21f, 2f); rect(6f, 6f, 18f, 18f, 1f)
        }
        EditorTool.LAYOUT -> {
            rect(3f, 5f, 21f, 19f, 2f); line(12f, 5f, 12f, 19f); line(3f, 12f, 21f, 12f)
        }
        EditorTool.ERASER -> {
            poly(5f, 15f, 13f, 7f, 18f, 12f, 10f, 20f, 5f, 15f); line(9f, 19f, 20f, 19f)
        }
        EditorTool.COLLAGE -> {
            rect(3f, 4f, 14f, 15f, 1.5f); rect(10f, 9f, 21f, 20f, 1.5f)
        }
        EditorTool.AI_CUT -> {
            circle(7f, 18f, 2.4f); circle(7f, 6f, 2.4f); line(9f, 16.5f, 19f, 4f); line(9f, 7.5f, 19f, 20f)
        }
        EditorTool.CAPTIONS -> {
            rect(3f, 5f, 21f, 19f, 3f); line(7f, 11f, 17f, 11f); line(9f, 15f, 15f, 15f)
        }
        EditorTool.AI_SPEECH -> {
            rect(3f, 5f, 18f, 16f, 3f); polyFilled(9f, 16f, 13f, 16f, 11f, 21f)
            line(21f, 9f, 21f, 9f)
        }
        EditorTool.ENHANCE -> {
            sparkle(12f, 12f, 8f); sparkle(19.5f, 18f, 3.5f)
        }
        EditorTool.CUTOUT -> {
            circle(12f, 8f, 3.5f); arcBox(5f, 13f, 19f, 24f, 180f, 180f)
        }
        EditorTool.STABILIZE -> {
            line(12f, 3f, 12f, 21f); line(3f, 12f, 21f, 12f)
            circle(12f, 12f, 4f); line(6f, 6f, 6f, 10f); line(18f, 14f, 18f, 18f)
        }
        EditorTool.AI_RETOUCH -> {
            circle(12f, 12f, 8f); circleFilled(9.5f, 10.5f, 1f); circleFilled(14.5f, 10.5f, 1f)
            arcBox(9f, 12f, 15f, 16f, 20f, 140f); sparkle(19f, 5f, 2.5f)
        }
        EditorTool.AI_LIGHT -> {
            circle(12f, 12f, 4.5f)
            line(12f, 2.5f, 12f, 5f); line(12f, 19f, 12f, 21.5f)
            line(2.5f, 12f, 5f, 12f); line(19f, 12f, 21.5f, 12f)
            line(5.2f, 5.2f, 7f, 7f); line(17f, 17f, 18.8f, 18.8f)
        }
        EditorTool.AI_EFFECT -> {
            circle(10f, 12f, 7f); sparkle(18.5f, 7f, 3.5f)
        }
        EditorTool.DRAFT -> {
            poly(4f, 4f, 15f, 4f, 20f, 9f, 20f, 20f, 4f, 20f); poly(15f, 4f, 15f, 9f, 20f, 9f)
        }
        EditorTool.DURATION -> {
            circle(12f, 12f, 7.5f); poly(12f, 7.5f, 12f, 12f, 15.5f, 14f)
        }
        EditorTool.RECORD -> {
            rect(9f, 3f, 15f, 12f, 3f); arcBox(5f, 7f, 19f, 18f, 0f, 180f)
            line(12f, 16f, 12f, 21f)
        }
        EditorTool.EXTRACT_AUDIO -> {
            line(9f, 16f, 9f, 5f); line(9f, 5f, 17f, 3f); line(17f, 3f, 17f, 14f)
            circleFilled(6.6f, 16.4f, 2.2f); circleFilled(14.6f, 14.4f, 2.2f)
        }
        EditorTool.TELEPROMPTER -> {
            rect(3f, 5f, 21f, 15f, 2f); poly(9f, 15f, 12f, 20f, 14f, 15f)
            line(7f, 9f, 17f, 9f); line(7f, 12f, 13f, 12f)
        }
        EditorTool.EXPORT -> {
            line(12f, 3f, 12f, 14f); polyFilled(12f, 3f, 8f, 8f, 16f, 8f)
            poly(4f, 14f, 4f, 20f, 20f, 20f, 20f, 14f)
        }
    }
}
