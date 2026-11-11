package com.snapzy.app.core.media

import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.common.Effect
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
    var appliedEffects by remember(filePath) { mutableStateOf(emptyList<Effect>()) }
    // Media3 rebuilds the video renderer when the effects change, which
    // detaches the surface, so the view is kept to hand back to the player.
    val textureView = remember(filePath) { arrayOfNulls<TextureView>(1) }
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
    //
    // Swapping the video renderer while the player is still preparing leaves
    // it stuck, so the effects are only applied once it is ready.
    LaunchedEffect(exoPlayer, lookGrade) {
        val grade = lookGrade
        // The chain always holds a frame processor, even with no grade: a
        // player with an empty effect list does not draw its first frame
        // while it is paused, so the preview would stay black.
        val effects = buildList {
            add(
                ScaleAndRotateTransformation.Builder()
                    .setScale(1f, 1f)
                    .setRotationDegrees(0f)
                    .build(),
            )
            if (grade != null) addAll(colorEffects(grade.filter, grade.adjust))
        }
        if (effects == appliedEffects) return@LaunchedEffect

        while (exoPlayer.playbackState == ExoPlayer.STATE_IDLE) {
            delay(50)
        }
        runCatching { exoPlayer.setVideoEffects(effects) }
        appliedEffects = effects
        // The renderer was rebuilt, so the surface has to be handed back.
        textureView[0]?.let { view -> runCatching { exoPlayer.setVideoTextureView(view) } }
    }

    // A paused player does not always draw its first frame, so the surface is
    // handed back once the player is ready. That makes the preview show the
    // poster frame straight away instead of only after playback starts.
    LaunchedEffect(exoPlayer) {
        while (exoPlayer.playbackState == ExoPlayer.STATE_IDLE) {
            delay(50)
        }
        textureView[0]?.let { view -> runCatching { exoPlayer.setVideoTextureView(view) } }
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
        update = { view ->
            // Only remember the view: handing the surface back on every
            // recomposition would restart the renderer over and over.
            textureView[0] = view
        },
        modifier = modifier,
    )
}

/**
 * The preview carries a real colour grade on Android 10 and newer, where
 * Media3 can render video effects into the playback surface.
 */
actual fun supportsPreviewColourGrade(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
