@file:OptIn(ExperimentalForeignApi::class)

package com.snapzy.app.core.media

import com.snapzy.app.core.editing.Overlay
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAssetReader
import platform.AVFoundation.AVAssetReaderTrackOutput
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.naturalSize
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.tracksWithMediaType
import platform.CoreImage.CIImage
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSURL

/**
 * Reads the frames of a picture-in-picture clip.
 *
 * The PIP clip is its own asset, so it needs its own decoder. When the
 * overlay's window is longer than the clip the frames loop, which is what a
 * repeated PIP looks like in the editor.
 */
internal class PipFrameSource(private val overlay: Overlay.Pip) {

    private val asset = AVURLAsset(NSURL.fileURLWithPath(overlay.path), options = null)

    /** The PIP's own video track. */
    val track: AVAssetTrack? =
        asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack

    private var reader: AVAssetReader? = null
    private var output: AVAssetReaderTrackOutput? = null
    private var nextBuffer: CMSampleBufferRef? = null
    private var nextTime = 0.0
    private var currentBuffer: CMSampleBufferRef? = null
    private var exhausted = false
    private var lastTime = 0.0

    private val trimStartSeconds = overlay.trimStartMs.coerceAtLeast(0L) / 1000.0
    private val trimDurationSeconds =
        (overlay.trimEndMs - overlay.trimStartMs).coerceAtLeast(1L) / 1000.0

    /** The frame on screen at [localTime] seconds into the PIP's own window. */
    fun frameAt(localTime: Double): CIImage? {
        val sourceTrack = track ?: return null
        if (trimDurationSeconds <= 0.0) return null

        // Loop when the window is longer than the clip.
        val target = trimStartSeconds + (localTime % trimDurationSeconds)
        if (target < lastTime) {
            restart(sourceTrack)
        }
        lastTime = target

        open(sourceTrack)
        if (exhausted) return null

        while (nextBuffer != null && nextTime <= target + TIME_EPSILON) {
            currentBuffer = nextBuffer
            nextBuffer = output?.copyNextSampleBuffer()
            nextTime = nextBuffer?.let { CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(it)) }
                ?: Double.MAX_VALUE
        }

        val buffer = currentBuffer ?: return null
        val pixelBuffer = CMSampleBufferGetImageBuffer(buffer) ?: return null
        return CIImage(cVPixelBuffer = pixelBuffer)
    }

    fun close() {
        reader?.cancelReading()
        reader = null
        output = null
        nextBuffer = null
        currentBuffer = null
        exhausted = true
    }

    private fun restart(sourceTrack: AVAssetTrack) {
        reader?.cancelReading()
        reader = null
        output = null
        nextBuffer = null
        currentBuffer = null
        exhausted = false
        open(sourceTrack)
    }

    private fun open(sourceTrack: AVAssetTrack) {
        if (reader != null || exhausted) return
        val assetReader = AVAssetReader(asset, null)
        val trackOutput = AVAssetReaderTrackOutput(
            sourceTrack,
            mapOf<Any?, Any?>(
                kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA,
            ),
        )
        assetReader.addOutput(trackOutput)
        assetReader.timeRange = CMTimeRangeMake(
            start = CMTimeMake((trimStartSeconds * TIMESCALE).toLong(), TIMESCALE),
            duration = CMTimeMake((trimDurationSeconds * TIMESCALE).toLong(), TIMESCALE),
        )
        if (!assetReader.startReading()) {
            exhausted = true
            return
        }
        reader = assetReader
        output = trackOutput
        nextBuffer = trackOutput.copyNextSampleBuffer()
        nextTime = nextBuffer?.let { CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(it)) }
            ?: Double.MAX_VALUE
    }

    private companion object {
        const val TIMESCALE = 600
        const val TIME_EPSILON = 0.0005
    }
}
