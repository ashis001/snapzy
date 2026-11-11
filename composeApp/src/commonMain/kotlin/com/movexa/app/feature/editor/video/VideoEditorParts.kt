package com.movexa.app.feature.editor.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.movexa.app.core.comingsoon.GatedFeature
import com.movexa.app.core.comingsoon.rememberComingSoonController
import kotlinx.coroutines.launch
import com.movexa.app.core.designsystem.MovexaColors
import com.movexa.app.core.designsystem.MovexaShapes
import com.movexa.app.core.designsystem.MovexaType
import com.movexa.app.core.designsystem.ToolPanel
import com.movexa.app.core.editing.AudioKind
import com.movexa.app.core.editing.AudioTrack
import com.movexa.app.core.editing.EditorTool
import com.movexa.app.core.editing.MUSIC_CATALOGUE
import com.movexa.app.core.editing.Overlay
import com.movexa.app.core.editing.SFX_CATALOGUE
import com.movexa.app.core.editing.STICKER_CATALOGUE
import com.movexa.app.core.editing.TransitionKind
import com.movexa.app.core.editing.VideoClip
import com.movexa.app.core.media.loadThumbnail
import com.movexa.app.feature.editor.common.AdjustPanel
import com.movexa.app.feature.editor.common.AspectPanel
import com.movexa.app.feature.editor.common.AudioLibraryPanel
import com.movexa.app.feature.editor.common.BackgroundPanel
import com.movexa.app.feature.editor.common.BlendPanel
import com.movexa.app.feature.editor.common.DRAW_COLORS
import com.movexa.app.feature.editor.common.DrawPanel
import com.movexa.app.feature.editor.common.FilterPanel
import com.movexa.app.feature.editor.common.MaskPanel
import com.movexa.app.feature.editor.common.SpeedPanel
import com.movexa.app.feature.editor.common.StickerPanel
import com.movexa.app.feature.editor.common.TextPanel
import com.movexa.app.feature.editor.common.TransitionPanel
import com.movexa.app.feature.editor.common.VolumePanel

/** The shortest clip the editor will let you trim down to. */
internal const val MIN_CLIP_MS = 200L

/** Formats a duration as `m:ss`. */
internal fun formatMs(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/**
 * The clip strip at the bottom of the editor.
 *
 * Mirrors InShot: a coral add button on the left, a mute chip, then the
 * scrolling clip thumbnails with the playhead drawn over them and a time
 * ruler underneath.
 */
@Composable
internal fun VideoTimelineStrip(
    clips: List<VideoClip>,
    selectedIndex: Int,
    transition: TransitionKind,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onAddClip: (() -> Unit)? = null,
    onMuteToggle: (() -> Unit)? = null,
    isMuted: Boolean = false,
    positionMs: State<Long> = mutableStateOf(0L),
    clipOffsetMs: Long = 0L,
    totalMs: Long = 0L,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MovexaColors.bgBase)
            .drawBehind {
                drawLine(
                    color = MovexaColors.borderSubtle,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MovexaColors.accentDanger)
                    .clickable { onAddClip?.invoke() },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "+", style = MovexaType.title, color = Color.White)
            }

            Spacer(Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .clip(MovexaShapes.small)
                    .clickable { onMuteToggle?.invoke() }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (isMuted) "🔇" else "🔊",
                    style = MovexaType.body,
                    color = if (isMuted) MovexaColors.accentDanger else MovexaColors.textSecondary,
                )
                Text(
                    text = "Mute clip",
                    style = MovexaType.caption,
                    color = MovexaColors.textSecondary,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.width(6.dp))

            ClipStrip(
                clips = clips,
                selectedIndex = selectedIndex,
                transition = transition,
                positionMs = positionMs,
                clipOffsetMs = clipOffsetMs,
                totalMs = totalMs,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            )
        }

        TimeRulerRow(totalMs = totalMs, positionMs = positionMs, clipOffsetMs = clipOffsetMs)
    }
}

