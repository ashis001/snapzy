package com.snapzy.app.core.designsystem

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
object SnapzyType {
    val display = TextStyle(fontSize = 28.sp, lineHeight = 36.sp)
    val title = TextStyle(fontSize = 20.sp, lineHeight = 28.sp)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
    val label = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontSize = 11.sp, lineHeight = 16.sp)
}

object SnapzyShapes {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(14.dp)
    val large = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(percent = 50)
}

private val SnapzyDarkColorScheme = darkColorScheme(
    primary = SnapzyColors.brandPrimary,
    onPrimary = SnapzyColors.textOnBrand,
    primaryContainer = SnapzyColors.bgSurfaceElevated,
    onPrimaryContainer = SnapzyColors.brandPrimary,
    secondary = SnapzyColors.brandSecondary,
    onSecondary = SnapzyColors.textOnBrand,
    secondaryContainer = SnapzyColors.bgSurfaceElevated,
    onSecondaryContainer = SnapzyColors.brandSecondary,
    tertiary = SnapzyColors.accentPro,
    onTertiary = SnapzyColors.bgBase,
    background = SnapzyColors.bgBase,
    onBackground = SnapzyColors.textPrimary,
    surface = SnapzyColors.bgSurface,
    onSurface = SnapzyColors.textPrimary,
    surfaceVariant = SnapzyColors.bgSurfaceElevated,
    onSurfaceVariant = SnapzyColors.textSecondary,
    surfaceContainerHighest = SnapzyColors.bgOverlay,
    outline = SnapzyColors.divider,
    outlineVariant = SnapzyColors.borderSubtle,
    error = SnapzyColors.accentDanger,
    onError = SnapzyColors.textPrimary,
    scrim = SnapzyColors.overlayScrim,
)

private val SnapzyMaterialShapes = Shapes(
    small = SnapzyShapes.small,
    medium = SnapzyShapes.medium,
    large = SnapzyShapes.large,
)

private val SnapzyMaterialTypography = Typography(
    displaySmall = SnapzyType.display,
    titleLarge = SnapzyType.title,
    bodyMedium = SnapzyType.body,
    labelMedium = SnapzyType.label,
    labelSmall = SnapzyType.caption,
)

@Composable
fun SnapzyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SnapzyDarkColorScheme,
        typography = SnapzyMaterialTypography,
        shapes = SnapzyMaterialShapes,
        content = content,
    )
}
