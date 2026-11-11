package com.movexa.app.feature.editor.photo

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.movexa.app.core.comingsoon.GatedFeature
import com.movexa.app.core.designsystem.OptionChip
import com.movexa.app.core.designsystem.MovexaColors
import com.movexa.app.core.designsystem.MovexaType
import com.movexa.app.core.designsystem.StatusChip
import com.movexa.app.core.designsystem.ToolPanel
import com.movexa.app.core.editing.AdjustSpec
import com.movexa.app.core.editing.AspectRatio
import com.movexa.app.core.editing.BackgroundStyle
import com.movexa.app.core.editing.EditProject
import com.movexa.app.core.editing.EffectPreset
import com.movexa.app.core.editing.FilterPreset

/**
 * The photo editor's own tool panels.
 *
 * The shared panels in `feature/editor/common` cover most tools; the ones
 * below are the panels a still image needs on its own — or needs because the
 * shared panel cannot express the photo-specific behaviour.
 */

/** A one-tap look the photo editor can render with the shared colour matrix. */
private data class PhotoEffectLook(
    val filter: FilterPreset,
    val adjust: AdjustSpec,
    val blurredBackground: Boolean,
)

/**
 * The effects a photo can actually render today.
 *
 * Everything else needs a fragment / shader pipeline (blur, grain, glow,
 * light leaks), so those chips point at the Coming Soon sheet instead.
 */
private val SUPPORTED_EFFECTS: Map<EffectPreset, PhotoEffectLook> = mapOf(
    EffectPreset.NONE to PhotoEffectLook(FilterPreset.ORIGINAL, AdjustSpec(), false),
    EffectPreset.SEPIA_TONE to PhotoEffectLook(FilterPreset.SEPIA, AdjustSpec(), false),
    EffectPreset.BLUR_BG to PhotoEffectLook(FilterPreset.ORIGINAL, AdjustSpec(), true),
    EffectPreset.SHARPEN to PhotoEffectLook(FilterPreset.ORIGINAL, AdjustSpec(contrast = 1.2f), false),
    EffectPreset.GLOW to PhotoEffectLook(FilterPreset.BRIGHT, AdjustSpec(saturation = 1.25f), false),
)

/**
 * The effects panel.
 *
 * Effects are one-tap looks: picking one replaces the current look, so the
 * selection is derived from the project instead of being stored twice.
 */
@Composable
internal fun PhotoEffectsPanel(
    project: EditProject,
    onApplyLook: (FilterPreset, AdjustSpec, Boolean) -> Unit,
    onComingSoon: (GatedFeature) -> Unit,
) {
    val current = SUPPORTED_EFFECTS.entries.firstOrNull { (_, look) ->
        look.filter == project.photoFilter &&
            look.adjust == project.photoAdjust &&
            look.blurredBackground == project.canvas.blurredBackground
    }?.key

    ToolPanel(title = "Effects") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EffectPreset.entries.forEach { effect ->
                val look = SUPPORTED_EFFECTS[effect]
                if (look != null) {
                    OptionChip(
                        label = effect.label,
                        selected = effect == current,
                        onClick = { onApplyLook(look.filter, look.adjust, look.blurredBackground) },
                    )
                } else {
                    OptionChip(
                        label = effect.label,
                        selected = false,
                        onClick = { onComingSoon(GatedFeature.EFFECTS_TOOL) },
                        leading = {
                            StatusChip(
                                text = "SOON",
                                background = MovexaColors.bgBase.copy(alpha = 0.7f),
                            )
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "One-tap looks. Picking an effect replaces the current filter and adjustments.",
            style = MovexaType.caption,
            color = MovexaColors.textSecondary,
        )
    }
}

/** The eraser panel: removes the selected layer or every doodle. */
@Composable
internal fun PhotoEraserPanel(
    hasSelection: Boolean,
    hasDrawings: Boolean,
    onDeleteSelected: () -> Unit,
    onClearDrawings: () -> Unit,
) {
    ToolPanel(title = "Eraser") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionChip(
                label = "Delete selected layer",
                selected = false,
                onClick = onDeleteSelected,
            )
            OptionChip(
                label = "Clear all doodles",
                selected = false,
                onClick = onClearDrawings,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (hasSelection) {
                "Tap a layer on the photo to erase it."
            } else {
                "Tap a layer on the photo to select it, then erase it."
            },
            style = MovexaType.caption,
            color = MovexaColors.textSecondary,
        )
        if (!hasDrawings) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "There are no doodles to clear yet.",
                style = MovexaType.caption,
                color = MovexaColors.textSecondary,
            )
        }
    }
}

