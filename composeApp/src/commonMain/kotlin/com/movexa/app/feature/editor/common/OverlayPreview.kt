package com.movexa.app.feature.editor.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import com.movexa.app.core.designsystem.MovexaColors
import com.movexa.app.core.designsystem.MovexaShapes
import com.movexa.app.core.designsystem.MovexaType
import com.movexa.app.core.editing.Overlay
import kotlin.math.roundToInt

/**
 * Renders the overlay layers on top of the media preview and lets the user
 * drag them around. Shared by the video and photo editors.
 */
@Composable
fun OverlayLayer(
    overlay: Overlay,
    selected: Boolean,
    onSelect: () -> Unit,
    onDrag: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (overlay) {
        is Overlay.Text -> {
            Box(
                modifier = modifier
                    .clip(MovexaShapes.small)
                    .background(if (selected) MovexaColors.brandPrimary.copy(alpha = 0.14f) else Color.Transparent)
                    .pointerInput(overlay.id) {
                        detectDragGestures(
                            onDragStart = { onSelect() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            },
                        )
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                BasicTextField(
                    value = overlay.text,
                    onValueChange = {},
                    readOnly = true,
                    textStyle = TextStyle(
                        color = overlay.color,
                        fontSize = (overlay.fontSizeSp * overlay.scale).sp(),
                        fontWeight = if (overlay.bold) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }

        is Overlay.Sticker -> {
            Text(
                text = overlay.emoji,
                style = MovexaType.display,
                color = overlay.color,
                modifier = modifier
                    .clip(MovexaShapes.small)
                    .background(if (selected) MovexaColors.brandPrimary.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable(onClick = onSelect)
                    .pointerInput(overlay.id) {
                        detectDragGestures(
                            onDragStart = { onSelect() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            },
                        )
                    }
                    .padding(4.dp),
            )
        }

        is Overlay.Drawing -> {
            // Strokes are stored normalised to the canvas (0..1 on both
            // axes) — the same space the export renderer uses — so they are
            // mapped back to pixels here.
            Canvas(
                modifier = modifier
                    .fillMaxWidth()
                    .pointerInput(overlay.id) {
                        detectDragGestures(
                            onDragStart = { onSelect() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            },
                        )
                    },
            ) {
                if (overlay.points.size < 2) return@Canvas
                val path = Path().apply {
                    moveTo(overlay.points.first().x * size.width, overlay.points.first().y * size.height)
                    overlay.points.drop(1).forEach {
                        lineTo(it.x * size.width, it.y * size.height)
                    }
                }
                drawPath(
                    path = path,
                    color = overlay.color,
                    style = Stroke(width = overlay.strokeWidth.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        is Overlay.Pip -> {
            Box(
                modifier = modifier
                    .clip(MovexaShapes.small)
                    .background(MovexaColors.bgSurfaceElevated)
                    .clickable(onClick = onSelect)
                    .padding(8.dp),
            ) {
                Text(
                    text = "PIP",
                    style = MovexaType.caption,
                    color = MovexaColors.brandPrimary,
                )
            }
        }
    }
}

/**
 * The editor's top bar: back, undo/redo and the export action.
 *
 * Kept in one place so the photo and video editors look and behave the same.
 */
@Composable
fun EditorTopBar(
    title: String,
    onBack: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MovexaColors.bgBase)
            .drawBehind {
                drawLine(
                    color = MovexaColors.borderSubtle,
                    start = Offset(0f, size.height - 1f),
                    end = Offset(size.width, size.height - 1f),
                    strokeWidth = 1f,
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(MovexaShapes.pill)
                .clickable(onClick = onBack)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(text = "‹", style = MovexaType.title, color = MovexaColors.textPrimary)
        }

        Text(
            text = title,
            style = MovexaType.body,
            color = MovexaColors.textSecondary,
            modifier = Modifier.weight(1f),
        )

        Box(
            modifier = Modifier
                .clip(MovexaShapes.pill)
                .clickable(enabled = canUndo, onClick = onUndo)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = "↶",
                style = MovexaType.title,
                color = if (canUndo) MovexaColors.textPrimary else MovexaColors.divider,
            )
        }
        Box(
            modifier = Modifier
                .clip(MovexaShapes.pill)
                .clickable(enabled = canRedo, onClick = onRedo)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = "↷",
                style = MovexaType.title,
                color = if (canRedo) MovexaColors.textPrimary else MovexaColors.divider,
            )
        }

        trailing?.invoke()
    }
}

/**
 * Moves an overlay by a delta.
 *
 * The delta is **normalised** (a fraction of the canvas), because overlay
 * positions and stroke points are both stored in the canvas's 0..1 space —
 * the same space the export renderer uses.
 */
fun Overlay.movedBy(dx: Float, dy: Float): Overlay = when (this) {
    is Overlay.Text -> copy(position = (position + Offset(dx, dy)).coercedInside())
    is Overlay.Sticker -> copy(position = (position + Offset(dx, dy)).coercedInside())
    is Overlay.Drawing -> copy(points = points.map { it + Offset(dx, dy) })
    is Overlay.Pip -> copy(position = (position + Offset(dx, dy)).coercedInside())
}

/** Converts a Compose font size in sp to the unit the overlay renderer expects. */
private fun Float.sp() = androidx.compose.ui.unit.TextUnit(
    this,
    androidx.compose.ui.unit.TextUnitType.Sp,
)

private fun Offset.coercedInside(): Offset = Offset(
    x = x.coerceIn(0f, 1f),
    y = y.coerceIn(0f, 1f),
)

/** Converts a normalised overlay position into a pixel offset inside a box. */
fun Overlay.offsetIn(width: Int, height: Int): IntOffset = IntOffset(
    (position.x * width).roundToInt() - (width / 2),
    (position.y * height).roundToInt() - (height / 2),
)

/** A thin spacer used between top-bar actions. */
@Composable
fun BarSpacer() {
    Spacer(Modifier.width(2.dp))
}