@Composable
private fun ClipStrip(
    clips: List<VideoClip>,
    selectedIndex: Int,
    transition: TransitionKind,
    positionMs: State<Long>,
    clipOffsetMs: Long,
    totalMs: Long,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var stripWidthPx by remember { mutableStateOf(1) }
    val playhead = clipOffsetMs + positionMs.value

    Box(
        modifier = modifier
            .height(56.dp)
            .onSizeChanged { stripWidthPx = it.width.coerceAtLeast(1) },
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxHeight(),
        ) {
            itemsIndexed(clips) { index, clip ->
                if (index > 0 && transition != TransitionKind.NONE) {
                    TransitionBadge(kind = transition)
                }
                Column(
                    modifier = Modifier
                        .width(52.dp)
                        .clickable { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(MovexaShapes.small)
                            .background(
                                if (index == selectedIndex) {
                                    MovexaColors.brandPrimary.copy(alpha = 0.32f)
                                } else {
                                    MovexaColors.bgSurfaceElevated
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        ClipThumbnail(clip.path)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatMs(clip.timelineDurationMs),
                        style = MovexaType.caption,
                        color = if (index == selectedIndex) {
                            MovexaColors.brandPrimary
                        } else {
                            MovexaColors.textSecondary
                        },
                        maxLines = 1,
                    )
                }
            }
        }

        if (totalMs > 0) {
            val x = (playhead.toFloat() / totalMs.toFloat()) * stripWidthPx
            Box(
                modifier = Modifier
                    .offset { IntOffset(x.toInt(), 0) }
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color.White),
            )
        }
    }
}

/** The chip between two clips that shows the transition they share. */
@Composable
private fun TransitionBadge(kind: TransitionKind) {
    Box(
        modifier = Modifier
            .width(16.dp)
            .height(36.dp)
            .clip(MovexaShapes.small)
            .background(MovexaColors.brandSecondary.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = kind.label.take(1),
            style = MovexaType.caption,
            color = MovexaColors.textPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ClipThumbnail(path: String) {
    var thumbnail by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        thumbnail = loadThumbnail("file://$path", 120, 120)
    }
    thumbnail?.let {
        androidx.compose.foundation.Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        )
    }
}

@Composable
private fun TimeRulerRow(totalMs: Long, positionMs: State<Long>, clipOffsetMs: Long) {
    val ticks = remember(totalMs) { buildRulerTicks(totalMs) }
    val playhead = clipOffsetMs + positionMs.value
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 128.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ticks.forEach { tick ->
                Text(
                    text = tick,
                    style = MovexaType.caption,
                    color = MovexaColors.textSecondary,
                    maxLines = 1,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 128.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatPreciseMs(playhead),
                style = MovexaType.caption,
                color = MovexaColors.brandPrimary,
                maxLines = 1,
            )
            Text(
                text = formatPreciseMs(totalMs),
                style = MovexaType.caption,
                color = MovexaColors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

/** Formats a duration as `m:ss.d`, the way the InShot timeline shows it. */
private fun formatPreciseMs(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val minutes = safe / 60_000
    val seconds = (safe % 60_000) / 1000
    val tenths = (safe % 1000) / 100
    return "$minutes:${seconds.toString().padStart(2, '0')}.$tenths"
}

private fun buildRulerTicks(totalMs: Long): List<String> {
    if (totalMs <= 0) return listOf("00:00")
    val stepMs = when {
        totalMs <= 10_000 -> 2_000L
        totalMs <= 30_000 -> 5_000L
        totalMs <= 120_000 -> 10_000L
        else -> 30_000L
    }
    val ticks = mutableListOf<String>()
    var t = 0L
    while (t <= totalMs && ticks.size < 5) {
        ticks += formatMs(t)
        t += stepMs
    }
    return ticks
}

/**
 * The trim scrubber for the selected clip.
 *
 * Lives inside the Trim panel, the way InShot's does — the handles stay close
 * to the frame they are trimming.
 */
@Composable
internal fun VideoTrimScrubber(
    sourceDurationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    positionMs: Long,
    onTrimStartChange: (Long) -> Unit,
    onTrimEndChange: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableStateOf(1) }
    val duration = sourceDurationMs.coerceAtLeast(1L)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .clip(MovexaShapes.small)
            .background(MovexaColors.bgBase)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val ms = ((offset.x / widthPx) * duration).toLong().coerceIn(0L, duration)
                    onSeek(ms)
                }
            },
    ) {
        fun msToPx(ms: Long): Float = (ms.toFloat() / duration.toFloat()) * widthPx

        // The kept region
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 6.dp)
                .offset { IntOffset(msToPx(trimStartMs).toInt(), 0) }
                .width((msToPx(trimEndMs) - msToPx(trimStartMs)).coerceAtLeast(2f).dp)
                .background(MovexaColors.brandPrimary.copy(alpha = 0.25f)),
        )

        // The playhead
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .offset { IntOffset(msToPx(positionMs).toInt(), 0) }
                .width(2.dp)
                .background(MovexaColors.accentSuccess),
        )

        TrimHandle(
            xPx = msToPx(trimStartMs),
            onDrag = { deltaPx ->
                val next = trimStartMs + ((deltaPx / widthPx) * duration).toLong()
                onTrimStartChange(next.coerceIn(0L, trimEndMs - MIN_CLIP_MS))
            },
        )
        TrimHandle(
            xPx = msToPx(trimEndMs),
            onDrag = { deltaPx ->
                val next = trimEndMs + ((deltaPx / widthPx) * duration).toLong()
                onTrimEndChange(next.coerceIn(trimStartMs + MIN_CLIP_MS, duration))
            },
        )
    }
}

