package com.snapzy.app.core.media

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
)
