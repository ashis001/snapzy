package com.movexa.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movexa.app.core.editing.EditorTool

/** Draws an [EditorTool] glyph inside the given bounds. */
@Composable
fun ToolIconView(
    tool: EditorTool,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawToolIcon(tool = tool, tint = tint)
    }
}

/**
 * One entry in the editor's scrolling tool bar.
 *
 * [active] paints the icon and its label in the brand colour — the way InShot
 * signals the open panel. [gated] adds the small amber "PRO" dot.
 */
@Composable
fun ToolButton(
    tool: EditorTool,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gated: Boolean = tool.gated,
) {
    val tint = if (active) MovexaColors.brandPrimary else MovexaColors.textSecondary
    Column(
        modifier = modifier
            .width(58.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            ToolIconView(
                tool = tool,
                tint = tint,
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.Center),
            )
            if (gated) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .align(Alignment.TopEnd)
                        .clip(MovexaShapes.pill)
                        .background(MovexaColors.accentPro),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = tool.label,
            style = MovexaType.caption,
            color = tint,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** Horizontally scrolling tool bar, the way InShot presents its editor tools. */
@Composable
fun EditorToolBar(
    tools: List<EditorTool>,
    active: EditorTool?,
    onSelect: (EditorTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
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
            }
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tools.forEach { tool ->
            ToolButton(
                tool = tool,
                active = active == tool,
                onClick = { onSelect(tool) },
            )
        }
    }
}

/**
 * The container every tool panel renders into. Gives all panels the same
 * surface colour, padding and title treatment so the editor reads as one app.
 */
@Composable
fun ToolPanel(
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MovexaColors.bgSurfaceElevated)
            .border(
                width = 1.dp,
                color = MovexaColors.borderSubtle,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MovexaType.label, color = MovexaColors.textSecondary)
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
            if (onClose != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Done",
                    style = MovexaType.label,
                    color = MovexaColors.brandPrimary,
                    modifier = Modifier.clickable(onClick = onClose),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** A selectable pill used for aspect ratios, blend modes, transitions, … */
@Composable
fun OptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    /** A disabled chip is dimmed and does not react to a tap. */
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(MovexaShapes.small)
            .background(if (selected) MovexaColors.brandPrimary.copy(alpha = 0.16f) else MovexaColors.bgBase)
            .border(
                BorderStroke(
                    1.dp,
                    when {
                        selected -> MovexaColors.brandPrimary
                        !enabled -> MovexaColors.borderSubtle
                        else -> MovexaColors.divider
                    },
                ),
                MovexaShapes.small,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(
            text = label,
            style = MovexaType.label,
            color = when {
                selected -> MovexaColors.brandPrimary
                !enabled -> MovexaColors.textTertiary
                else -> MovexaColors.textPrimary
            },
        )
    }
}

/** A round swatch used for colour pickers. */
@Composable
fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(MovexaShapes.pill)
            .background(color)
            .border(
                BorderStroke(if (selected) 2.dp else 0.dp, MovexaColors.textPrimary),
                MovexaShapes.pill,
            )
            .clickable(onClick = onClick),
    )
}

/** Labelled slider used by every numeric tool panel (Adjust, Speed, Volume…). */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueLabel: String? = null,
    steps: Int = 0,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MovexaType.label, color = MovexaColors.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                text = valueLabel ?: formatSliderValue(value),
                style = MovexaType.label,
                color = MovexaColors.textPrimary,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MovexaColors.brandPrimary,
                activeTrackColor = MovexaColors.brandPrimary,
                inactiveTrackColor = MovexaColors.divider,
            ),
        )
    }
}

/** Segmented control, used for photo/video picker tabs and export options. */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MovexaShapes.pill)
            .background(MovexaColors.bgBase)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MovexaShapes.pill)
                    .background(if (selected) MovexaColors.brandPrimary else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MovexaType.label,
                    color = if (selected) MovexaColors.bgBase else MovexaColors.textSecondary,
                )
            }
        }
    }
}

/**
 * A thin, right-aligned tab strip with an underline on the active tab.
 *
 * The picker uses this instead of the chunky segmented control, matching the
 * strip InShot puts under its top bar.
 */
@Composable
fun TabStrip(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .clip(MovexaShapes.small)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = option,
                    style = MovexaType.body,
                    color = if (selected) MovexaColors.textPrimary else MovexaColors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(if (selected) 28.dp else 0.dp)
                        .clip(MovexaShapes.pill)
                        .background(if (selected) MovexaColors.textPrimary else Color.Transparent),
                )
            }
        }
    }
}

/** Primary call-to-action used on the export and home screens. */
@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MovexaShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = MovexaColors.brandPrimary,
            contentColor = MovexaColors.bgBase,
            disabledContainerColor = MovexaColors.bgSurfaceElevated,
            disabledContentColor = MovexaColors.textSecondary,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 13.dp),
    ) {
        Text(text = text, style = MovexaType.body)
    }
}

/** Circular icon-only button used in the editor top bar (back, undo, redo). */
@Composable
fun CircleIconButton(
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MovexaColors.textPrimary,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(MovexaShapes.pill)
            .background(MovexaColors.bgSurfaceElevated)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/** Formats a slider value for display (drops trailing `.0`). */
fun formatSliderValue(value: Float): String {
    val rounded = (value * 100f).toInt() / 100f
    return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
}

/** A no-ripple clickable, used where Material's ripple would bleed out of a shape. */
@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
    )
}

/** Placeholder used for screens that are still planned. */
@Composable
fun ComingSoonTile(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MovexaShapes.medium)
            .background(MovexaColors.bgSurface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(MovexaShapes.small)
                .background(MovexaColors.brandSecondary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "✦", style = MovexaType.title, color = MovexaColors.brandSecondary)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MovexaType.body, color = MovexaColors.textPrimary)
            Text(text = subtitle, style = MovexaType.caption, color = MovexaColors.textSecondary)
        }
        StatusChip(
            text = "SOON",
            background = MovexaColors.bgSurfaceElevated,
            contentColor = MovexaColors.textSecondary,
        )
    }
}

/** Convenience for the common "rounded card" surface used across the app. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(MovexaShapes.medium)
            .background(MovexaColors.bgSurface)
            .border(1.dp, MovexaColors.borderSubtle, MovexaShapes.medium),
    ) { content() }
}

/** Material theme accessor used by panels that need the current text colour. */
@Composable
fun currentTextColor(): Color = MaterialTheme.colorScheme.onBackground
