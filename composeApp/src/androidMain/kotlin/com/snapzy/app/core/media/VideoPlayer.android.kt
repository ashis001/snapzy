package com.snapzy.app.core.media

import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.View
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
    lookGrade: com.snapzy.app.core.editing.LookGrade?,
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

    // The preview carries the real grade: Media3 renders the colour effects
    // into the surface, so the user sees the same pixels the export will have.
    LaunchedEffect(exoPlayer, lookGrade) {
        val grade = lookGrade
        val effects = if (grade == null) {
            emptyList()
        } else {
            colorEffects(grade.filter, grade.adjust)
        }
        runCatching { exoPlayer.setVideoEffects(effects) }
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
        update = { view -> view.setRenderEffect(null) },
        modifier = modifier,
    )
}

/**
 * The preview carries a real colour grade on Android 10 and newer, where
 * Media3 can render video effects into the playback surface.
 */
actual fun supportsPreviewColourGrade(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
