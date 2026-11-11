@file:OptIn(ExperimentalForeignApi::class)

package com.snapzy.app.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.snapzy.app.core.editing.LookGrade
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView

/**
 * Keeps the AVPlayerLayer's frame in sync via layoutSubviews — the system calls this
 * every time the view's own bounds actually change, unlike Compose's UIKitView `update`
 * callback, which can fire before the view has been laid out to its final size.
 */
private class PlayerContainerView(
    frame: CValue<CGRect>,
    private val playerLayer: AVPlayerLayer,
) : UIView(frame) {
    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        playerLayer.frame = this.bounds
        CATransaction.commit()
    }
}

@Composable
actual fun VideoPlayerSurface(
    filePath: String,
    isPlaying: Boolean,
    seekToMs: Long?,
    onPositionChanged: (positionMs: Long, durationMs: Long) -> Unit,
    modifier: Modifier,
    lookGrade: LookGrade?,
) {
    val player = remember(filePath) { AVPlayer(uRL = NSURL.fileURLWithPath(filePath)) }

    val playerLayer = remember(player) {
        AVPlayerLayer().apply {
            this.player = player
            this.videoGravity = AVLayerVideoGravityResizeAspect
        }
    }

    DisposableEffect(player) { onDispose { player.pause() } }
    LaunchedEffect(isPlaying) { if (isPlaying) player.play() else player.pause() }
    LaunchedEffect(seekToMs) {
        seekToMs?.let { ms -> player.seekToTime(CMTimeMakeWithSeconds(ms / 1000.0, 600)) }
    }
    LaunchedEffect(player) {
        while (true) {
            val durationSeconds = CMTimeGetSeconds(player.currentItem?.duration ?: CMTimeMake(0, 1))
            val positionSeconds = CMTimeGetSeconds(player.currentTime())
            onPositionChanged((positionSeconds * 1000).toLong(), (durationSeconds * 1000).toLong())
            delay(100)
        }
    }

    UIKitView(
        factory = {
            PlayerContainerView(CGRectMake(0.0, 0.0, 0.0, 0.0), playerLayer).apply {
                backgroundColor = UIColor.blackColor
                layer.addSublayer(playerLayer)
            }
        },
        modifier = modifier,
        onRelease = { playerLayer.removeFromSuperlayer() },
    )
}


/**
 * iOS cannot colour-grade the live preview.
 *
 * `AVPlayerItem.videoComposition` — the only way AVFoundation can filter the
 * frames a player shows — is not exposed by the Kotlin/Native bindings, so the
 * editor falls back to its translucent approximation here. The exported file
 * is graded properly on both platforms.
 */
actual fun supportsPreviewColourGrade(): Boolean = false
