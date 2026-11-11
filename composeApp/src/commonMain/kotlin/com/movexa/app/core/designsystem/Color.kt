package com.movexa.app.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Movexa colour tokens.
 *
 * The app is a video editor, so the UI chrome stays deliberately quiet: a
 * five-step cool near-black elevation ladder, hairline borders instead of
 * filled panels, and a single confident aqua accent. The user's footage is
 * always the most colourful thing on screen.
 *
 * Every colour in the app comes from here — no hex literals in feature code.
 * See `docs/ARCHITECTURE.md` for the elevation model.
 */
object MovexaColors {

    /* ------------------------------------------------------------ surfaces -- */

    /** Behind everything: letterbox bars, the deepest well. */
    val bgCanvas = Color(0xFF070809)

    /** The app background. */
    val bgBase = Color(0xFF0B0D12)

    /** Cards, tool bars, the timeline strip. */
    val bgSurface = Color(0xFF13161E)

    /** Tool panels and bottom sheets. */
    val bgSurfaceElevated = Color(0xFF1B1F29)

    /** Modals, popovers and menus — the top of the elevation ladder. */
    val bgOverlay = Color(0xFF232836)

    /* ------------------------------------------------------------- borders -- */

    /** A barely-there edge, for large quiet surfaces. */
    val borderSubtle = Color(0x0FFFFFFF)

    /** The default hairline. White at low alpha reads as a lit edge. */
    val divider = Color(0x1FFFFFFF)

    /** A brighter edge, for focused or selected surfaces. */
    val borderStrong = Color(0x33FFFFFF)

    /* ---------------------------------------------------------------- text -- */

    val textPrimary = Color(0xFFEEF1F6)
    val textSecondary = Color(0xFF9AA3B2)

    /** Decorative text only — never body copy. */
    val textTertiary = Color(0xFF6B7484)

    /** Text drawn on top of a brand-filled surface. */
    val textOnBrand = Color(0xFF04211C)

    /* --------------------------------------------------------------- brand -- */

    /**
     * The accent. A calm aqua rather than an electric cyan: bright enough to
     * mark the active tool, quiet enough not to fight the footage.
     */
    val brandPrimary = Color(0xFF3DDCC0)

    /** Deep indigo — the gradient partner. Never used as a standalone accent. */
    val brandSecondary = Color(0xFF6E6AF0)

    /** A deeper indigo for gradient tails and pressed states. */
    val brandSecondaryDeep = Color(0xFF4A46C8)

    /** The brand ramp, light to deep. Used for gradients and pressed states. */
    val brandRamp = listOf(
        Color(0xFF5FE3CC),
        Color(0xFF3DDCC0),
        Color(0xFF22B39C),
        Color(0xFF158A78),
    )

    /** The full brand gradient — used for the logo and the primary button. */
    val brandGradient = listOf(brandPrimary, brandSecondary)

    /* ------------------------------------------------------------ semantic -- */

    /** Pro / premium badges and locked content. */
    val accentPro = Color(0xFFF0B429)

    val accentSuccess = Color(0xFF3FCF86)

    val accentDanger = Color(0xFFF2555A)

    val accentWarning = Color(0xFFF59E42)

    /* ------------------------------------------------------------- overlay -- */

    /** Scrim behind modals and bottom sheets. */
    val overlayScrim = Color(0x99000000)

    /** A stronger scrim, for full-screen takeovers. */
    val overlayScrimStrong = Color(0xB3000000)

    /* --------------------------------------------------------- content set -- */

    /**
     * The text swatches offered by the text tool.
     *
     * Ordered light to dark so the first pick is the safe one on any media.
     */
    val textSwatches = listOf(
        Color(0xFFFFFFFF),
        Color(0xFF000000),
        brandPrimary,
        brandSecondary,
        accentPro,
        accentDanger,
        accentSuccess,
        Color(0xFF4DA6FF),
        Color(0xFFFF7AC6),
        Color(0xFFFFA64D),
    )

    /** Neutral greys used to fake a monochrome filter preview. */
    val filterSwatchNeutral = Color(0xFF4A4D57)
    val filterSwatchMono = Color(0xFF6E7078)

    /** The brush colours offered by the draw tool. */
    val brushSwatches = listOf(
        Color(0xFFFFFFFF),
        Color(0xFF000000),
        accentDanger,
        brandPrimary,
        brandSecondary,
        accentPro,
    )
}
