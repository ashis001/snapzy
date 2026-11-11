package com.movexa.app.core.editing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * The shared edit model for both the photo and video editors.
 *
 * Everything here is plain data so the same model can drive the preview, the
 * timeline, the export planner and (later) draft persistence. Platform code
 * only ever reads this model — it never owns it.
 *
 * Mirrors the feature set observed in InShot 2.243 (see
 * docs/INSHOT-2.243-FEATURE-REPORT.md), but every asset in here is ours.
 */

enum class EditKind { VIDEO, PHOTO }

/** Canvas / crop aspect presets. [ratio] is width / height, `null` = keep source. */
enum class AspectRatio(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null),
    SQUARE("1:1", 1f),
    PORTRAIT("9:16", 9f / 16f),
    PORTRAIT_4_5("4:5", 4f / 5f),
    PORTRAIT_3_4("3:4", 3f / 4f),
    LANDSCAPE("16:9", 16f / 9f),
    CINEMA("2.35:1", 2.35f),
}

/** Bundled look presets. Each maps to a colour matrix in the preview and a LUT/colour pipeline at export. */
enum class FilterPreset(val label: String) {
    ORIGINAL("Original"),
    BRIGHT("Bright"),
    VIVID("Vivid"),
    WARM("Warm"),
    COOL("Cool"),
    MONO("Mono"),
    FILM("Film"),
    FADE("Fade"),
    SEPIA("Sepia"),
    NIGHT("Night"),
    SUNSET("Sunset"),
    MINT("Mint"),
}

/** Colour grading. Ranges match the preview sliders 1:1 so the two never drift. */
data class AdjustSpec(
    val brightness: Float = 0f,   // -1f .. 1f
    val contrast: Float = 1f,     // 0.5f .. 1.5f
    val saturation: Float = 1f,   // 0f .. 2f
    val hue: Float = 0f,          // -180f .. 180f
    val temperature: Float = 0f,  // -1f .. 1f
) {
    val isIdentity: Boolean
        get() = brightness == 0f && contrast == 1f && saturation == 1f && hue == 0f && temperature == 0f
}

/** A single video clip on the timeline. */
data class VideoClip(
    val id: Long,
    val path: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val rotation: Int = 0,
    val flipped: Boolean = false,
    val filter: FilterPreset = FilterPreset.ORIGINAL,
    val filterIntensity: Float = 1f,
    val adjust: AdjustSpec = AdjustSpec(),
) {
    /** Length of this clip on the timeline once speed is applied. */
    val timelineDurationMs: Long
        get() = (((trimEndMs - trimStartMs).coerceAtLeast(0L)) / speed.coerceAtLeast(0.05f)).toLong()

    val trimDurationMs: Long get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)
}

enum class BackgroundStyle { BLACK, WHITE, BLUR, CUSTOM }

data class CanvasSpec(
    val aspect: AspectRatio = AspectRatio.ORIGINAL,
    val background: BackgroundStyle = BackgroundStyle.BLACK,
    val backgroundImagePath: String? = null,
    val blurredBackground: Boolean = false,
)

enum class AudioKind { MUSIC, SOUND_EFFECT, VOICEOVER }

data class AudioTrack(
    val id: Long,
    val label: String,
    val kind: AudioKind,
    /** `null` for bundled catalogue entries that have no local file yet. */
    val sourcePath: String? = null,
    val startMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val muted: Boolean = false,
)

/** Anything that lives on top of the media: text, stickers, doodles, PIP clips. */
sealed interface Overlay {
    val id: Long
    /** Timeline window. Video only; photos use the whole timeline. */
    val startMs: Long
    val endMs: Long
    /** Normalised position inside the canvas, 0..1 on both axes. */
    val position: Offset
    val scale: Float
    val rotation: Float
    val opacity: Float

    data class Text(
        override val id: Long,
        override val startMs: Long = 0L,
        override val endMs: Long = Long.MAX_VALUE,
        override val position: Offset = Offset(0.5f, 0.25f),
        override val scale: Float = 1f,
        override val rotation: Float = 0f,
        override val opacity: Float = 1f,
        val text: String = "Add text",
        val color: Color = Color.White,
        val fontSizeSp: Float = 28f,
        val bold: Boolean = false,
        val outline: Boolean = true,
        val shadow: Boolean = false,
        val backgroundLabel: Boolean = false,
    ) : Overlay

