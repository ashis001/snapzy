package com.movexa.app.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.movexa.app.core.designsystem.MovexaColors
import com.movexa.app.core.designsystem.MovexaShapes

/**
 * A stylised illustration of the app for each onboarding slide.
 *
 * Drawn with Compose primitives rather than shipped as bitmaps so it stays
 * crisp at every density and adds no assets to the APK.
 */
@Composable
fun OnboardingArt(
    variant: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MovexaShapes.large)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MovexaColors.brandPrimary.copy(alpha = 0.10f),
                        MovexaColors.bgSurface,
                    ),
                )
            )
            .border(1.dp, MovexaColors.divider, MovexaShapes.large),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(28.dp)) {
            when (variant) {
                0 -> drawVideoEditor()
                1 -> drawPhotoEditor()
                else -> drawProBadge()
            }
        }
    }
}

/** A mock of the video editor: preview frame, timeline strip, tool row. */
private fun DrawScope.drawVideoEditor() {
    val w = size.width
    val h = size.height

    // Preview frame
    val frameWidth = w * 0.34f
    val frameHeight = frameWidth * 16f / 9f
    val frameLeft = (w - frameWidth) / 2f
    val frameTop = h * 0.05f
    drawRoundRect(
        color = MovexaColors.bgSurfaceElevated,
        topLeft = Offset(frameLeft, frameTop),
        size = Size(frameWidth, frameHeight),
        cornerRadius = CornerRadius(14f, 14f),
    )
    drawRoundRect(
        color = MovexaColors.brandPrimary.copy(alpha = 0.18f),
        topLeft = Offset(frameLeft, frameTop),
        size = Size(frameWidth, frameHeight),
        cornerRadius = CornerRadius(14f, 14f),
    )
    // Play triangle
    val cx = frameLeft + frameWidth / 2f
    val cy = frameTop + frameHeight / 2f
    val play = Path().apply {
        moveTo(cx - 9f, cy - 12f)
        lineTo(cx + 12f, cy)
        lineTo(cx - 9f, cy + 12f)
        close()
    }
    drawPath(play, color = MovexaColors.brandPrimary)

    // Timeline strip — same width as the preview frame so it reads as one unit.
    val timelineTop = frameTop + frameHeight + h * 0.07f
    val clipGap = 5f
    val clipWidth = (frameWidth - clipGap * 3f) / 4f
    val clipHeight = h * 0.10f
    var x = frameLeft
    repeat(4) { index ->
        drawRoundRect(
            color = if (index == 0) MovexaColors.brandPrimary.copy(alpha = 0.55f) else MovexaColors.bgSurfaceElevated,
            topLeft = Offset(x, timelineTop),
            size = Size(clipWidth, clipHeight),
            cornerRadius = CornerRadius(6f, 6f),
        )
        x += clipWidth + clipGap
    }

    // Tool row
    val toolTop = timelineTop + clipHeight + h * 0.09f
    val toolSize = w * 0.07f
    val gap = (w - toolSize * 6f) / 7f
    repeat(6) { index ->
        val tx = gap + index * (toolSize + gap)
        drawRoundRect(
            color = if (index == 1) MovexaColors.brandPrimary else MovexaColors.bgSurfaceElevated,
            topLeft = Offset(tx, toolTop),
            size = Size(toolSize, toolSize),
            cornerRadius = CornerRadius(toolSize / 2.4f, toolSize / 2.4f),
        )
    }
}

