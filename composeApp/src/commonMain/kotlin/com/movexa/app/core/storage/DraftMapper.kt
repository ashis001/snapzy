package com.movexa.app.core.storage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.movexa.app.core.editing.AdjustSpec
import com.movexa.app.core.editing.AudioKind
import com.movexa.app.core.editing.AudioTrack
import com.movexa.app.core.editing.AspectRatio
import com.movexa.app.core.editing.BackgroundStyle
import com.movexa.app.core.editing.BlendMode
import com.movexa.app.core.editing.CanvasSpec
import com.movexa.app.core.editing.EditKind
import com.movexa.app.core.editing.EditProject
import com.movexa.app.core.editing.FilterPreset
import com.movexa.app.core.editing.MaskShape
import com.movexa.app.core.editing.Overlay
import com.movexa.app.core.editing.TransitionKind
import com.movexa.app.core.editing.VideoClip
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Converts an [EditProject] into the storable [DraftSnapshot] and back.
 *
 * The round trip is lossless for every field the snapshot keeps: the same
 * project in, the same project out. Compose types are translated on the way
 * through — `Offset` becomes a pair of floats, `Color` becomes a packed
 * ARGB long, raw bytes become base64.
 */

/** Builds the snapshot for [project], stamped with [id], [title] and [createdAt]. */
fun EditProject.toSnapshot(id: String, title: String, createdAt: Long): DraftSnapshot = DraftSnapshot(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = createdAt,
    thumbnailPath = thumbnailPath(),
    kind = kind.name,
    clips = clips.map { it.toSnapshot() },
    imagePath = imagePath,
    imageBytesBase64 = imageBytes?.toBase64(),
    overlays = overlays.map { it.toSnapshot() },
    audioTracks = audioTracks.map { it.toSnapshot() },
    canvas = canvas.toSnapshot(),
    transition = transition.name,
    durationMs = durationMs,
)

/** Rebuilds the edited project a snapshot was taken from. */
fun DraftSnapshot.toProject(): EditProject = EditProject(
    kind = enumByName<EditKind>(kind),
    clips = clips.map { it.toProject() },
    imagePath = imagePath,
    imageBytes = imageBytesBase64?.fromBase64(),
    overlays = overlays.map { it.toProject() },
    audioTracks = audioTracks.map { it.toProject() },
    canvas = canvas.toProject(),
    transition = enumByName<TransitionKind>(transition),
    durationMs = durationMs,
)

/**
 * The file a draft's poster frame comes from.
 *
 * Video drafts show the first clip; photo drafts show the still. A photo
 * keeps its bytes in the project and has no file behind them, so it has no
 * thumbnail.
 */
internal fun EditProject.thumbnailPath(): String? =
    clips.firstOrNull()?.path?.takeIf { it.isNotBlank() }
        ?: imagePath?.takeIf { it.isNotBlank() }

/**
 * Reads an enum constant by name.
 *
 * An unknown name — a constant added by a newer build, say — falls back to
 * the first constant of the enum so an old draft still opens.
 */
internal inline fun <reified T : Enum<T>> enumByName(name: String?): T {
    val constants = enumValues<T>()
    return constants.firstOrNull { it.name == name } ?: constants.first()
}

/* ------------------------------------------------------------------ clips -- */

private fun VideoClip.toSnapshot(): ClipSnapshot = ClipSnapshot(
    id = id,
    path = path,
    sourceDurationMs = sourceDurationMs,
    trimStartMs = trimStartMs,
    trimEndMs = trimEndMs,
    speed = speed,
    volume = volume,
    muted = muted,
    rotation = rotation,
    flipped = flipped,
    filter = filter.name,
    filterIntensity = filterIntensity,
    adjust = adjust.toSnapshot(),
)

private fun ClipSnapshot.toProject(): VideoClip = VideoClip(
    id = id,
    path = path,
    sourceDurationMs = sourceDurationMs,
    trimStartMs = trimStartMs,
    trimEndMs = trimEndMs,
    speed = speed,
    volume = volume,
    muted = muted,
    rotation = rotation,
    flipped = flipped,
    filter = enumByName<FilterPreset>(filter),
    filterIntensity = filterIntensity,
    adjust = adjust.toProject(),
)

private fun AdjustSpec.toSnapshot(): AdjustSnapshot = AdjustSnapshot(
    brightness = brightness,
    contrast = contrast,
    saturation = saturation,
    hue = hue,
    temperature = temperature,
)

private fun AdjustSnapshot.toProject(): AdjustSpec = AdjustSpec(
    brightness = brightness,
    contrast = contrast,
    saturation = saturation,
    hue = hue,
    temperature = temperature,
)

/* ---------------------------------------------------------------- overlay -- */

