package com.movexa.app.core.comingsoon

/**
 * Registry of every server/API-dependent feature (PRODUCT_SPEC §4.2).
 * Each entry point in the app calls [com.movexa.app.core.comingsoon.ComingSoonSheet]
 * with one of these instead of hand-rolling a dialog per screen — see MVP_PLAN §2.
 */
enum class GatedFeature(
    val title: String,
    val description: String,
) {
    AI_CUT(
        "AI Cut",
        "Auto-remove silences and filler words from your videos.",
    ),
    AI_ENHANCE(
        "AI Enhance",
        "One-tap cloud upscaling for low-quality clips.",
    ),
    AI_ERASER(
        "AI Eraser",
        "Remove unwanted objects or backgrounds automatically.",
    ),
    AUTO_CAPTIONS(
        "Auto Captions",
        "Automatic speech-to-text captions.",
    ),
    VOICEOVER(
        "AI Voiceover",
        "Turn text into natural speech narration.",
    ),
    MARKETPLACE(
        "Marketplace",
        "Browse and download more stickers, music, and effects.",
    ),
    TEMPLATES(
        "Templates",
        "Trending edit templates, ready to remix.",
    ),
    DISCOVER(
        "Discover",
        "See what the community is creating.",
    ),
    GO_PRO(
        "Movexa Pro",
        "Unlock premium filters, templates, and remove the watermark.",
    ),
    ACCOUNT(
        "Account",
        "Sign in to back up projects and sync across devices.",
    ),
    SETTINGS(
        "Settings",
        "Manage your account, preferences and subscription.",
    ),
    STABILIZER(
        "Video Stabilizer",
        "Smooth out shaky clips with one tap.",
    ),
    TRACKING(
        "Tracking",
        "Make stickers and text follow an object in the shot.",
    ),
    TEMPLATES_EDITOR(
        "Templates",
        "Remix trending edits with ready-made templates.",
    ),
    MASK_TOOL(
        "Shape Mask",
        "Mask a layer with a shape.",
    ),
    BLEND_TOOL(
        "Blend Modes",
        "Composite layers with blend modes.",
    ),
    KEYFRAME_TOOL(
        "Keyframes",
        "Animate zoom, movement and opacity over time.",
    ),
    TRANSITION_TOOL(
        "Video Transitions",
        "Move between clips with transitions.",
    ),
    CHROMA_TOOL(
        "Chroma Key",
        "Remove a colour from a clip with a green screen.",
    ),
    MOSAIC_TOOL(
        "Mosaic",
        "Blur or pixelate part of your video.",
    ),
    EFFECTS_TOOL(
        "Video Effects",
        "Add glitch, bling and style effects.",
    ),
    ANIMATION_TOOL(
        "Clip Animation",
        "Animate clips in, out or on a loop.",
    ),
}
