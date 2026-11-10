package com.snapzy.app.feature.editor.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snapzy.app.core.designsystem.ColorSwatch
import com.snapzy.app.core.designsystem.OptionChip
import com.snapzy.app.core.designsystem.SliderRow
import com.snapzy.app.core.designsystem.SnapzyColors
import com.snapzy.app.core.designsystem.SnapzyShapes
import com.snapzy.app.core.designsystem.SnapzyType
import com.snapzy.app.core.designsystem.ToolPanel
import com.snapzy.app.core.editing.AdjustSpec
import com.snapzy.app.core.editing.AspectRatio
import com.snapzy.app.core.editing.BackgroundStyle
import com.snapzy.app.core.editing.BlendMode
import com.snapzy.app.core.editing.CatalogueItem
import com.snapzy.app.core.editing.EditorFont
import com.snapzy.app.core.editing.EffectPreset
import com.snapzy.app.core.editing.FilterPreset
import com.snapzy.app.core.editing.filterSwatch
import com.snapzy.app.core.editing.filterTint
import com.snapzy.app.core.editing.MaskShape
import com.snapzy.app.core.editing.Overlay
import com.snapzy.app.core.editing.StickerItem
import com.snapzy.app.core.editing.TransitionKind

/**
 * The tool panels shared by the video and photo editors.
 *
 * Every panel is a stateless composable: it renders the current value and
 * reports changes through a callback, so both editors can reuse them without
 * either owning the other's state.
 */

/* ------------------------------------------------------------------ look -- */

