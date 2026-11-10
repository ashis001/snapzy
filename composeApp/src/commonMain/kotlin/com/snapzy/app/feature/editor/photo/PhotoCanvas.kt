package com.snapzy.app.feature.editor.photo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapzy.app.core.designsystem.SnapzyColors
import com.snapzy.app.core.designsystem.SnapzyShapes
import com.snapzy.app.core.editing.BackgroundStyle
import com.snapzy.app.core.editing.EditProject
import com.snapzy.app.core.editing.EditorFont
import com.snapzy.app.core.editing.Overlay
import com.snapzy.app.feature.editor.common.OverlayLayer
import com.snapzy.app.feature.editor.common.offsetIn

/**
 * The photo preview.
 *
 * The canvas keeps the aspect ratio the project asks for, draws the still
 * inside it (letterboxed by the canvas background) and composites every
 * overlay on top. The whole canvas is recorded into a [GraphicsLayer] so the
 * export can turn the exact preview into JPEG bytes.
 */
@Composable
internal fun PhotoCanvas(
    bitmap: ImageBitmap,
    project: EditProject,
    colorFilter: ColorFilter,
    selectedOverlayId: Long?,
    fontByOverlay: Map<Long, EditorFont>,
    drawingEnabled: Boolean,
    textEditingEnabled: Boolean,
    onSelectOverlay: (Long) -> Unit,
    onMoveOverlay: (Long, Offset) -> Unit,
    onMoveOverlayEnd: () -> Unit,
    onEditText: (Long, String) -> Unit,
    onStrokeStart: (Offset) -> Unit,
    onStrokeMove: (Offset) -> Unit,
    graphicsLayer: GraphicsLayer,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val sourceRatio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
        val ratio = project.canvas.aspect.ratio ?: sourceRatio
        val fitsHeight = maxHeight * ratio <= maxWidth
        val canvasWidth = if (fitsHeight) maxHeight * ratio else maxWidth
        val canvasHeight = if (fitsHeight) maxHeight else maxWidth / ratio

        var canvasSize by remember { mutableStateOf(IntSize.Zero) }

        /** Converts a pixel position inside the canvas into normalised 0..1 space. */
        val normalised: (Offset) -> Offset = { point ->
            if (canvasSize.width <= 0 || canvasSize.height <= 0) {
                Offset.Zero
            } else {
                Offset(point.x / canvasSize.width, point.y / canvasSize.height)
            }
        }

        Box(
            modifier = Modifier
                .size(canvasWidth, canvasHeight)
                .clipToBounds()
                .onSizeChanged { canvasSize = it }
                .drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(graphicsLayer)
                }
                .then(
                    if (drawingEnabled) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { onStrokeStart(normalised(it)) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    onStrokeMove(normalised(change.position))
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            // The canvas colour is a child (not a modifier on the box) so the
            // recorded layer — and therefore the exported JPEG — keeps it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(canvasBackgroundColor(project.canvas.background)),
            )

            if (project.canvas.background == BackgroundStyle.BLUR || project.canvas.blurredBackground) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(28.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                )
            }

            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                project.overlays.forEach { overlay ->
                    when (overlay) {
                        is Overlay.Text -> PhotoTextLayer(
                            overlay = overlay,
                            selected = overlay.id == selectedOverlayId,
                            editable = textEditingEnabled && overlay.id == selectedOverlayId,
                            interactive = !drawingEnabled,
                            font = fontByOverlay[overlay.id] ?: EditorFont.DEFAULT,
                            onSelect = { onSelectOverlay(overlay.id) },
                            onDrag = { onMoveOverlay(overlay.id, normalised(it)) },
                            onDragEnd = onMoveOverlayEnd,
                            onTextChange = { onEditText(overlay.id, it) },
                            modifier = Modifier.offset {
                                overlay.offsetIn(canvasSize.width, canvasSize.height)
                            },
                        )

                        is Overlay.Sticker -> OverlayLayer(
                            overlay = overlay,
                            selected = overlay.id == selectedOverlayId,
                            onSelect = { onSelectOverlay(overlay.id) },
                            onDrag = { onMoveOverlay(overlay.id, normalised(it)) },
                            modifier = Modifier.offset {
                                overlay.offsetIn(canvasSize.width, canvasSize.height)
                            },
                        )

                        is Overlay.Drawing -> PhotoDrawingLayer(
                            overlay = overlay,
                            selected = overlay.id == selectedOverlayId,
                            canvasSize = canvasSize,
                            interactive = !drawingEnabled,
                            onSelect = { onSelectOverlay(overlay.id) },
                            onDrag = { onMoveOverlay(overlay.id, normalised(it)) },
                            onDragEnd = onMoveOverlayEnd,
                            modifier = Modifier.fillMaxSize(),
                        )

                        is Overlay.Pip -> OverlayLayer(
                            overlay = overlay,
                            selected = overlay.id == selectedOverlayId,
                            onSelect = { onSelectOverlay(overlay.id) },
                            onDrag = { onMoveOverlay(overlay.id, normalised(it)) },
                            modifier = Modifier.offset {
                                overlay.offsetIn(canvasSize.width, canvasSize.height)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The colour the canvas is painted with behind the photo. */
private fun canvasBackgroundColor(style: BackgroundStyle): Color = when (style) {
    BackgroundStyle.BLACK -> Color.Black
    BackgroundStyle.WHITE -> Color.White
    BackgroundStyle.BLUR -> Color.Black
    BackgroundStyle.CUSTOM -> SnapzyColors.bgSurface
}

/**
 * A text layer.
 *
 * The shared [OverlayLayer] renders text read-only, so the photo editor draws
 * its own: the layer has to be editable (the user types straight on the
 * photo) and it has to honour the outline / shadow switches.
 */
@Composable
private fun PhotoTextLayer(
    overlay: Overlay.Text,
    selected: Boolean,
    editable: Boolean,
    interactive: Boolean,
    font: EditorFont,
    onSelect: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = TextStyle(
        color = overlay.color,
        fontSize = (overlay.fontSizeSp * overlay.scale).sp,
        fontWeight = if (overlay.bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = fontFamilyFor(font),
        textAlign = TextAlign.Center,
        shadow = if (overlay.shadow) {
            Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(2f, 3f), blurRadius = 6f)
        } else {
            null
        },
    )

    Box(
        modifier = modifier
            .clip(SnapzyShapes.small)
            .background(if (selected) SnapzyColors.brandPrimary.copy(alpha = 0.14f) else Color.Transparent)
            .then(
                if (interactive) {
                    Modifier.tapAndDrag(onTap = onSelect, onDrag = onDrag, onDragEnd = onDragEnd)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (overlay.outline) {
            Text(
                text = overlay.text,
                style = style.copy(
                    color = Color.Black,
                    drawStyle = Stroke(width = 4f, join = StrokeJoin.Round),
                ),
            )
        }

        if (editable) {
            BasicTextField(
                value = overlay.text,
                onValueChange = onTextChange,
                textStyle = style,
            )
        } else {
            Text(text = overlay.text, style = style)
        }
    }
}

/**
 * A doodle layer.
 *
 * The points are normalised (0..1) so a stroke keeps its shape when the
 * canvas changes size, which means the shared [OverlayLayer] drawing branch
 * (it expects pixels) cannot be used here.
 */
@Composable
private fun PhotoDrawingLayer(
    overlay: Overlay.Drawing,
    selected: Boolean,
    canvasSize: IntSize,
    interactive: Boolean,
    onSelect: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val strokePx = with(density) { overlay.strokeWidth.dp.toPx() }
    val currentSize by rememberUpdatedState(canvasSize)

    Canvas(
        modifier = modifier
            .then(
                if (interactive) {
                    Modifier.pointerInput(overlay.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val size = currentSize
                            if (size.width <= 0 || size.height <= 0) return@awaitEachGesture
                            val hit = Offset(down.position.x / size.width, down.position.y / size.height)
                            if (!hitsStroke(hit, overlay.points, 28f * density.density / size.width)) {
                                return@awaitEachGesture
                            }
                            var dragged = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val delta = change.positionChange()
                                if (delta != Offset.Zero) {
                                    dragged = true
                                    change.consume()
                                    onDrag(delta)
                                }
                            }
                            if (!dragged) onSelect()
                            onDragEnd()
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        if (overlay.points.size < 2) return@Canvas
        val path = Path().apply {
            moveTo(overlay.points.first().x * size.width, overlay.points.first().y * size.height)
            overlay.points.drop(1).forEach { point ->
                lineTo(point.x * size.width, point.y * size.height)
            }
        }
        drawPath(
            path = path,
            color = overlay.color,
            style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** True when [point] is close enough to one of the stroke's [points]. */
private fun hitsStroke(point: Offset, points: List<Offset>, tolerance: Float): Boolean =
    points.any { stroke ->
        val dx = stroke.x - point.x
        val dy = stroke.y - point.y
        (dx * dx + dy * dy) <= tolerance * tolerance
    }

/** The font family used for a text layer. */
private fun fontFamilyFor(font: EditorFont): FontFamily = when (font) {
    EditorFont.DEFAULT, EditorFont.BOLD, EditorFont.ROUNDED -> FontFamily.Default
    EditorFont.SERIF -> FontFamily.Serif
    EditorFont.MONO -> FontFamily.Monospace
}

/**
 * Tap to select, drag to move.
 *
 * The shared overlay component only reports a drag, so the photo editor adds
 * its own gesture: a tap selects the layer, a drag moves it.
 */
private fun Modifier.tapAndDrag(
    onTap: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dragged = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val delta = change.positionChange()
            if (delta != Offset.Zero) {
                dragged = true
                change.consume()
                onDrag(delta)
            }
        }
        if (!dragged) onTap()
        onDragEnd()
    }
}
