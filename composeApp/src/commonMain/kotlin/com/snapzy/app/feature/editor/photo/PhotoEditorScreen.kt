package com.snapzy.app.feature.editor.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snapzy.app.core.comingsoon.GatedFeature
import com.snapzy.app.core.comingsoon.rememberComingSoonController
import com.snapzy.app.core.designsystem.EditorToolBar
import com.snapzy.app.core.designsystem.SnapzyColors
import com.snapzy.app.core.designsystem.SnapzyShapes
import com.snapzy.app.core.designsystem.SnapzyType
import com.snapzy.app.core.editing.AdjustSpec
import com.snapzy.app.core.editing.AspectRatio
import com.snapzy.app.core.editing.BackgroundStyle
import com.snapzy.app.core.editing.BlendMode
import com.snapzy.app.core.editing.EditKind
import com.snapzy.app.core.editing.EditProject
import com.snapzy.app.core.editing.EditorController
import com.snapzy.app.core.editing.EditorFont
import com.snapzy.app.core.editing.EditorTool
import com.snapzy.app.core.editing.FilterPreset
import com.snapzy.app.core.editing.MaskShape
import com.snapzy.app.core.editing.Overlay
import com.snapzy.app.core.editing.PHOTO_TOOLS
import com.snapzy.app.core.editing.StickerItem
import com.snapzy.app.core.media.EditorSessionStore
import com.snapzy.app.core.storage.DraftAutosave
import com.snapzy.app.core.media.decodeImageBitmap
import com.snapzy.app.core.media.encodeImageBitmapToJpeg
import com.snapzy.app.feature.editor.common.AdjustPanel
import com.snapzy.app.feature.editor.common.AspectPanel
import com.snapzy.app.feature.editor.common.BackgroundPanel
import com.snapzy.app.feature.editor.common.BlendPanel
import com.snapzy.app.feature.editor.common.DRAW_COLORS
import com.snapzy.app.feature.editor.common.DrawPanel
import com.snapzy.app.feature.editor.common.FilterPanel
import com.snapzy.app.feature.editor.common.MaskPanel
import com.snapzy.app.feature.editor.common.StickerPanel
import com.snapzy.app.feature.editor.common.TextPanel
import com.snapzy.app.feature.editor.common.movedBy
import com.snapzy.app.core.editing.STICKER_CATALOGUE
import com.snapzy.app.core.editing.buildLookMatrix
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The photo editor.
 *
 * Layout follows InShot: the preview owns the space, the transport row sits
 * under it, the open tool's panel sits under the transport row and the tool
 * strip is always the last row. There is no timeline — a photo is a single
 * still.
 */