/** A mock of the photo editor: image card with filters and a slider. */
private fun DrawScope.drawPhotoEditor() {
    val w = size.width
    val h = size.height

    val cardWidth = w * 0.62f
    val cardHeight = h * 0.62f
    val left = (w - cardWidth) / 2f
    val top = h * 0.10f

    drawRoundRect(
        color = MovexaColors.bgSurfaceElevated,
        topLeft = Offset(left, top),
        size = Size(cardWidth, cardHeight),
        cornerRadius = CornerRadius(18f, 18f),
    )
    // "Photo" gradient placeholder
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                MovexaColors.brandSecondary.copy(alpha = 0.55f),
                MovexaColors.brandPrimary.copy(alpha = 0.45f),
            ),
        ),
        topLeft = Offset(left + 12f, top + 12f),
        size = Size(cardWidth - 24f, cardHeight - 24f),
        cornerRadius = CornerRadius(12f, 12f),
    )

    // Filter swatches
    val swatchTop = top + cardHeight + h * 0.08f
    val swatchSize = w * 0.085f
    val swatchGap = (w - swatchSize * 5f) / 6f
    repeat(5) { index ->
        val sx = swatchGap + index * (swatchSize + swatchGap)
        drawRoundRect(
            color = if (index == 2) MovexaColors.brandPrimary else MovexaColors.bgSurfaceElevated,
            topLeft = Offset(sx, swatchTop),
            size = Size(swatchSize, swatchSize),
            cornerRadius = CornerRadius(8f, 8f),
        )
    }

    // Slider
    val sliderTop = swatchTop + swatchSize + h * 0.09f
    drawLine(
        color = MovexaColors.divider,
        start = Offset(w * 0.16f, sliderTop),
        end = Offset(w * 0.84f, sliderTop),
        strokeWidth = 6f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = MovexaColors.brandPrimary,
        start = Offset(w * 0.16f, sliderTop),
        end = Offset(w * 0.56f, sliderTop),
        strokeWidth = 6f,
        cap = StrokeCap.Round,
    )
    drawCircle(MovexaColors.brandPrimary, radius = 11f, center = Offset(w * 0.56f, sliderTop))
}

/** A mock of the Pro tier: a gradient badge with sparkles. */
private fun DrawScope.drawProBadge() {
    val w = size.width
    val h = size.height
    val badgeSize = w * 0.42f
    val left = (w - badgeSize) / 2f
    val top = h * 0.22f

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(MovexaColors.brandPrimary, MovexaColors.brandSecondary),
        ),
        topLeft = Offset(left, top),
        size = Size(badgeSize, badgeSize * 0.62f),
        cornerRadius = CornerRadius(22f, 22f),
    )

    // Crown
    val cx = left + badgeSize / 2f
    val cy = top + badgeSize * 0.31f
    val crown = Path().apply {
        moveTo(cx - badgeSize * 0.22f, cy + badgeSize * 0.11f)
        lineTo(cx - badgeSize * 0.17f, cy - badgeSize * 0.08f)
        lineTo(cx - badgeSize * 0.07f, cy + badgeSize * 0.02f)
        lineTo(cx, cy - badgeSize * 0.13f)
        lineTo(cx + badgeSize * 0.07f, cy + badgeSize * 0.02f)
        lineTo(cx + badgeSize * 0.17f, cy - badgeSize * 0.08f)
        lineTo(cx + badgeSize * 0.22f, cy + badgeSize * 0.11f)
        close()
    }
    drawPath(crown, color = MovexaColors.bgBase)

    // Sparkles
    sparkle(cx - badgeSize * 0.52f, cy - badgeSize * 0.30f, badgeSize * 0.13f)
    sparkle(cx + badgeSize * 0.50f, cy - badgeSize * 0.18f, badgeSize * 0.09f)
    sparkle(cx + badgeSize * 0.38f, cy + badgeSize * 0.34f, badgeSize * 0.07f)
}

/** A four-point sparkle. */
private fun DrawScope.sparkle(cx: Float, cy: Float, size: Float) {
    val path = Path().apply {
        moveTo(cx, cy - size)
        lineTo(cx + size * 0.26f, cy - size * 0.26f)
        lineTo(cx + size, cy)
        lineTo(cx + size * 0.26f, cy + size * 0.26f)
        lineTo(cx, cy + size)
        lineTo(cx - size * 0.26f, cy + size * 0.26f)
        lineTo(cx - size, cy)
        lineTo(cx - size * 0.26f, cy - size * 0.26f)
        close()
    }
    drawPath(path, color = MovexaColors.accentPro)
}
