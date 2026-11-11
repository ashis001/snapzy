package com.movexa.app.core.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The on-disk shape of a saved edit.
 *
 * `core/editing/EditModels.kt` deliberately stays free of serialisation
 * annotations: it holds Compose types (`Offset`, `Color`) and raw bytes, none
 * of which belong in a stored format. This file mirrors the model with
 * primitives only, so a draft written by one build can still be read by the
 * next one.
 *
 * Two rules keep old drafts readable:
 *
 * - Enums are stored by name and read back with a fallback to the first
 *   constant, so a draft written by a newer build never crashes an older one.
 * - Every field has a default, so a field added later does not break the
 *   files written before it.
 */
@Serializable
data class DraftSnapshot(
    val id: String,
    val title: String = "",
    /** Epoch millis. */
    val createdAt: Long = 0L,
    /** Epoch millis. */
    val updatedAt: Long = 0L,
    /** The file the poster frame comes from: the first clip, or the still. */
    val thumbnailPath: String? = null,
    /** [com.movexa.app.core.editing.EditKind] by name. */
    val kind: String = "VIDEO",
    val clips: List<ClipSnapshot> = emptyList(),
    val imagePath: String? = null,
    /** The still's bytes, base64 encoded. */
    val imageBytesBase64: String? = null,
    val overlays: List<OverlaySnapshot> = emptyList(),
    val audioTracks: List<AudioTrackSnapshot> = emptyList(),
    val canvas: CanvasSnapshot = CanvasSnapshot(),
    /** [com.movexa.app.core.editing.TransitionKind] by name. */
    val transition: String = "NONE",
    val durationMs: Long = 0L,
)

/** One clip on the timeline. */
@Serializable
data class ClipSnapshot(
    val id: Long = 0L,
    val path: String = "",
    val sourceDurationMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val rotation: Int = 0,
    val flipped: Boolean = false,
    /** [com.movexa.app.core.editing.FilterPreset] by name. */
    val filter: String = "ORIGINAL",
    val filterIntensity: Float = 1f,
    val adjust: AdjustSnapshot = AdjustSnapshot(),
)

/** The colour grade applied to a clip. */
@Serializable
data class AdjustSnapshot(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val hue: Float = 0f,
    val temperature: Float = 0f,
)

/**
 * A layer on top of the media.
 *
 * Modelled as a sealed class so the subtype is part of the stored format:
 * the discriminator (`type`) is written next to the shared fields and read
 * back into the matching subtype.
 */
@Serializable
sealed class OverlaySnapshot {

    abstract val id: Long
    abstract val startMs: Long
    abstract val endMs: Long

    /** Normalised canvas position; `Offset.x` / `Offset.y`. */
    abstract val x: Float
    abstract val y: Float

    abstract val scale: Float
    abstract val rotation: Float
    abstract val opacity: Float

    /** A text layer. */
    @Serializable
    @SerialName("text")
    data class Text(
        override val id: Long,
        override val startMs: Long = 0L,
        override val endMs: Long = Long.MAX_VALUE,
        override val x: Float = 0f,
        override val y: Float = 0f,
        override val scale: Float = 1f,
        override val rotation: Float = 0f,
        override val opacity: Float = 1f,
        val text: String = "",
        /** ARGB, packed into the low 32 bits. */
        val color: Long = 0xFFFFFFFFL,
        val fontSizeSp: Float = 28f,
        val bold: Boolean = false,
        val outline: Boolean = true,
        val shadow: Boolean = false,
        val backgroundLabel: Boolean = false,
    ) : OverlaySnapshot()

    /** An emoji sticker. */
    @Serializable
    @SerialName("sticker")
    data class Sticker(
        override val id: Long,
        override val startMs: Long = 0L,
        override val endMs: Long = Long.MAX_VALUE,
        override val x: Float = 0f,
        override val y: Float = 0f,
        override val scale: Float = 1f,
        override val rotation: Float = 0f,
        override val opacity: Float = 1f,
        val emoji: String = "",
        val label: String = "",
        /** ARGB, packed into the low 32 bits. */
        val color: Long = 0xFFFFFFFFL,
    ) : OverlaySnapshot()

    /** A freehand stroke. */
    @Serializable
    @SerialName("drawing")
    data class Drawing(
        override val id: Long,
        override val startMs: Long = 0L,
        override val endMs: Long = Long.MAX_VALUE,
        override val x: Float = 0f,
        override val y: Float = 0f,
        override val scale: Float = 1f,
        override val rotation: Float = 0f,
        override val opacity: Float = 1f,
        /** The stroke, as x / y pairs. */
        val points: List<Pair<Float, Float>> = emptyList(),
        /** ARGB, packed into the low 32 bits. */
        val color: Long = 0xFFFFFFFFL,
        val strokeWidth: Float = 8f,
    ) : OverlaySnapshot()

    /** A picture-in-picture clip. */
    @Serializable
    @SerialName("pip")
    data class Pip(
        override val id: Long,
        override val startMs: Long = 0L,
        override val endMs: Long = Long.MAX_VALUE,
        override val x: Float = 0f,
        override val y: Float = 0f,
        override val scale: Float = 1f,
        override val rotation: Float = 0f,
        override val opacity: Float = 1f,
        val path: String = "",
        val sourceDurationMs: Long = 0L,
        val trimStartMs: Long = 0L,
        val trimEndMs: Long = 0L,
        val chromaEnabled: Boolean = false,
        /** ARGB, packed into the low 32 bits. */
        val chromaColor: Long = 0xFF00FF00L,
        /** [com.movexa.app.core.editing.MaskShape] by name. */
        val maskShape: String = "NONE",
        /** [com.movexa.app.core.editing.BlendMode] by name. */
        val blend: String = "NORMAL",
    ) : OverlaySnapshot()
}

/** The canvas the edit is composed on. */
@Serializable
data class CanvasSnapshot(
    /** [com.movexa.app.core.editing.AspectRatio] by name. */
    val aspect: String = "ORIGINAL",
    /** [com.movexa.app.core.editing.BackgroundStyle] by name. */
    val background: String = "BLACK",
    val backgroundImagePath: String? = null,
    val blurredBackground: Boolean = false,
)

/** A music track, sound effect or voice-over on the timeline. */
@Serializable
data class AudioTrackSnapshot(
    val id: Long = 0L,
    val label: String = "",
    /** [com.movexa.app.core.editing.AudioKind] by name. */
    val kind: String = "MUSIC",
    val sourcePath: String? = null,
    val startMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val muted: Boolean = false,
)