@Composable
private fun TrimHandle(
    xPx: Float,
    onDrag: (Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(xPx.toInt() - 8, 0) }
            .width(16.dp)
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .clip(MovexaShapes.small)
            .background(MovexaColors.brandPrimary)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x)
                }
            },
    )
}

/**
 * The panel for the open tool.
 *
 * Everything here mutates the project through the session, so undo and redo
 * cover the whole screen. Tools that need a backend or a GPU shader we do not
 * have open the shared Coming Soon sheet instead of pretending to work.
 */
@Composable
internal fun VideoToolPanel(
    tool: EditorTool,
    session: VideoEditSession,
    selectedClipIndex: Int,
    selectedOverlayId: Long?,
    positionMs: State<Long>,
    drawColor: Color,
    onDrawColorChange: (Color) -> Unit,
    drawStrokeWidth: Float,
    onDrawStrokeWidthChange: (Float) -> Unit,
) {
    val comingSoon = rememberComingSoonController()
    val scope = rememberCoroutineScope()
    val project = session.project
    val clip = project.clips.getOrNull(selectedClipIndex)
    val playhead = positionMs.value
    val pip = project.overlays.filterIsInstance<Overlay.Pip>()
        .firstOrNull { it.id == selectedOverlayId }
        ?: project.overlays.filterIsInstance<Overlay.Pip>().firstOrNull()

    when (tool) {
        EditorTool.TRIM -> ToolPanel(title = "Trim") {
            if (clip == null) {
                PanelNote("Select a clip to trim")
            } else {
                VideoTrimScrubber(
                    sourceDurationMs = clip.sourceDurationMs,
                    trimStartMs = clip.trimStartMs,
                    trimEndMs = clip.trimEndMs,
                    positionMs = playhead,
                    onTrimStartChange = { ms ->
                        session.updateClip(selectedClipIndex) { current ->
                            current.copy(trimStartMs = ms.coerceIn(0L, current.trimEndMs - MIN_CLIP_MS))
                        }
                    },
                    onTrimEndChange = { ms ->
                        session.updateClip(selectedClipIndex) { current ->
                            current.copy(
                                trimEndMs = ms.coerceIn(current.trimStartMs + MIN_CLIP_MS, current.sourceDurationMs),
                            )
                        }
                    },
                    onSeek = { ms -> session.seek(ms) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelAction("Set in @ ${formatMs(playhead)}") {
                        session.updateClip(selectedClipIndex) { current ->
                            current.copy(trimStartMs = playhead.coerceIn(0L, current.trimEndMs - MIN_CLIP_MS))
                        }
                    }
                    PanelAction("Set out @ ${formatMs(playhead)}") {
                        session.updateClip(selectedClipIndex) { current ->
                            current.copy(
                                trimEndMs = playhead.coerceIn(current.trimStartMs + MIN_CLIP_MS, current.sourceDurationMs),
                            )
                        }
                    }
                    PanelAction("Reset") {
                        session.updateClip(selectedClipIndex) { current ->
                            current.copy(trimStartMs = 0L, trimEndMs = current.sourceDurationMs)
                        }
                    }
                }
            }
        }

        EditorTool.SPEED -> if (clip == null) {
            ToolPanel(title = "Speed") { PanelNote("Select a clip") }
        } else {
            SpeedPanel(
                speed = clip.speed,
                onSpeedChange = { speed ->
                    session.updateClip(selectedClipIndex) { it.copy(speed = speed) }
                },
            )
        }

        EditorTool.VOLUME -> if (clip == null) {
            ToolPanel(title = "Volume") { PanelNote("Select a clip") }
        } else {
            VolumePanel(
                volume = clip.volume,
                muted = clip.muted,
                onVolumeChange = { volume ->
                    session.updateClip(selectedClipIndex) { it.copy(volume = volume) }
                },
                onMuteToggle = {
                    session.commitClip(selectedClipIndex) { it.copy(muted = !it.muted) }
                },
            )
        }

        EditorTool.FILTER -> if (clip == null) {
            ToolPanel(title = "Filter") { PanelNote("Select a clip") }
        } else {
            FilterPanel(
                selected = clip.filter,
                onSelect = { filter ->
                    session.commitClip(selectedClipIndex) { it.copy(filter = filter) }
                },
            )
        }

        EditorTool.ADJUST -> if (clip == null) {
            ToolPanel(title = "Adjust") { PanelNote("Select a clip") }
        } else {
            AdjustPanel(
                adjust = clip.adjust,
                onChange = { adjust ->
                    session.updateClip(selectedClipIndex) { it.copy(adjust = adjust) }
                },
            )
        }

        EditorTool.TEXT -> {
            val selectedText = project.overlays
                .filterIsInstance<Overlay.Text>()
                .firstOrNull { it.id == selectedOverlayId }
            val textId = selectedText?.id
            TextPanel(
                onAddText = { session.addText() },
                selectedText = selectedText,
                onColorChange = { color ->
                    val id = textId ?: return@TextPanel
                    session.updateOverlay(id) { (it as Overlay.Text).copy(color = color) }
                },
                onSizeChange = { size ->
                    val id = textId ?: return@TextPanel
                    session.updateOverlay(id) { (it as Overlay.Text).copy(fontSizeSp = size) }
                },
                onBoldToggle = {
                    val id = textId ?: return@TextPanel
                    session.commitOverlay(id) { (it as Overlay.Text).copy(bold = !(it as Overlay.Text).bold) }
                },
                onOutlineToggle = {
                    val id = textId ?: return@TextPanel
                    session.commitOverlay(id) { (it as Overlay.Text).copy(outline = !(it as Overlay.Text).outline) }
                },
                onShadowToggle = {
                    val id = textId ?: return@TextPanel
                    session.commitOverlay(id) { (it as Overlay.Text).copy(shadow = !(it as Overlay.Text).shadow) }
                },
                onDelete = {
                    val id = selectedOverlayId ?: return@TextPanel
                    session.deleteOverlay(id)
                },
            )
        }

        EditorTool.STICKER -> StickerPanel(
            stickers = STICKER_CATALOGUE,
            onPick = { sticker -> session.addSticker(sticker) },
        )

        EditorTool.DRAW -> DrawPanel(
            colors = DRAW_COLORS,
            selectedColor = drawColor,
            onColorChange = onDrawColorChange,
            strokeWidth = drawStrokeWidth,
            onStrokeWidthChange = onDrawStrokeWidthChange,
            onClear = { session.clearDrawings() },
        )

        EditorTool.MUSIC -> Column {
            AudioLibraryPanel(
                title = "Music",
                items = MUSIC_CATALOGUE,
                onPick = { item -> scope.launch { session.addAudioTrack(item, AudioKind.MUSIC) } },
            )
            AudioLibraryPanel(
                title = "Sound effects",
                items = SFX_CATALOGUE,
                onPick = { item -> scope.launch { session.addAudioTrack(item, AudioKind.SOUND_EFFECT) } },
            )
            if (project.audioTracks.isNotEmpty()) {
                ToolPanel(title = "Tracks") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        project.audioTracks.forEach { track ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${track.emojiForKind()}  ${track.label}",
                                    style = MovexaType.label,
                                    color = MovexaColors.textPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                )
                                PanelAction("Remove") { session.removeAudioTrack(track.id) }
                            }
                        }
                    }
                }
            }
        }

        EditorTool.RATIO -> AspectPanel(
            selected = project.canvas.aspect,
            onSelect = { aspect ->
                session.commit { current ->
                    current.copy(canvas = current.canvas.copy(aspect = aspect))
                }
            },
        )

        EditorTool.BACKGROUND -> BackgroundPanel(
            selected = project.canvas.background,
            onSelect = { background ->
                session.commit { current ->
                    current.copy(canvas = current.canvas.copy(background = background))
                }
            },
        )

        EditorTool.TRANSITION -> TransitionPanel(
            selected = project.transition,
            onSelect = { kind ->
                session.commit { current -> current.copy(transition = kind) }
            },
        )

        EditorTool.ROTATE -> ToolPanel(title = "Rotate") {
            if (clip == null) {
                PanelNote("Select a clip")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PanelAction("Rotate 90°") {
                        session.commitClip(selectedClipIndex) { it.copy(rotation = (it.rotation + 90) % 360) }
                    }
                    PanelAction("Reset") {
                        session.commitClip(selectedClipIndex) { it.copy(rotation = 0) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Current rotation: ${clip.rotation}°",
                    style = MovexaType.caption,
                    color = MovexaColors.textSecondary,
                )
            }
        }

        EditorTool.PIP -> ToolPanel(title = "Picture in picture") {
            if (project.clips.isEmpty()) {
                PanelNote("Pick a video first")
            } else {
                Text(
                    text = "Add one of your clips as a floating layer.",
                    style = MovexaType.caption,
                    color = MovexaColors.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    project.clips.forEachIndexed { index, source ->
                        PanelAction("Clip ${index + 1}") { session.addPipLayer(source) }
                    }
                }
            }
        }

        EditorTool.MASK -> if (pip == null) {
            ToolPanel(title = "Mask") { PanelNote("Add a picture-in-picture layer first") }
        } else {
            MaskPanel(
                selected = pip.maskShape,
                onSelect = { shape ->
                    session.updateOverlay(pip.id) { (it as Overlay.Pip).copy(maskShape = shape) }
                },
            )
        }

        EditorTool.BLEND -> if (pip == null) {
            ToolPanel(title = "Blend") { PanelNote("Add a picture-in-picture layer first") }
        } else {
            BlendPanel(
                selected = pip.blend,
                onSelect = { mode ->
                    session.updateOverlay(pip.id) { (it as Overlay.Pip).copy(blend = mode) }
                },
            )
        }

        EditorTool.SPLIT -> ToolPanel(title = "Split") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PanelAction("Split here") {
                    session.splitClip(selectedClipIndex, playhead)
                }
                PanelAction("Duplicate") {
                    session.duplicateClip(selectedClipIndex)
                }
                PanelAction("Delete") {
                    session.deleteClip(selectedClipIndex)
                }
            }
        }

        EditorTool.SWAP -> ToolPanel(title = "Swap") {
            if (project.clips.size < 2) {
                PanelNote("Add a second clip to swap with")
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (selectedClipIndex > 0) {
                        PanelAction("Swap with previous") {
                            session.swapClips(selectedClipIndex, selectedClipIndex - 1)
                        }
                    }
                    if (selectedClipIndex < project.clips.lastIndex) {
                        PanelAction("Swap with next") {
                            session.swapClips(selectedClipIndex, selectedClipIndex + 1)
                        }
                    }
                }
            }
        }

        EditorTool.FREEZE -> ToolPanel(title = "Freeze") {
            Text(
                text = "Hold the frame at ${formatMs(playhead)} for half a second.",
                style = MovexaType.caption,
                color = MovexaColors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            PanelAction("Freeze at playhead") {
                session.freezeAtPlayhead(selectedClipIndex, playhead)
            }
        }

        EditorTool.REVERSE -> ToolPanel(title = "Reverse") {
            Text(
                text = "Play the timeline backwards.",
                style = MovexaType.caption,
                color = MovexaColors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            PanelAction("Reverse timeline") { session.reverseTimeline() }
        }

        EditorTool.APPLY_ALL -> ToolPanel(title = "Apply to all clips") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PanelAction("Copy look to all") {
                    session.applyLookToAllClips(selectedClipIndex)
                }
            }
        }

        // Tools that need a backend or a shader we do not have.
        EditorTool.EFFECTS -> comingSoon.show(GatedFeature.EFFECTS_TOOL)
        EditorTool.ANIMATION -> comingSoon.show(GatedFeature.ANIMATION_TOOL)
        EditorTool.KEYFRAME -> comingSoon.show(GatedFeature.KEYFRAME_TOOL)
        EditorTool.CHROMA -> comingSoon.show(GatedFeature.CHROMA_TOOL)
        EditorTool.MOSAIC -> comingSoon.show(GatedFeature.MOSAIC_TOOL)
        EditorTool.TRACKING -> comingSoon.show(GatedFeature.TRACKING)
        EditorTool.AI_CUT -> comingSoon.show(GatedFeature.AI_CUT)
        EditorTool.CAPTIONS -> comingSoon.show(GatedFeature.AUTO_CAPTIONS)
        EditorTool.AI_SPEECH -> comingSoon.show(GatedFeature.VOICEOVER)
        EditorTool.ENHANCE -> comingSoon.show(GatedFeature.AI_ENHANCE)
        EditorTool.CUTOUT -> comingSoon.show(GatedFeature.AI_ERASER)
        EditorTool.STABILIZE -> comingSoon.show(GatedFeature.STABILIZER)

        // Tools without a shared Coming Soon entry keep an inline placeholder.
        EditorTool.VOICE,
        EditorTool.CROP,
        EditorTool.DURATION,
        EditorTool.RECORD,
        EditorTool.EXTRACT_AUDIO,
        EditorTool.TELEPROMPTER,
        EditorTool.DRAFT,
        -> ToolPanel(title = tool.label) {
            PanelNote("Coming soon — ${tool.label} is not available in this build yet")
        }

        // Photo-only tools have no meaning here.
        else -> ToolPanel(title = tool.label) {
            PanelNote("Coming soon — ${tool.label} is not available in this build yet")
        }
    }
}