@Composable
fun PhotoEditorScreen(onBack: () -> Unit, onExport: () -> Unit) {
    val picked = EditorSessionStore.pickedImage
    if (picked == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val controller = remember(picked) {
        EditorController(
            EditProject(
                kind = EditKind.PHOTO,
                imageBytes = picked.bytes,
                clips = listOf(photoStillClip()),
            )
        )
    }
    val project = controller.project

    // Every change is written out as a draft, so the edit survives leaving
    // the app and shows up again on the home screen.
    LaunchedEffect(project) {
        DraftAutosave.saveAfterPause(project, DraftAutosave.title(project))
    }
    val bitmap = remember(picked) { decodeImageBitmap(picked.bytes) }
    val comingSoon = rememberComingSoonController()
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()

    var activeTool by remember { mutableStateOf<EditorTool?>(null) }
    var selectedOverlayId by remember { mutableStateOf<Long?>(null) }
    var drawColor by remember { mutableStateOf(DRAW_COLORS.first()) }
    var drawStrokeWidth by remember { mutableStateOf(8f) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isComparing by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var showTips by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var draggingOverlayId by remember { mutableStateOf<Long?>(null) }
    var adjustBurstActive by remember { mutableStateOf(false) }

    /** The font of a text layer — `Overlay.Text` has no font slot of its own. */
    val fontByOverlay = remember { mutableStateMapOf<Long, EditorFont>() }

    LaunchedEffect(project) { EditorSessionStore.project = project }

    val effectiveFilter = if (isComparing) FilterPreset.ORIGINAL else project.photoFilter
    val effectiveAdjust = if (isComparing) AdjustSpec() else project.photoAdjust
    val colorFilter = remember(effectiveFilter, effectiveAdjust) {
        ColorFilter.colorMatrix(buildLookMatrix(effectiveFilter, effectiveAdjust))
    }

    val selectedOverlay = project.overlays.firstOrNull { it.id == selectedOverlayId }
    val selectedPip = selectedOverlay as? Overlay.Pip

    /* ------------------------------------------------------------- actions -- */

    fun applyLook(filter: FilterPreset, adjust: AdjustSpec, blurred: Boolean? = null) {
        controller.update { current ->
            val updated = current.withPhotoLook(filter, adjust)
            if (blurred == null) {
                updated
            } else {
                updated.copy(canvas = updated.canvas.copy(blurredBackground = blurred))
            }
        }
    }

    /** Slider drags push one undo entry per gesture instead of one per pixel. */
    fun applyAdjust(adjust: AdjustSpec) {
        if (adjustBurstActive) {
            controller.updateLive { it.withPhotoLook(it.photoFilter, adjust) }
        } else {
            adjustBurstActive = true
            controller.update { it.withPhotoLook(it.photoFilter, adjust) }
            scope.launch {
                delay(600)
                adjustBurstActive = false
            }
        }
    }

    fun moveOverlay(id: Long, delta: Offset) {
        val move: (EditProject) -> EditProject = { current ->
            current.copy(
                overlays = current.overlays.map { overlay ->
                    if (overlay.id == id) overlay.movedBy(delta.x, delta.y) else overlay
                },
            )
        }
        if (draggingOverlayId != id) {
            draggingOverlayId = id
            controller.update(move)
        } else {
            controller.updateLive(move)
        }
    }

    fun updateSelectedText(transform: (Overlay.Text) -> Overlay.Text) {
        val id = selectedOverlayId ?: return
        controller.update { current ->
            current.copy(
                overlays = current.overlays.map { overlay ->
                    if (overlay.id == id && overlay is Overlay.Text) transform(overlay) else overlay
                },
            )
        }
    }

    fun updateSelectedPip(transform: (Overlay.Pip) -> Overlay.Pip) {
        val id = selectedOverlayId ?: return
        controller.update { current ->
            current.copy(
                overlays = current.overlays.map { overlay ->
                    if (overlay.id == id && overlay is Overlay.Pip) transform(overlay) else overlay
                },
            )
        }
    }

    fun deleteOverlay(id: Long) {
        controller.update { current ->
            current.copy(overlays = current.overlays.filterNot { it.id == id })
        }
        if (selectedOverlayId == id) selectedOverlayId = null
    }

    fun addText() {
        val id = project.nextOverlayId
        controller.update { current ->
            current.copy(
                overlays = current.overlays + Overlay.Text(
                    id = current.nextOverlayId,
                    text = "Your text",
                    position = Offset(0.5f, 0.5f),
                )
            )
        }
        selectedOverlayId = id
        activeTool = EditorTool.TEXT
    }

    fun addSticker(item: StickerItem) {
        controller.update { current ->
            current.copy(
                overlays = current.overlays + Overlay.Sticker(
                    id = current.nextOverlayId,
                    emoji = item.emoji,
                    label = item.label,
                    position = Offset(0.5f, 0.5f),
                )
            )
        }
    }

    fun addPip() {
        controller.update { current ->
            current.copy(
                overlays = current.overlays + Overlay.Pip(
                    id = current.nextOverlayId,
                    path = "",
                )
            )
        }
    }

    fun startStroke(point: Offset) {
        controller.update { current ->
            current.copy(
                overlays = current.overlays + Overlay.Drawing(
                    id = current.nextOverlayId,
                    points = listOf(point),
                    color = drawColor,
                    strokeWidth = drawStrokeWidth,
                )
            )
        }
    }

    fun moveStroke(point: Offset) {
        controller.updateLive { current ->
            val last = current.overlays.lastOrNull()
            if (last is Overlay.Drawing) {
                current.copy(
                    overlays = current.overlays.dropLast(1) + last.copy(points = last.points + point)
                )
            } else {
                current
            }
        }
    }

    fun clearDrawings() {
        controller.update { current ->
            current.copy(overlays = current.overlays.filterNot { it is Overlay.Drawing })
        }
    }

    fun setAspect(aspect: AspectRatio) {
        controller.update { it.copy(canvas = it.canvas.copy(aspect = aspect)) }
    }

    fun setBackground(style: BackgroundStyle) {
        controller.update {
            it.copy(canvas = it.canvas.copy(background = style, blurredBackground = style == BackgroundStyle.BLUR))
        }
    }

    fun setBackgroundWithBlur(style: BackgroundStyle, blurred: Boolean) {
        controller.update {
            it.copy(canvas = it.canvas.copy(background = style, blurredBackground = blurred))
        }
    }

    fun setLayout(aspect: AspectRatio, background: BackgroundStyle) {
        controller.update {
            it.copy(
                canvas = it.canvas.copy(
                    aspect = aspect,
                    background = background,
                    blurredBackground = background == BackgroundStyle.BLUR,
                )
            )
        }
    }

    fun selectTool(tool: EditorTool) {
        if (activeTool == tool) {
            activeTool = null
            return
        }
        activeTool = tool
        statusMessage = null
        gatedFeatureFor(tool)?.let { comingSoon.show(it) }
    }

    fun exportPhoto() {
        if (isExporting) return
        isExporting = true
        scope.launch {
            // The layer already holds the composed canvas from the last frame.
            val composed = graphicsLayer.toImageBitmap()
            val bytes = if (composed.width > 0 && composed.height > 0) {
                encodeImageBitmapToJpeg(composed)
            } else {
                picked.bytes
            }
            EditorSessionStore.exportedImageBytes = bytes
            isExporting = false
            onExport()
        }
    }

    /* -------------------------------------------------------------- layout -- */

    val canvas: @Composable (Modifier) -> Unit = { canvasModifier ->
        PhotoCanvas(
            bitmap = bitmap,
            project = project,
            colorFilter = colorFilter,
            selectedOverlayId = selectedOverlayId,
            fontByOverlay = fontByOverlay,
            drawingEnabled = activeTool == EditorTool.DRAW,
            textEditingEnabled = activeTool == EditorTool.TEXT,
            onSelectOverlay = { id ->
                selectedOverlayId = id
                // A fresh selection means a fresh drag: the next move starts a
                // new undo entry instead of continuing the previous one.
                draggingOverlayId = null
            },
            onMoveOverlay = { id, delta ->
                if (activeTool != EditorTool.DRAW) moveOverlay(id, delta)
            },
            onMoveOverlayEnd = { draggingOverlayId = null },
            onEditText = { id, text ->
                controller.updateLive { current ->
                    current.copy(
                        overlays = current.overlays.map { overlay ->
                            if (overlay.id == id && overlay is Overlay.Text) overlay.copy(text = text) else overlay
                        },
                    )
                }
            },
            onStrokeStart = { point -> startStroke(point) },
            onStrokeMove = { point -> moveStroke(point) },
            graphicsLayer = graphicsLayer,
            modifier = canvasModifier,
        )
    }

    if (isFullscreen) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SnapzyColors.bgBase)
                .statusBarsPadding(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                canvas(Modifier.fillMaxSize())
                PhotoIconButton(
                    glyph = "✕",
                    onClick = { isFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SnapzyColors.bgBase)
            .statusBarsPadding(),
    ) {
        PhotoHeaderRow(
            isExporting = isExporting,
            onBack = onBack,
            onToggleTips = { showTips = !showTips },
            onExport = { exportPhoto() },
        )

        if (showTips) {
            PhotoTipsPanel()
        }

        canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        PhotoTransportRow(
            canUndo = controller.canUndo,
            canRedo = controller.canRedo,
            isComparing = isComparing,
            toolOpen = activeTool != null,
            onUndo = { controller.undo() },
            onRedo = { controller.redo() },
            onCompare = { isComparing = !isComparing },
            onDone = { activeTool = null },
            onFullscreen = { isFullscreen = true },
        )

        val tool = activeTool
        if (tool != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (tool) {
                    EditorTool.FILTER -> FilterPanel(
                        selected = project.photoFilter,
                        onSelect = { filter -> applyLook(filter, project.photoAdjust) },
                    )

                    EditorTool.ADJUST -> AdjustPanel(
                        adjust = project.photoAdjust,
                        onChange = { adjust -> applyAdjust(adjust) },
                    )

                    EditorTool.EFFECTS -> PhotoEffectsPanel(
                        project = project,
                        onApplyLook = { filter, adjust, blurred -> applyLook(filter, adjust, blurred) },
                        onComingSoon = { feature -> comingSoon.show(feature) },
                    )

                    EditorTool.TEXT -> TextPanel(
                        onAddText = { addText() },
                        selectedText = selectedOverlay as? Overlay.Text,
                        onColorChange = { color -> updateSelectedText { it.copy(color = color) } },
                        onFontChange = { font ->
                            val id = selectedOverlayId ?: return@TextPanel
                            fontByOverlay[id] = font
                            when (font) {
                                EditorFont.BOLD -> updateSelectedText { it.copy(bold = true) }
                                EditorFont.DEFAULT -> updateSelectedText { it.copy(bold = false) }
                                else -> Unit
                            }
                        },
                        onSizeChange = { size -> updateSelectedText { it.copy(fontSizeSp = size) } },
                        onBoldToggle = { updateSelectedText { it.copy(bold = !it.bold) } },
                        onOutlineToggle = { updateSelectedText { it.copy(outline = !it.outline) } },
                        onShadowToggle = { updateSelectedText { it.copy(shadow = !it.shadow) } },
                        onDelete = { selectedOverlayId?.let { deleteOverlay(it) } },
                    )

                    EditorTool.STICKER -> StickerPanel(
                        stickers = STICKER_CATALOGUE,
                        onPick = { item -> addSticker(item) },
                    )

                    EditorTool.DRAW -> DrawPanel(
                        colors = DRAW_COLORS,
                        selectedColor = drawColor,
                        onColorChange = { color -> drawColor = color },
                        strokeWidth = drawStrokeWidth,
                        onStrokeWidthChange = { width -> drawStrokeWidth = width },
                        onClear = { clearDrawings() },
                    )

                    EditorTool.ERASER -> PhotoEraserPanel(
                        hasSelection = selectedOverlay != null,
                        hasDrawings = project.overlays.any { it is Overlay.Drawing },
                        onDeleteSelected = { selectedOverlayId?.let { deleteOverlay(it) } },
                        onClearDrawings = { clearDrawings() },
                    )

                    EditorTool.CROP -> AspectPanel(
                        selected = project.canvas.aspect,
                        onSelect = { aspect -> setAspect(aspect) },
                    )

                    EditorTool.RATIO -> AspectPanel(
                        selected = project.canvas.aspect,
                        onSelect = { aspect -> setAspect(aspect) },
                    )

                    EditorTool.BACKGROUND -> BackgroundPanel(
                        selected = project.canvas.background,
                        onSelect = { style -> setBackground(style) },
                        onPickImage = {
                            controller.update {
                                it.copy(canvas = it.canvas.copy(background = BackgroundStyle.CUSTOM))
                            }
                            statusMessage = "Custom background images are coming soon."
                        },
                    )

                    EditorTool.BORDER -> PhotoBorderPanel(
                        selected = project.canvas.background,
                        onSelect = { style -> setBackground(style) },
                    )

                    EditorTool.FRAME -> PhotoFramePanel(
                        selected = project.canvas.background,
                        blurred = project.canvas.blurredBackground,
                        onSelect = { style, blurred -> setBackgroundWithBlur(style, blurred) },
                    )

                    EditorTool.LAYOUT -> PhotoLayoutPanel(
                        selectedAspect = project.canvas.aspect,
                        selectedBackground = project.canvas.background,
                        onSelect = { aspect, background -> setLayout(aspect, background) },
                    )

                    EditorTool.MOSAIC -> PhotoComingSoonPanel(title = tool.label)

                    EditorTool.PIP -> PhotoPipPanel(
                        hasPip = project.overlays.any { it is Overlay.Pip },
                        hasSelection = selectedPip != null,
                        onAddPip = { addPip() },
                        onRemoveSelected = { selectedOverlayId?.let { deleteOverlay(it) } },
                    )

                    EditorTool.MASK -> {
                        MaskPanel(
                            selected = selectedPip?.maskShape ?: MaskShape.NONE,
                            onSelect = { shape -> updateSelectedPip { it.copy(maskShape = shape) } },
                        )
                        if (selectedPip == null) {
                            Text(
                                text = "Add a PIP layer first, then pick a mask shape for it.",
                                style = SnapzyType.caption,
                                color = SnapzyColors.textSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }

                    EditorTool.BLEND -> BlendPanel(
                        selected = selectedPip?.blend ?: BlendMode.NORMAL,
                        onSelect = { mode -> updateSelectedPip { it.copy(blend = mode) } },
                    )

                    EditorTool.DRAFT -> PhotoDraftPanel(
                        onSaveDraft = {
                            EditorSessionStore.project = project
                            statusMessage = "Draft saved to this session."
                        },
                        statusMessage = statusMessage,
                    )

                    else -> PhotoComingSoonPanel(title = tool.label)
                }

                statusMessage?.let { message ->
                    if (tool != EditorTool.DRAFT) {
                        Text(
                            text = message,
                            style = SnapzyType.caption,
                            color = SnapzyColors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        EditorToolBar(
            tools = PHOTO_TOOLS,
            active = activeTool,
            onSelect = { tool -> selectTool(tool) },
        )
    }
}

/** The header row: back, help and the export action. */
@Composable
private fun PhotoHeaderRow(
    isExporting: Boolean,
    onBack: () -> Unit,
    onToggleTips: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SnapzyColors.bgBase)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoIconButton(glyph = "←", onClick = onBack)
        Spacer(Modifier.width(4.dp))
        PhotoIconButton(glyph = "?", onClick = onToggleTips, tint = SnapzyColors.textSecondary)
        Spacer(Modifier.weight(1f))
        Text(
            text = if (isExporting) "Exporting…" else "Export",
            style = SnapzyType.body,
            color = SnapzyColors.brandPrimary,
            modifier = Modifier
                .clip(SnapzyShapes.pill)
                .clickable(enabled = !isExporting, onClick = onExport)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * The transport row under the preview: undo / redo, the compare toggle and
 * the fullscreen action — which becomes a "Done" button while a tool panel is
 * open.
 */
@Composable
private fun PhotoTransportRow(
    canUndo: Boolean,
    canRedo: Boolean,
    isComparing: Boolean,
    toolOpen: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCompare: () -> Unit,
    onDone: () -> Unit,
    onFullscreen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoIconButton(glyph = "↶", onClick = onUndo, enabled = canUndo)
        PhotoIconButton(glyph = "↷", onClick = onRedo, enabled = canRedo)
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            PhotoIconButton(
                glyph = if (isComparing) "◑" else "◐",
                onClick = onCompare,
                tint = if (isComparing) SnapzyColors.brandPrimary else SnapzyColors.textPrimary,
            )
        }
        if (toolOpen) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(SnapzyShapes.pill)
                    .background(SnapzyColors.brandPrimary)
                    .clickable(onClick = onDone),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "✓", style = SnapzyType.body, color = SnapzyColors.bgBase)
            }
        } else {
            PhotoIconButton(glyph = "⛶", onClick = onFullscreen)
        }
    }
}

/** A round, icon-only button used by the header and the transport row. */
@Composable
private fun PhotoIconButton(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = SnapzyColors.textPrimary,
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(SnapzyShapes.pill)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = SnapzyType.title,
            color = if (enabled) tint else SnapzyColors.divider,
        )
    }
}

/** The tools that need a backend or a real shader open the Coming Soon sheet. */
private fun gatedFeatureFor(tool: EditorTool): GatedFeature? = when (tool) {
    EditorTool.AI_RETOUCH -> GatedFeature.AI_ERASER
    EditorTool.AI_LIGHT -> GatedFeature.AI_ENHANCE
    EditorTool.AI_EFFECT -> GatedFeature.EFFECTS_TOOL
    EditorTool.ENHANCE -> GatedFeature.AI_ENHANCE
    EditorTool.CUTOUT -> GatedFeature.AI_ERASER
    EditorTool.MOSAIC -> GatedFeature.MOSAIC_TOOL
    else -> null
}