private fun Overlay.toSnapshot(): OverlaySnapshot = when (this) {
    is Overlay.Text -> OverlaySnapshot.Text(
        id = id,
        startMs = startMs,
        endMs = endMs,
        x = position.x,
        y = position.y,
        scale = scale,
        rotation = rotation,
        opacity = opacity,
        text = text,
        color = color.toArgbLong(),
        fontSizeSp = fontSizeSp,
        bold = bold,
        outline = outline,
        shadow = shadow,
        backgroundLabel = backgroundLabel,
    )

    is Overlay.Sticker -> OverlaySnapshot.Sticker(
        id = id,
        startMs = startMs,
        endMs = endMs,
        x = position.x,
        y = position.y,
        scale = scale,
        rotation = rotation,
        opacity = opacity,
        emoji = emoji,
        label = label,
        color = color.toArgbLong(),
    )

    is Overlay.Drawing -> OverlaySnapshot.Drawing(
        id = id,
        startMs = startMs,
        endMs = endMs,
        x = position.x,
        y = position.y,
        scale = scale,
        rotation = rotation,
        opacity = opacity,
        points = points.map { it.x to it.y },
        color = color.toArgbLong(),
        strokeWidth = strokeWidth,
    )

    is Overlay.Pip -> OverlaySnapshot.Pip(
        id = id,
        startMs = startMs,
        endMs = endMs,
        x = position.x,
        y = position.y,
        scale = scale,
        rotation = rotation,
        opacity = opacity,
        path = path,
        sourceDurationMs = sourceDurationMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        chromaEnabled = chromaEnabled,
        chromaColor = chromaColor.toArgbLong(),
        maskShape = maskShape.name,
        blend = blend.name,
    )
}

private fun OverlaySnapshot.toProject(): Overlay = when (this) {
    is OverlaySnapshot.Text -> Overlay.Text(
        id = id,
        startMs = startMs,
        endMs = endMs,
        position = Offset(x, y),
        scale = scale,
        rotation = rotation,
        opacity = opacity,
        text = text,
        color = color.toComposeColor(),
        fontSizeSp = fontSizeSp,
        bold = bold,
        outline = outline,
        shadow = shadow,
        backgroundLabel = backgroundLabel,
    )

    is OverlaySnapshot.Sticker -> Overlay.Sticker(
        id = id,
        startMs = startMs,
        endMs = endMs,
        position = Offset(x, y),
        scale = scale,
        rotation = rotation,
        opacity = opacity,
        emoji = emoji,
        label = label,
        color = color.toComposeColor(),
    )

    is OverlaySnapshot.Drawing -> Overlay.Drawing(
        id = id,
        startMs = startMs,
        endMs = endMs,
        position = Offset(x, y),
        scale = scale,
        rotation = rotation,
        opacity = opacity,
        points = points.map { Offset(it.first, it.second) },
        color = color.toComposeColor(),
        strokeWidth = strokeWidth,
    )

    is OverlaySnapshot.Pip -> Overlay.Pip(
        id = id,
        startMs = startMs,
        endMs = endMs,
        position = Offset(x, y),
        scale = scale,
        rotation = rotation,
        opacity = opacity,
        path = path,
        sourceDurationMs = sourceDurationMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        chromaEnabled = chromaEnabled,
        chromaColor = chromaColor.toComposeColor(),
        maskShape = enumByName<MaskShape>(maskShape),
        blend = enumByName<BlendMode>(blend),
    )
}

/* ----------------------------------------------------------------- canvas -- */

private fun CanvasSpec.toSnapshot(): CanvasSnapshot = CanvasSnapshot(
    aspect = aspect.name,
    background = background.name,
    backgroundImagePath = backgroundImagePath,
    blurredBackground = blurredBackground,
)

private fun CanvasSnapshot.toProject(): CanvasSpec = CanvasSpec(
    aspect = enumByName<AspectRatio>(aspect),
    background = enumByName<BackgroundStyle>(background),
    backgroundImagePath = backgroundImagePath,
    blurredBackground = blurredBackground,
)

/* ------------------------------------------------------------------ audio -- */

private fun AudioTrack.toSnapshot(): AudioTrackSnapshot = AudioTrackSnapshot(
    id = id,
    label = label,
    kind = kind.name,
    sourcePath = sourcePath,
    startMs = startMs,
    durationMs = durationMs,
    volume = volume,
    muted = muted,
)

private fun AudioTrackSnapshot.toProject(): AudioTrack = AudioTrack(
    id = id,
    label = label,
    kind = enumByName<AudioKind>(kind),
    sourcePath = sourcePath,
    startMs = startMs,
    durationMs = durationMs,
    volume = volume,
    muted = muted,
)

/* ----------------------------------------------------------------- colour -- */

/** Packs a Compose colour into the low 32 bits as ARGB. */
private fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

/** Unpacks an ARGB long back into a Compose colour. */
private fun Long.toComposeColor(): Color = Color(toInt())

/* ------------------------------------------------------------------ bytes -- */

@OptIn(ExperimentalEncodingApi::class)
private fun ByteArray.toBase64(): String = Base64.encode(this)

@OptIn(ExperimentalEncodingApi::class)
private fun String.fromBase64(): ByteArray? = runCatching { Base64.decode(this) }.getOrNull()
