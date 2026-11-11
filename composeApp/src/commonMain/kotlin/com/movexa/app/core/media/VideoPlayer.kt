package com.movexa.app.core.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Native video playback surface (ExoPlayer on Android, AVPlayer on iOS). */
@Composable
expect fun VideoPlayerSurface(
    filePath: String,
    isPlaying: Boolean,
    seekToMs: Long?,
    onPositionChanged: (positionMs: Long, durationMs: Long) -> Unit,
    modifier: Modifier,
    /**
     * The colour grade applied to the previewed frames, or `null` for the
     * untouched image. This is the same grade the export uses, so the preview
     * shows what the user is making.
     */
    lookGrade: com.movexa.app.core.editing.LookGrade? = null,
)

/**
 * Whether the platform can colour-grade the preview itself.
 *
 * When it can, the editor does not need to fake the grade with translucent
 * tints over the player.
 */
expect fun supportsPreviewColourGrade(): Boolean
