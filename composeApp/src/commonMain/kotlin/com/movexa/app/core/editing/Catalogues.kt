package com.movexa.app.core.editing

/** Bundled asset catalogues. All content here is ours — nothing is copied from another app. */

/** Music library shown in the Music panel. */
val MUSIC_CATALOGUE: List<CatalogueItem> = listOf(
    CatalogueItem("morning", "Morning Light", "Lo-fi · 1:12", 72_000, "☀️"),
    CatalogueItem("drive", "Night Drive", "Synthwave · 0:58", 58_000, "🚗"),
    CatalogueItem("focus", "Deep Focus", "Ambient · 2:04", 124_000, "🎧"),
    CatalogueItem("happy", "Good Vibes", "Pop · 0:45", 45_000, "🎉"),
    CatalogueItem("cinema", "Cinematic Rise", "Epic · 1:30", 90_000, "🎬"),
    CatalogueItem("chill", "Sunday Chill", "Acoustic · 1:05", 65_000, "🌿"),
    CatalogueItem("beat", "Trap Beat", "Drums · 0:52", 52_000, "🥁"),
    CatalogueItem("lofi", "Lofi Study", "Beats · 1:48", 108_000, "📚"),
    CatalogueItem("rock", "Rock Anthem", "Band · 1:10", 70_000, "🎸"),
    CatalogueItem("piano", "Soft Piano", "Keys · 1:33", 93_000, "🎹"),
    CatalogueItem("summer", "Summer Beach", "Tropical · 0:56", 56_000, "🏖️"),
    CatalogueItem("gym", "Workout Rush", "EDM · 0:48", 48_000, "💪"),
)

/** Sound effects library shown in the Sound Effects panel. */
val SFX_CATALOGUE: List<CatalogueItem> = listOf(
    CatalogueItem("whoosh", "Whoosh", "Transition", 900, "💨"),
    CatalogueItem("pop", "Pop", "UI", 400, "🫧"),
    CatalogueItem("click", "Click", "UI", 300, "👆"),
    CatalogueItem("camera", "Camera Shutter", "Camera", 700, "📸"),
    CatalogueItem("typewriter", "Typewriter", "Texture", 2_400, "⌨️"),
    CatalogueItem("glitch", "Glitch", "Digital", 800, "📺"),
    CatalogueItem("boom", "Boom", "Impact", 1_600, "💥"),
    CatalogueItem("swoosh", "Swoosh", "Transition", 600, "🌀"),
    CatalogueItem("bell", "Bell", "Notification", 1_200, "🔔"),
    CatalogueItem("coin", "Coin", "Game", 700, "🪙"),
    CatalogueItem("sparkle", "Sparkle", "Magic", 1_000, "✨"),
    CatalogueItem("drum", "Drum Hit", "Percussion", 500, "🥁"),
)

/** Sticker pack shown in the Sticker panel. */
val STICKER_CATALOGUE: List<StickerItem> = listOf(
    StickerItem("heart", "Heart", "❤️"),
    StickerItem("sparkle", "Sparkle", "✨"),
    StickerItem("fire", "Fire", "🔥"),
    StickerItem("party", "Party", "🎉"),
    StickerItem("star", "Star", "⭐"),
    StickerItem("cool", "Cool", "😎"),
    StickerItem("wow", "Wow", "😮"),
    StickerItem("love", "Love", "😍"),
    StickerItem("sad", "Sad", "😢"),
    StickerItem("angry", "Angry", "😡"),
    StickerItem("thumbsup", "Thumbs up", "👍"),
    StickerItem("clap", "Clap", "👏"),
    StickerItem("check", "Check", "✅"),
    StickerItem("arrow", "Arrow", "➡️"),
    StickerItem("pin", "Pin", "📍"),
    StickerItem("bulb", "Idea", "💡"),
    StickerItem("rocket", "Rocket", "🚀"),
    StickerItem("sun", "Sun", "☀️"),
    StickerItem("moon", "Moon", "🌙"),
    StickerItem("rainbow", "Rainbow", "🌈"),
    StickerItem("cake", "Cake", "🎂"),
)

/** Font choices shown in the Text panel. */
enum class EditorFont(val label: String) {
    DEFAULT("Default"),
    BOLD("Bold"),
    SERIF("Serif"),
    MONO("Mono"),
    ROUNDED("Rounded"),
}

/** Effects that ship in the Effects panel. Deliberately simple, shader-free looks. */
enum class EffectPreset(val label: String) {
    NONE("None"),
    BLUR("Blur"),
    SHARPEN("Sharpen"),
    GLOW("Glow"),
    FILM_GRAIN("Grain"),
    VIGNETTE("Vignette"),
    SEPIA_TONE("Old Film"),
    BLUR_BG("Blur BG"),
    CHROMA_EDGE("Edge"),
    LIGHT_LEAK("Light Leak"),
}