/**
 * The transport row: undo and redo on the left, play/pause in the middle, and
 * a fullscreen affordance on the right.
 *
 * While a tool panel is open the right-hand button becomes a brand-coloured
 * Done button that closes it — the same affordance InShot uses.
 */
@Composable
internal fun TransportRow(
    canUndo: Boolean,
    canRedo: Boolean,
    isPlaying: Boolean,
    panelOpen: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onPlayToggle: () -> Unit,
    onClosePanel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(symbol = "↶", enabled = canUndo, onClick = onUndo)
        Spacer(Modifier.width(12.dp))
        TransportButton(symbol = "↷", enabled = canRedo, onClick = onRedo)

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MovexaColors.bgSurfaceElevated)
                .clickable(onClick = onPlayToggle),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isPlaying) "❚❚" else "▶",
                style = MovexaType.body,
                color = Color.White,
            )
        }

        Spacer(Modifier.weight(1f))

        if (panelOpen) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MovexaColors.brandPrimary)
                    .clickable(onClick = onClosePanel),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "✓", style = MovexaType.body, color = MovexaColors.textOnBrand)
            }
        } else {
            TransportButton(symbol = "⛶", enabled = true, onClick = {})
        }
    }
}

@Composable
private fun TransportButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = MovexaType.title,
            color = if (enabled) MovexaColors.textPrimary else MovexaColors.divider,
            textAlign = TextAlign.Center,
        )
    }
}

/** A tappable action inside a tool panel. */
@Composable
private fun PanelAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MovexaType.label,
        color = MovexaColors.brandPrimary,
        modifier = Modifier
            .clip(MovexaShapes.pill)
            .background(MovexaColors.bgBase)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Placeholder line shown when a panel has nothing to act on. */
@Composable
private fun PanelNote(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MovexaType.body,
            color = MovexaColors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** Emoji used in front of an audio track in the track list. */
private fun AudioTrack.emojiForKind(): String = when (kind) {
    AudioKind.MUSIC -> "♪"
    AudioKind.SOUND_EFFECT -> "✦"
    AudioKind.VOICEOVER -> "◉"
}
