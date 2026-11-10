package com.snapzy.app.core.editing

/**
 * Every tool the editor chrome can show. Each editor picks the subset it
 * supports (see `VideoEditorScreen` / `PhotoEditorScreen`).
 *
 * [gated] marks a tool whose real implementation needs a backend we do not
 * have yet (AI features, marketplace content). Gated tools still render with
 * their icon and open the Coming Soon sheet — see `core/comingsoon`.
 */
enum class EditorTool(val label: String, val gated: Boolean = false) {
    // Clip / timeline
    TRIM("Trim"),
    SPLIT("Split"),
    SPEED("Speed"),
    VOLUME("Volume"),
    MUSIC("Music"),
    VOICE("Voice"),
    REVERSE("Reverse"),
    FREEZE("Freeze"),
    SWAP("Swap"),
    APPLY_ALL("Apply all"),

    // Look
    FILTER("Filter"),
    ADJUST("Adjust"),
    EFFECTS("Effects"),

    // Layers
    TEXT("Text"),
    STICKER("Sticker"),
    DRAW("Draw"),
    MOSAIC("Mosaic"),
    PIP("PIP"),
    MASK("Mask"),
    BLEND("Blend"),
    CHROMA("Chroma"),
    TRACKING("Tracking"),

    // Canvas
    CROP("Crop"),
    ROTATE("Rotate"),
    RATIO("Canvas"),
    BACKGROUND("BG"),

    // Motion
    TRANSITION("Transition"),
    ANIMATION("Animation"),
    KEYFRAME("Keyframe"),

    // Extras
    DURATION("Duration"),
    RECORD("Record"),
    EXTRACT_AUDIO("Extract"),
    TELEPROMPTER("Teleprompter"),

    // Photo-only extras
    BORDER("Border"),
    FRAME("Frame"),
    LAYOUT("Layout"),
    ERASER("Eraser"),
    COLLAGE("Collage"),

    // AI (gated — server-side)
    AI_CUT("AI Cut", gated = true),
    CAPTIONS("Captions", gated = true),
    AI_SPEECH("AI Speech", gated = true),
    ENHANCE("AI Enhance", gated = true),
    CUTOUT("Cutout", gated = true),
    STABILIZE("Stabilize", gated = true),
    AI_RETOUCH("AI Retouch", gated = true),
    AI_LIGHT("AI Light", gated = true),
    AI_EFFECT("AI Effect", gated = true),

    // Project
    DRAFT("Draft"),
    EXPORT("Export"),
}

/**
 * The tools the video editor shows.
 *
 * Ordered the way InShot orders its editor strip: canvas and layers first,
 * then the look tools, then clip operations, then the AI tools.
 */
val VIDEO_TOOLS: List<EditorTool> = listOf(
    EditorTool.RATIO,
    EditorTool.VOLUME,
    EditorTool.STICKER,
    EditorTool.TEXT,
    EditorTool.EFFECTS,
    EditorTool.FILTER,
    EditorTool.ADJUST,
    EditorTool.PIP,
    EditorTool.DURATION,
    EditorTool.TRIM,
    EditorTool.SPLIT,
    EditorTool.SPEED,
    EditorTool.MUSIC,
    EditorTool.VOICE,
    EditorTool.CROP,
    EditorTool.ROTATE,
    EditorTool.MASK,
    EditorTool.CHROMA,
    EditorTool.BLEND,
    EditorTool.ANIMATION,
    EditorTool.KEYFRAME,
    EditorTool.TRANSITION,
    EditorTool.BACKGROUND,
    EditorTool.DRAW,
    EditorTool.MOSAIC,
    EditorTool.RECORD,
    EditorTool.EXTRACT_AUDIO,
    EditorTool.TELEPROMPTER,
    EditorTool.SWAP,
    EditorTool.FREEZE,
    EditorTool.REVERSE,
    EditorTool.APPLY_ALL,
    EditorTool.AI_CUT,
    EditorTool.CAPTIONS,
    EditorTool.AI_SPEECH,
    EditorTool.ENHANCE,
    EditorTool.CUTOUT,
    EditorTool.TRACKING,
    EditorTool.STABILIZE,
    EditorTool.DRAFT,
)

/** The tools the photo editor shows, in InShot's toolbar order. */
val PHOTO_TOOLS: List<EditorTool> = listOf(
    EditorTool.FILTER,
    EditorTool.ADJUST,
    EditorTool.EFFECTS,
    EditorTool.TEXT,
    EditorTool.STICKER,
    EditorTool.DRAW,
    EditorTool.ERASER,
    EditorTool.CROP,
    EditorTool.RATIO,
    EditorTool.BACKGROUND,
    EditorTool.BORDER,
    EditorTool.FRAME,
    EditorTool.LAYOUT,
    EditorTool.MOSAIC,
    EditorTool.PIP,
    EditorTool.MASK,
    EditorTool.BLEND,
    EditorTool.AI_RETOUCH,
    EditorTool.AI_LIGHT,
    EditorTool.AI_EFFECT,
    EditorTool.ENHANCE,
    EditorTool.CUTOUT,
    EditorTool.DRAFT,
)

/** Tools that need a backend and therefore open the Coming Soon sheet. */
val GATED_TOOLS: Set<EditorTool> = EditorTool.entries.filter { it.gated }.toSet()