    data class Sticker(
        override val id: Long,
        override val startMs: Long = 0L,
        override val endMs: Long = Long.MAX_VALUE,
        override val position: Offset = Offset(0.5f, 0.5f),
        override val scale: Float = 1f,
        override val rotation: Float = 0f,
        override val opacity: Float = 1f,
        val emoji: String = "✨",
        val label: String = "Sticker",
        val color: Color = Color.White,
    ) : Overlay

    data class Drawing(
        override val id: Long,
        override val startMs: Long = 0L,
        override val endMs: Long = Long.MAX_VALUE,
        override val position: Offset = Offset.Zero,
        override val scale: Float = 1f,
        override val rotation: Float = 0f,
        override val opacity: Float = 1f,
        val points: List<Offset> = emptyList(),
        val color: Color = Color.White,
        val strokeWidth: Float = 8f,
    ) : Overlay

    data class Pip(
        override val id: Long,
        override val startMs: Long = 0L,
        override val endMs: Long = Long.MAX_VALUE,
        override val position: Offset = Offset(0.75f, 0.25f),
        override val scale: Float = 0.35f,
        override val rotation: Float = 0f,
        override val opacity: Float = 1f,
        val path: String,
        val sourceDurationMs: Long = 0L,
        val trimStartMs: Long = 0L,
        val trimEndMs: Long = 0L,
        val chromaEnabled: Boolean = false,
        val chromaColor: Color = Color.Green,
        val maskShape: MaskShape = MaskShape.NONE,
        val blend: BlendMode = BlendMode.NORMAL,
    ) : Overlay
}

/** Shape masks available for PIP and sticker layers. */
enum class MaskShape(val label: String) {
    NONE("None"),
    CIRCLE("Circle"),
    SQUARE("Square"),
    ROUNDED("Rounded"),
    HEART("Heart"),
    STAR("Star"),
    DIAMOND("Diamond"),
}

/** Blend / composite modes for PIP layers. */
enum class BlendMode(val label: String) {
    NORMAL("Normal"),
    LIGHTEN("Lighten"),
    SCREEN("Screen"),
    DODGE("Dodge"),
    DARKEN("Darken"),
    MULTIPLY("Multiply"),
    OVERLAY("Overlay"),
    HARD_LIGHT("Hard light"),
    SOFT_LIGHT("Soft light"),
    DIFFERENCE("Difference"),
}

/** Transition applied between two adjacent clips. */
enum class TransitionKind(val label: String) {
    NONE("None"),
    FADE("Fade"),
    SLIDE("Slide"),
    ZOOM("Zoom"),
    WIPE("Wipe"),
    BLUR("Blur"),
    DISSOLVE("Dissolve"),
    ;

    /**
     * Whether the export can render this transition.
     *
     * Media3 has no transition primitive and its video compositor does not
     * run in the export path, so a transition is drawn by cutting the
     * incoming clip's opening into stills and laying them over the outgoing
     * clip's tail. Everything but the blur can be drawn that way.
     */
    val isRenderedInExport: Boolean
        get() = this != BLUR
}

/** The whole edit. The controller mutates copies of this. */
data class EditProject(
    val kind: EditKind,
    val clips: List<VideoClip> = emptyList(),
    /** Photo projects edit a single still. */
    val imagePath: String? = null,
    val imageBytes: ByteArray? = null,
    val overlays: List<Overlay> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val canvas: CanvasSpec = CanvasSpec(),
    /** Transition applied between adjacent clips. */
    val transition: TransitionKind = TransitionKind.NONE,
    val durationMs: Long = 0L,
) {
    val isEmpty: Boolean get() = clips.isEmpty() && imageBytes == null && imagePath == null
}

/** A bundled music / sound-effect catalogue entry. */
data class CatalogueItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val durationMs: Long = 0L,
    val emoji: String = "♪",
)

/** Bundled sticker catalogue entry. */
data class StickerItem(
    val id: String,
    val label: String,
    val emoji: String,
)
