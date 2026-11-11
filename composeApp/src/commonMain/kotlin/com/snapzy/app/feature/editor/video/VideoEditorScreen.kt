package com.snapzy.app.feature.editor.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snapzy.app.core.designsystem.EditorToolBar
import com.snapzy.app.core.designsystem.SnapzyColors
import com.snapzy.app.core.designsystem.SnapzyShapes
import com.snapzy.app.core.designsystem.SnapzyType
import com.snapzy.app.core.editing.AspectRatio
import com.snapzy.app.core.editing.BackgroundStyle
import com.snapzy.app.core.editing.CanvasSpec
import com.snapzy.app.core.editing.EditKind
import com.snapzy.app.core.editing.EditProject
import com.snapzy.app.core.editing.EditorTool
import com.snapzy.app.core.editing.LookGrade
import com.snapzy.app.core.editing.FilterPreset
import com.snapzy.app.core.editing.Overlay
import com.snapzy.app.core.editing.VIDEO_TOOLS
import com.snapzy.app.core.editing.VideoClip
import com.snapzy.app.core.editing.filterBrightness
import com.snapzy.app.core.editing.filterTint
import com.snapzy.app.core.editing.withRecomputedDuration
import com.snapzy.app.core.media.EditorSessionStore
import com.snapzy.app.core.storage.DraftAutosave
import kotlinx.coroutines.launch
import com.snapzy.app.core.media.PickedMedia
import com.snapzy.app.core.media.VideoPlayerSurface
import com.snapzy.app.core.media.supportsPreviewColourGrade
import com.snapzy.app.feature.editor.common.DRAW_COLORS
import com.snapzy.app.feature.editor.common.OverlayLayer
import com.snapzy.app.feature.editor.common.movedBy
import com.snapzy.app.feature.editor.common.offsetIn
import kotlinx.coroutines.delay

/**
 * The video editor.
 *
 * The layout follows InShot exactly: a header row, the preview canvas that
 * takes all the space that is left, the transport row, the panel of the open
 * tool, the scrolling tool strip and — always last — the timeline pinned to
 * the bottom of the screen.
 *
 * Every edit goes through [VideoEditSession], so undo and redo cover the
 * whole screen.
 */
@Composable
fun VideoEditorScreen(onBack: () -> Unit, onExport: () -> Unit) {
    val sources = editorSources()
    if (sources.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SnapzyColors.bgBase),
        )
        return
    }
    VideoEditorContent(sources = sources, onBack = onBack, onExport = onExport)
}

/** The videos the user picked for this edit. */
private fun editorSources(): List<PickedMedia.Video> {
    val single = EditorSessionStore.pickedVideo
    val many = EditorSessionStore.pickedVideos
    return if (many.isNotEmpty()) many else listOfNotNull(single)
}

