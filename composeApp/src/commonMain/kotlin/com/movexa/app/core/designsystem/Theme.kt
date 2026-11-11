package com.movexa.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// NOTE: Manrope/Inter custom fonts (PRODUCT_SPEC §2.2) are a follow-up —
// loaded via Compose Resources font families once the .ttf assets are added.
// Phase 0 ships with the platform default font so the type scale and app
// shell are correct now; swapping the FontFamily later is a one-line change
// here, not a call-site change.
object MovexaType {
    val display = TextStyle(fontSize = 28.sp, lineHeight = 36.sp)
    val title = TextStyle(fontSize = 20.sp, lineHeight = 28.sp)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
    val label = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontSize = 11.sp, lineHeight = 16.sp)
}

object MovexaShapes {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(14.dp)
    val large = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(percent = 50)
}

private val MovexaDarkColorScheme = darkColorScheme(
    primary = MovexaColors.brandPrimary,
    onPrimary = MovexaColors.textOnBrand,
    primaryContainer = MovexaColors.bgSurfaceElevated,
    onPrimaryContainer = MovexaColors.brandPrimary,
    secondary = MovexaColors.brandSecondary,
    onSecondary = MovexaColors.textOnBrand,
    secondaryContainer = MovexaColors.bgSurfaceElevated,
    onSecondaryContainer = MovexaColors.brandSecondary,
    tertiary = MovexaColors.accentPro,
    onTertiary = MovexaColors.bgBase,
    background = MovexaColors.bgBase,
    onBackground = MovexaColors.textPrimary,
    surface = MovexaColors.bgSurface,
    onSurface = MovexaColors.textPrimary,
    surfaceVariant = MovexaColors.bgSurfaceElevated,
    onSurfaceVariant = MovexaColors.textSecondary,
    surfaceContainerHighest = MovexaColors.bgOverlay,
    outline = MovexaColors.divider,
    outlineVariant = MovexaColors.borderSubtle,
    error = MovexaColors.accentDanger,
    onError = MovexaColors.textPrimary,
    scrim = MovexaColors.overlayScrim,
)

private val MovexaMaterialShapes = Shapes(
    small = MovexaShapes.small,
    medium = MovexaShapes.medium,
    large = MovexaShapes.large,
)

private val MovexaMaterialTypography = Typography(
    displaySmall = MovexaType.display,
    titleLarge = MovexaType.title,
    bodyMedium = MovexaType.body,
    labelMedium = MovexaType.label,
    labelSmall = MovexaType.caption,
)

@Composable
fun MovexaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MovexaDarkColorScheme,
        typography = MovexaMaterialTypography,
        shapes = MovexaMaterialShapes,
        content = content,
    )
}
