package com.movexa.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/** The gradient FAB used for the Home "Create" action — brand.gradient from PRODUCT_SPEC §2.1. */
@Composable
fun GradientFab(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 64.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(MovexaColors.brandPrimary, MovexaColors.brandSecondary)
                )
            )
            .then(Modifier.clickableNoRipple(onClick)),        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Small rounded "PRO" / "SOON" tag used on gated toolbar icons and premium content. */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    background: androidx.compose.ui.graphics.Color = MovexaColors.accentPro,
    contentColor: androidx.compose.ui.graphics.Color = MovexaColors.bgBase,
) {
    Box(
        modifier = modifier
            .clip(MovexaShapes.pill)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = text, style = MovexaType.caption, color = contentColor)
    }
}

@Composable
fun MovexaPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = MovexaShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = MovexaColors.brandPrimary,
            contentColor = MovexaColors.bgBase,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(text = text, style = MovexaType.body)
    }
}

@Composable
fun MovexaSecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = MovexaShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = MovexaColors.bgSurfaceElevated,
            contentColor = MovexaColors.textPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(text = text, style = MovexaType.body)
    }
}

/** Minimal no-ripple clickable, used for the FAB where a circular gradient looks better without Material ripple bleed. */
/** Lives in EditorComponents.kt so every screen shares one implementation. */