@Composable
private fun VideoEditorContent(
    sources: List<PickedMedia.Video>,
    onBack: () -> Unit,
    onExport: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val session = remember(sources) {
        val reopened = EditorSessionStore.draftProject
        EditorSessionStore.draftProject = null
        VideoEditSession(reopened ?: initialVideoProject(sources), sources)
    }
    val project = session.project

    // Every change is written out as a draft, so the edit survives leaving
    // the app and shows up again on the home screen.
    LaunchedEffect(project) {
        DraftAutosave.saveAfterPause(project, DraftAutosave.title(project))
    }

    var activeTool by remember { mutableStateOf<EditorTool?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    // The playhead moves ten times a second, so it lives in its own state:
    // only the timeline and the open panel read it, the rest of the screen
    // stays untouched while the video plays.
    val position = remember { mutableStateOf(0L) }
    var seekRequestMs by remember { mutableStateOf<Long?>(null) }
    var drawColor by remember { mutableStateOf(DRAW_COLORS.first()) }
    var drawStrokeWidth by remember { mutableStateOf(8f) }
    var strokeInProgress by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasWidthPx by remember { mutableStateOf(1) }
    var canvasHeightPx by remember { mutableStateOf(1) }
    var showHelp by remember { mutableStateOf(false) }

    val clips = project.clips
    val selectedClipIndex = session.selectedClipIndex.coerceIn(0, clips.lastIndex.coerceAtLeast(0))
    val selectedClip = clips.getOrNull(selectedClipIndex)
    val canvasRatio = project.canvas.aspect.ratio ?: DEFAULT_ASPECT_RATIO

    // A stroke is committed when the gesture ends, so one stroke is one undo entry.
    val commitStroke: () -> Unit = {
        val points = strokeInProgress
        strokeInProgress = emptyList()
        if (points.size >= 2) {
            session.commit { current ->
                current.copy(
                    overlays = current.overlays + Overlay.Drawing(
                        id = session.nextId(),
                        points = points,
                        color = drawColor,
                        strokeWidth = drawStrokeWidth,
                    ),
                )
            }
        }
    }

    // The player only understands one file at a time, so it always plays the
    // selected clip and the playhead is relative to that clip.
    LaunchedEffect(selectedClip?.id) {
        isPlaying = false
        val start = selectedClip?.trimStartMs ?: 0L
        position.value = start
        seekRequestMs = start
    }

    // A seek requested by a tool panel (the trim scrubber) drives the player.
    LaunchedEffect(session.seekRequestMs) {
        val target = session.seekRequestMs ?: return@LaunchedEffect
        position.value = target
        seekRequestMs = target
        session.consumeSeek()
    }

    // Clearing the request lets the next seek to the same position trigger again.
    LaunchedEffect(seekRequestMs) {
        if (seekRequestMs != null) {
            delay(150)
            seekRequestMs = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SnapzyColors.bgBase)
            .statusBarsPadding(),
    ) {
        VideoHeaderRow(
            onBack = {
                // The draft is written out before leaving, so nothing is lost.
                scope.launch { DraftAutosave.save(project, DraftAutosave.title(project)) }
                onBack()
            },
            onExport = {
                EditorSessionStore.project = session.project
                scope.launch { DraftAutosave.save(project, DraftAutosave.title(project)) }
                onExport()
            },
            showHelp = showHelp,
            onToggleHelp = { showHelp = !showHelp },
        )

        if (showHelp) {
            HelpStrip()
        }

        // The preview is graded with the same matrix the export uses.
        val lookGrade = remember(selectedClip?.filter, selectedClip?.adjust) {
            val clip = selectedClip
            if (clip == null) null else LookGrade(clip.filter, clip.adjust)
        }
        val previewGradesItsOwnFrames = supportsPreviewColourGrade()

        // The canvas owns all the vertical space that is left.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(canvasRatio, matchHeightConstraintsFirst = true)
                    .clip(SnapzyShapes.small)
                    .background(canvasBackgroundColor(project.canvas.background))
                    .onSizeChanged {
                        canvasWidthPx = it.width.coerceAtLeast(1)
                        canvasHeightPx = it.height.coerceAtLeast(1)
                    },
            ) {
                selectedClip?.let { clip ->
                    VideoPlayerSurface(
                        filePath = clip.path,
                        isPlaying = isPlaying,
                        seekToMs = seekRequestMs,
                        onPositionChanged = { playhead, _ ->
                            position.value = playhead
                            val end = clip.trimEndMs
                            if (isPlaying && playhead >= end) isPlaying = false
                        },
                        modifier = Modifier.fillMaxSize(),
                        lookGrade = lookGrade,
                    )
                }

                // Where the platform cannot colour-grade the preview itself,
                // the grade is approximated with a tint so the user still gets
                // feedback while they drag the sliders.
                if (!previewGradesItsOwnFrames) {
                    gradeTints(selectedClip).forEach { (color, alpha) ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(color.copy(alpha = alpha)),
                        )
                    }
                }

                DrawingLayer(
                    drawings = project.overlays.filterIsInstance<Overlay.Drawing>(),
                    strokeInProgress = strokeInProgress,
                    strokeColor = drawColor,
                    strokeWidth = drawStrokeWidth,
                    modifier = Modifier.fillMaxSize(),
                )

                project.overlays.filterNot { it is Overlay.Drawing }.forEach { overlay ->
                    OverlayLayer(
                        overlay = overlay,
                        selected = overlay.id == session.selectedOverlayId,
                        onSelect = { session.selectOverlay(overlay.id) },
                        onDrag = { delta ->
                            session.updateOverlay(overlay.id) { current ->
                                current.movedBy(
                                    delta.x / canvasWidthPx.toFloat(),
                                    delta.y / canvasHeightPx.toFloat(),
                                )
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset { overlay.offsetIn(canvasWidthPx, canvasHeightPx) },
                    )
                }

                if (activeTool == EditorTool.DRAW) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        strokeInProgress = listOf(offset.normalised(canvasWidthPx, canvasHeightPx))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        strokeInProgress = strokeInProgress +
                                            change.position.normalised(canvasWidthPx, canvasHeightPx)
                                    },
                                    onDragEnd = { commitStroke() },
                                    onDragCancel = { commitStroke() },
                                )
                            },
                    )
                }

                // The active look, spelled out because the preview can only
                // approximate it.
                LookBadge(
                    clip = selectedClip,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            }
        }

        // Transport controls sit directly under the preview, the way InShot
        // does it: undo/redo on the left, play in the middle, and either a
        // fullscreen affordance or a Done button that closes the open panel.
        TransportRow(
            canUndo = session.controller.canUndo,
            canRedo = session.controller.canRedo,
            isPlaying = isPlaying,
            panelOpen = activeTool != null,
            onUndo = { session.controller.undo() },
            onRedo = { session.controller.redo() },
            onPlayToggle = { isPlaying = !isPlaying },
            onClosePanel = { activeTool = null },
        )

        // The open tool's panel sits between the transport row and the
        // tool strip. It is capped in height so a tall panel (adjust, stickers)
        // can never push the timeline off the screen.
        activeTool?.let { tool ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = PANEL_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
            ) {
                VideoToolPanel(
                    tool = tool,
                    session = session,
                    selectedClipIndex = selectedClipIndex,
                    selectedOverlayId = session.selectedOverlayId,
                    positionMs = position,
                    drawColor = drawColor,
                    onDrawColorChange = { drawColor = it },
                    drawStrokeWidth = drawStrokeWidth,
                    onDrawStrokeWidthChange = { drawStrokeWidth = it },
                )
            }
        }

        EditorToolBar(
            tools = VIDEO_TOOLS,
            active = activeTool,
            onSelect = { tool ->
                activeTool = if (activeTool == tool) null else tool
                strokeInProgress = emptyList()
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // The timeline is always the last element, pinned to the bottom.
        VideoTimelineStrip(
            clips = clips,
            selectedIndex = selectedClipIndex,
            transition = project.transition,
            onSelect = { index ->
                session.selectClip(index)
                session.selectOverlay(null)
            },
            onAddClip = { session.addNextSourceClip() },
            positionMs = position,
            clipOffsetMs = clips.take(selectedClipIndex).sumOf { it.timelineDurationMs },
            totalMs = project.durationMs.coerceAtLeast(1L),
            isMuted = selectedClip?.muted ?: false,
            onMuteToggle = {
                session.commitClip(selectedClipIndex) { it.copy(muted = !it.muted) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* ----------------------------------------------------------------- pieces -- */

/**
 * The header row: back on the left, a help toggle and the export action.
 */
@Composable
private fun VideoHeaderRow(
    onBack: () -> Unit,
    onExport: () -> Unit,
    showHelp: Boolean,
    onToggleHelp: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SnapzyColors.bgBase)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(SnapzyShapes.pill)
                .clickable(onClick = onBack)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(text = "‹", style = SnapzyType.title, color = SnapzyColors.textPrimary)
        }

        Spacer(Modifier.width(4.dp))

        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(SnapzyColors.bgSurfaceElevated)
                .clickable(onClick = onToggleHelp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "?",
                style = SnapzyType.caption,
                color = if (showHelp) SnapzyColors.brandPrimary else SnapzyColors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = "Export",
            style = SnapzyType.body,
            color = SnapzyColors.brandPrimary,
            modifier = Modifier
                .clip(SnapzyShapes.pill)
                .clickable(onClick = onExport)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** The hint list behind the help button. */
@Composable
private fun HelpStrip() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SnapzyColors.bgSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        HELP_HINTS.forEach { hint ->
            Text(
                text = "· $hint",
                style = SnapzyType.caption,
                color = SnapzyColors.textSecondary,
            )
        }
    }
}

/**
 * Draws the freehand strokes.
 *
 * `Overlay.Drawing.points` are normalised to the canvas (0..1 on both axes) —
 * the same space the export renderer uses — so they are mapped back to pixels
 * here.
 */
@Composable
private fun DrawingLayer(
    drawings: List<Overlay.Drawing>,
    strokeInProgress: List<Offset>,
    strokeColor: Color,
    strokeWidth: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawings.forEach { drawing ->
            drawDrawing(drawing.points, drawing.color, drawing.strokeWidth)
        }
        if (strokeInProgress.size >= 2) {
            drawDrawing(strokeInProgress, strokeColor, strokeWidth)
        }
    }
}

private fun DrawScope.drawDrawing(points: List<Offset>, color: Color, width: Float) {
    if (points.size < 2) return
    val path = Path().apply {
        points.forEachIndexed { index, point ->
            val x = point.x * size.width
            val y = point.y * size.height
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = width.dp.toPx(), cap = StrokeCap.Round),
    )
}

/** The chip on the canvas that spells out the active look. */
@Composable
private fun LookBadge(clip: VideoClip?, modifier: Modifier = Modifier) {
    val summary = lookSummary(clip)
    Text(
        text = when {
            summary == null -> "Look · none"
            supportsPreviewColourGrade() -> "Look · $summary"
            else -> "Look (approx.) · $summary"
        },
        style = SnapzyType.caption,
        color = if (summary == null) SnapzyColors.textSecondary else SnapzyColors.brandPrimary,
        modifier = modifier
            .clip(SnapzyShapes.pill)
            .background(SnapzyColors.bgCanvas.copy(alpha = 0.75f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Human readable summary of the filter and adjustments of a clip. */
private fun lookSummary(clip: VideoClip?): String? {
    if (clip == null) return null
    val parts = mutableListOf<String>()
    if (clip.filter != FilterPreset.ORIGINAL) parts += clip.filter.label
    val adjust = clip.adjust
    if (adjust.brightness != 0f) parts += "B ${adjust.brightness.signed()}"
    if (adjust.contrast != 1f) parts += "C ${adjust.contrast.plain()}"
    if (adjust.saturation != 1f) parts += "S ${adjust.saturation.plain()}"
    if (adjust.hue != 0f) parts += "H ${adjust.hue.toInt()}°"
    if (adjust.temperature != 0f) parts += "T ${adjust.temperature.signed()}"
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * The translucent tints that approximate the colour grade on the preview.
 *
 * The player is a native surface, so a `ColorFilter` cannot reach the video
 * pixels — this is an approximation and the badge says so.
 */
private fun gradeTints(clip: VideoClip?): List<Pair<Color, Float>> {
    if (clip == null) return emptyList()
    val tints = mutableListOf<Pair<Color, Float>>()

    val brightness = clip.adjust.brightness + filterBrightness(clip.filter)
    if (brightness > 0f) {
        tints += Color.White to (brightness * 0.55f).coerceIn(0f, 0.32f)
    } else if (brightness < 0f) {
        tints += Color.Black to (-brightness * 0.6f).coerceIn(0f, 0.45f)
    }

    if (clip.filter == FilterPreset.MONO) {
        tints += Color.Gray to 0.3f
    }

    filterTint(clip.filter)?.let { tint ->
        val color = Color(
            red = tint[0].coerceIn(0f, 1f),
            green = tint[1].coerceIn(0f, 1f),
            blue = tint[2].coerceIn(0f, 1f),
        )
        tints += color to 0.16f
    }

    return tints.filter { it.second > 0.01f }
}

/** The colour the canvas is painted with behind the video. */
private fun canvasBackgroundColor(style: BackgroundStyle): Color = when (style) {
    BackgroundStyle.BLACK -> Color.Black
    BackgroundStyle.WHITE -> Color.White
    BackgroundStyle.BLUR -> SnapzyColors.bgCanvas
    BackgroundStyle.CUSTOM -> SnapzyColors.bgOverlay
}

/* ---------------------------------------------------------------- helpers -- */

/** Builds the starting project from the picked videos. */
private fun initialVideoProject(sources: List<PickedMedia.Video>): EditProject = EditProject(
    kind = EditKind.VIDEO,
    clips = sources.mapIndexed { index, source ->
        VideoClip(
            id = index + 1L,
            path = source.filePath,
            sourceDurationMs = source.durationMs,
            trimStartMs = 0L,
            trimEndMs = source.durationMs,
        )
    },
    canvas = CanvasSpec(aspect = AspectRatio.PORTRAIT),
).withRecomputedDuration()

/** Normalises a pixel position inside the canvas to 0..1 on both axes. */
private fun Offset.normalised(width: Int, height: Int): Offset = Offset(
    x = (x / width.toFloat()).coerceIn(0f, 1f),
    y = (y / height.toFloat()).coerceIn(0f, 1f),
)

private fun Float.signed(): String = if (this > 0f) "+${plain()}" else plain()

private fun Float.plain(): String {
    val rounded = (this * 100f).toInt() / 100f
    return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
}

/** The canvas ratio used when the project does not pin one down. */
private const val DEFAULT_ASPECT_RATIO = 9f / 16f

/** The tallest a tool panel may get before it starts scrolling. */
private val PANEL_MAX_HEIGHT = 280.dp

/** The hints behind the help button. */
private val HELP_HINTS = listOf(
    "Tap a tool below to open its panel, tap ✓ to close it again.",
    "Drag the handles in the Trim panel to set the in and out points.",
    "Drag a text, sticker or doodle on the preview to move it.",
    "Open Draw and drag on the preview to doodle.",
)