/** The picture-in-picture panel. */
@Composable
internal fun PhotoPipPanel(
    hasPip: Boolean,
    hasSelection: Boolean,
    onAddPip: () -> Unit,
    onRemoveSelected: () -> Unit,
) {
    ToolPanel(title = "PIP") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionChip(label = "+ Add PIP layer", selected = false, onClick = onAddPip)
            OptionChip(label = "Remove selected", selected = false, onClick = onRemoveSelected)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (hasPip) {
                "PIP layers can be masked and blended from the Mask and Blend tools."
            } else {
                "A PIP layer is a second clip placed on top of the photo. Add one to try the layout."
            },
            style = MovexaType.caption,
            color = MovexaColors.textSecondary,
        )
        if (hasSelection) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap the PIP layer on the photo to select it.",
                style = MovexaType.caption,
                color = MovexaColors.textSecondary,
            )
        }
    }
}

/** The border panel: the area around the photo inside the canvas. */
@Composable
internal fun PhotoBorderPanel(
    selected: BackgroundStyle,
    onSelect: (BackgroundStyle) -> Unit,
) {
    ToolPanel(title = "Border") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionChip(label = "None", selected = selected == BackgroundStyle.BLACK, onClick = { onSelect(BackgroundStyle.BLACK) })
            OptionChip(label = "White", selected = selected == BackgroundStyle.WHITE, onClick = { onSelect(BackgroundStyle.WHITE) })
            OptionChip(label = "Blur", selected = selected == BackgroundStyle.BLUR, onClick = { onSelect(BackgroundStyle.BLUR) })
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "The border is the canvas area the photo does not fill.",
            style = MovexaType.caption,
            color = MovexaColors.textSecondary,
        )
    }
}

/** The frame panel: the look of the canvas around the photo. */
@Composable
internal fun PhotoFramePanel(
    selected: BackgroundStyle,
    blurred: Boolean,
    onSelect: (BackgroundStyle, Boolean) -> Unit,
) {
    ToolPanel(title = "Frame") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionChip(label = "Solid", selected = selected == BackgroundStyle.BLACK && !blurred, onClick = { onSelect(BackgroundStyle.BLACK, false) })
            OptionChip(label = "White", selected = selected == BackgroundStyle.WHITE && !blurred, onClick = { onSelect(BackgroundStyle.WHITE, false) })
            OptionChip(label = "Blurred", selected = selected == BackgroundStyle.BLUR || blurred, onClick = { onSelect(BackgroundStyle.BLUR, true) })
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "A blurred frame repeats the photo behind the picture.",
            style = MovexaType.caption,
            color = MovexaColors.textSecondary,
        )
    }
}

/** The layout panel: canvas ratio plus canvas colour in one tap. */
@Composable
internal fun PhotoLayoutPanel(
    selectedAspect: AspectRatio,
    selectedBackground: BackgroundStyle,
    onSelect: (AspectRatio, BackgroundStyle) -> Unit,
) {
    ToolPanel(title = "Layout") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LAYOUT_PRESETS.forEach { preset ->
                OptionChip(
                    label = preset.label,
                    selected = preset.aspect == selectedAspect && preset.background == selectedBackground,
                    onClick = { onSelect(preset.aspect, preset.background) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Layouts set the canvas ratio and the colour behind the photo.",
            style = MovexaType.caption,
            color = MovexaColors.textSecondary,
        )
    }
}

/** The draft panel: stores the current edit in the session store. */
@Composable
internal fun PhotoDraftPanel(
    onSaveDraft: () -> Unit,
    statusMessage: String?,
) {
    ToolPanel(title = "Draft") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionChip(label = "Save draft", selected = false, onClick = onSaveDraft)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = statusMessage ?: "Drafts keep every edit you made to this photo.",
            style = MovexaType.caption,
            color = MovexaColors.textSecondary,
        )
    }
}

/** The state shown for tools that are not implemented in the photo editor yet. */
@Composable
internal fun PhotoComingSoonPanel(
    title: String,
    message: String = "Coming soon",
) {
    ToolPanel(title = title) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = message, style = MovexaType.body, color = MovexaColors.textSecondary)
        }
    }
}

/** The short help sheet behind the header's "?" button. */
@Composable
internal fun PhotoTipsPanel() {
    ToolPanel(title = "Tips") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "Drag text, stickers and doodles to move them on the photo.",
                "Tap a text layer with the Text tool open to type on the photo.",
                "Filter, Adjust and Effects all stack — try a filter, then nudge the contrast.",
                "Undo and redo sit under the photo, next to the compare button.",
            ).forEach { tip ->
                Text(text = "• $tip", style = MovexaType.caption, color = MovexaColors.textSecondary)
            }
        }
    }
}

/** A layout preset: canvas ratio plus the colour behind the photo. */
private data class LayoutPreset(
    val label: String,
    val aspect: AspectRatio,
    val background: BackgroundStyle,
)

private val LAYOUT_PRESETS: List<LayoutPreset> = listOf(
    LayoutPreset("Original", AspectRatio.ORIGINAL, BackgroundStyle.BLACK),
    LayoutPreset("Square", AspectRatio.SQUARE, BackgroundStyle.WHITE),
    LayoutPreset("Story", AspectRatio.PORTRAIT, BackgroundStyle.BLACK),
    LayoutPreset("Wide", AspectRatio.LANDSCAPE, BackgroundStyle.BLACK),
)
