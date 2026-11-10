package com.snapzy.app.core.media

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import java.io.File

/**
 * The preview surface for the video editor.
 *
 * Uses a [TextureView] rather than Media3's `PlayerView`, because a
 * `SurfaceView` lives in its own hardware layer *behind* the window: Compose
 * then paints over it and the video is invisible. A `TextureView` is a normal
 * view, so it composites with Compose and the overlay layers (text, stickers,
 * doodles) draw on top of it correctly.
 */
@Composable
actual fun VideoPlayerSurface(
    filePath: String,
    isPlaying: Boolean,
    seekToMs: Long?,
    onPositionChanged: (positionMs: Long, durationMs: Long) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val exoPlayer = remember(filePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(filePath))))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    LaunchedEffect(seekToMs) {
        seekToMs?.let { exoPlayer.seekTo(it) }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            val duration = exoPlayer.duration.coerceAtLeast(0L)
            onPositionChanged(exoPlayer.currentPosition.coerceAtLeast(0L), duration)
            delay(100)
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        texture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        exoPlayer.setVideoTextureView(this@apply)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        texture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) = Unit

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        exoPlayer.clearVideoTextureView(this@apply)
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
        modifier = modifier,
    )
}