@Composable
fun FilterPanel(
    selected: FilterPreset,
    onSelect: (FilterPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(title = "Filter", modifier = modifier) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(FilterPreset.entries.toList()) { filter ->
                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .clickable { onSelect(filter) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(SnapzyShapes.small)
                            .background(Brush.linearGradient(filterSwatch(filter)))
                            .then(
                                if (filter == selected) {
                                    Modifier.border(2.dp, SnapzyColors.brandPrimary, SnapzyShapes.small)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (filter == FilterPreset.ORIGINAL) {
                            Text(
                                text = "None",
                                style = SnapzyType.caption,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = filter.label,
                        style = SnapzyType.caption,
                        color = if (filter == selected) SnapzyColors.brandPrimary else SnapzyColors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun AdjustPanel(
    adjust: AdjustSpec,
    onChange: (AdjustSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(title = "Adjust", modifier = modifier) {
        SliderRow(
            label = "Brightness",
            value = adjust.brightness,
            range = -1f..1f,
            onChange = { onChange(adjust.copy(brightness = it)) },
        )
        SliderRow(
            label = "Contrast",
            value = adjust.contrast,
            range = 0.5f..1.5f,
            onChange = { onChange(adjust.copy(contrast = it)) },
        )
        SliderRow(
            label = "Saturation",
            value = adjust.saturation,
            range = 0f..2f,
            onChange = { onChange(adjust.copy(saturation = it)) },
        )
        SliderRow(
            label = "Hue",
            value = adjust.hue,
            range = -180f..180f,
            onChange = { onChange(adjust.copy(hue = it)) },
            valueLabel = "${adjust.hue.toInt()}°",
        )
        SliderRow(
            label = "Temperature",
            value = adjust.temperature,
            range = -1f..1f,
            onChange = { onChange(adjust.copy(temperature = it)) },
        )
    }
}

@Composable
fun EffectsPanel(
    selected: EffectPreset,
    onSelect: (EffectPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(title = "Effects", modifier = modifier) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(EffectPreset.entries.toList()) { effect ->
                OptionChip(
                    label = effect.label,
                    selected = selected == effect,
                    onClick = { onSelect(effect) },
                )
            }
        }
    }
}

/* ---------------------------------------------------------------- layers -- */

@Composable
fun TextPanel(
    onAddText: () -> Unit,
    modifier: Modifier = Modifier,
    selectedText: Overlay.Text? = null,
    onColorChange: ((Color) -> Unit)? = null,
    onFontChange: ((EditorFont) -> Unit)? = null,
    onSizeChange: ((Float) -> Unit)? = null,
    onBoldToggle: (() -> Unit)? = null,
    onOutlineToggle: (() -> Unit)? = null,
    onShadowToggle: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    ToolPanel(title = "Text", modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OptionChip(label = "+ Add text", selected = false, onClick = onAddText)
            if (selectedText != null) {
                Spacer(Modifier.weight(1f))
                OptionChip(
                    label = if (selectedText.bold) "Bold" else "Regular",
                    selected = selectedText.bold,
                    onClick = { onBoldToggle?.invoke() },
                )
                OptionChip(
                    label = "Outline",
                    selected = selectedText.outline,
                    onClick = { onOutlineToggle?.invoke() },
                )
                OptionChip(
                    label = "Shadow",
                    selected = selectedText.shadow,
                    onClick = { onShadowToggle?.invoke() },
                )
                OptionChip(label = "Delete", selected = false, onClick = { onDelete?.invoke() })
            }
        }

        if (selectedText != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Colour",
                style = SnapzyType.label,
                color = SnapzyColors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TEXT_COLORS.forEach { color ->
                    ColorSwatch(
                        color = color,
                        selected = selectedText.color == color,
                        onClick = { onColorChange?.invoke(color) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SliderRow(
                label = "Size",
                value = selectedText.fontSizeSp,
                range = 12f..72f,
                onChange = { onSizeChange?.invoke(it) },
                valueLabel = "${selectedText.fontSizeSp.toInt()}",
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorFont.entries.forEach { font ->
                    OptionChip(
                        label = font.label,
                        selected = false,
                        onClick = { onFontChange?.invoke(font) },
                    )
                }
            }
        }
    }
}

@Composable
fun StickerPanel(
    stickers: List<StickerItem>,
    onPick: (StickerItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(title = "Stickers", modifier = modifier) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier.height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(stickers) { sticker ->
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(SnapzyShapes.small)
                        .background(SnapzyColors.bgBase)
                        .clickable { onPick(sticker) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = sticker.emoji, style = SnapzyType.title)
                }
            }
        }
    }
}

@Composable
fun DrawPanel(
    colors: List<Color>,
    selectedColor: Color,
    onColorChange: (Color) -> Unit,
    strokeWidth: Float,
    onStrokeWidthChange: (Float) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(title = "Draw", modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            colors.forEach { color ->
                ColorSwatch(
                    color = color,
                    selected = selectedColor == color,
                    onClick = { onColorChange(color) },
                )
            }
            Spacer(Modifier.weight(1f))
            OptionChip(label = "Clear", selected = false, onClick = onClear)
        }
        Spacer(Modifier.height(10.dp))
        SliderRow(
            label = "Brush size",
            value = strokeWidth,
            range = 2f..28f,
            onChange = onStrokeWidthChange,
            valueLabel = "${strokeWidth.toInt()}",
        )
    }
}

/* ---------------------------------------------------------------- canvas -- */

@Composable
fun AspectPanel(
    selected: AspectRatio,
    onSelect: (AspectRatio) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(title = "Canvas", modifier = modifier) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AspectRatio.entries.forEach { aspect ->
                OptionChip(
                    label = aspect.label,
                    selected = selected == aspect,
                    onClick = { onSelect(aspect) },
                )
            }
        }
    }
}

@Composable
fun BackgroundPanel(
    selected: BackgroundStyle,
    onSelect: (BackgroundStyle) -> Unit,
    modifier: Modifier = Modifier,
    onPickImage: (() -> Unit)? = null,
) {
    ToolPanel(title = "Background", modifier = modifier) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OptionChip(label = "Black", selected = selected == BackgroundStyle.BLACK, onClick = { onSelect(BackgroundStyle.BLACK) })
            OptionChip(label = "White", selected = selected == BackgroundStyle.WHITE, onClick = { onSelect(BackgroundStyle.WHITE) })
            OptionChip(label = "Blur", selected = selected == BackgroundStyle.BLUR, onClick = { onSelect(BackgroundStyle.BLUR) })
            OptionChip(label = "Custom", selected = selected == BackgroundStyle.CUSTOM, onClick = { onPickImage?.invoke() })
        }
    }
}

/* ----------------------------------------------------------------- audio -- */

@Composable
fun SpeedPanel(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(title = "Speed", modifier = modifier) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SPEED_PRESETS.forEach { preset ->
                OptionChip(
                    label = preset.label,
                    selected = speed == preset.value,
                    onClick = { onSpeedChange(preset.value) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        SliderRow(
            label = "Speed",
            value = speed,
            range = 0.25f..4f,
            onChange = onSpeedChange,
            valueLabel = "${speed}x",
        )
    }
}

@Composable
fun VolumePanel(
    volume: Float,
    muted: Boolean,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(title = "Volume", modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OptionChip(
                label = if (muted) "Unmute" else "Mute",
                selected = muted,
                onClick = onMuteToggle,
            )
        }
        Spacer(Modifier.height(10.dp))
        SliderRow(
            label = "Clip volume",
            value = volume,
            range = 0f..2f,
            onChange = onVolumeChange,
            valueLabel = "${(volume * 100).toInt()}%",
        )
    }
}

@Composable
fun AudioLibraryPanel(
    title: String,
    items: List<CatalogueItem>,
    onPick: (CatalogueItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(title = title, modifier = modifier) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items) { item ->
                Column(
                    modifier = Modifier
                        .width(84.dp)
                        .clickable { onPick(item) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(SnapzyShapes.small)
                            .background(SnapzyColors.bgBase),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = item.emoji, style = SnapzyType.title)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = item.title,
                        style = SnapzyType.caption,
                        color = SnapzyColors.textPrimary,
                        maxLines = 1,
                    )
                    Text(
                        text = item.subtitle,
                        style = SnapzyType.caption,
                        color = SnapzyColors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/* -------------------------------------------------------------- compositing -- */

@Composable
fun MaskPanel(
    selected: MaskShape,
    onSelect: (MaskShape) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(title = "Mask", modifier = modifier) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MaskShape.entries.forEach { shape ->
                OptionChip(
                    label = shape.label,
                    selected = selected == shape,
                    onClick = { onSelect(shape) },
                )
            }
        }
    }
}

@Composable
fun BlendPanel(
    selected: BlendMode,
    onSelect: (BlendMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(title = "Blend", modifier = modifier) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BlendMode.entries.forEach { mode ->
                OptionChip(
                    label = mode.label,
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                )
            }
        }
    }
}

@Composable
fun TransitionPanel(
    selected: TransitionKind,
    onSelect: (TransitionKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(title = "Transition", modifier = modifier) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransitionKind.entries.forEach { kind ->
                OptionChip(
                    label = kind.label,
                    selected = selected == kind,
                    onClick = { onSelect(kind) },
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ data -- */

/** Colour options offered by the text tool. */
val TEXT_COLORS: List<Color> = SnapzyColors.textSwatches

/** Brush colours offered by the draw tool. */
val DRAW_COLORS: List<Color> = SnapzyColors.brushSwatches

private data class SpeedPreset(val label: String, val value: Float)

private val SPEED_PRESETS = listOf(
    SpeedPreset("0.25x", 0.25f),
    SpeedPreset("0.5x", 0.5f),
    SpeedPreset("0.75x", 0.75f),
    SpeedPreset("1x", 1f),
    SpeedPreset("1.5x", 1.5f),
    SpeedPreset("2x", 2f),
    SpeedPreset("3x", 3f),
    SpeedPreset("4x", 4f),
)

/** A small circular badge used to flag Pro-gated items in a panel. */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(SnapzyColors.accentPro)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = "PRO",
            style = SnapzyType.caption,
            color = SnapzyColors.bgBase,
            fontWeight = FontWeight.Bold,
        )
    }
}
