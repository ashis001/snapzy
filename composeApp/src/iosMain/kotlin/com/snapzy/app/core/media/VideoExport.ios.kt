@file:OptIn(ExperimentalForeignApi::class)

package com.snapzy.app.core.media

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAssetExportPresetHighestQuality
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVFileTypeQuickTimeMovie
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMutableComposition
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addMutableTrackWithMediaType
import platform.AVFoundation.insertTimeRange
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.tracksWithMediaType
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.kCMPersistentTrackID_Invalid
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

/**
 * AVAssetExportSession.timeRange isn't exposed by this Kotlin/Native AVFoundation
 * binding, so trimming goes through an AVMutableComposition instead: copy only the
 * [startMs, endMs) range of the source's video (and, unless [muted], audio) track
 * into a new composition, then export that composition as-is.
 */
actual fun trimAndExportVideo(
    sourcePath: String,
    startMs: Long,
    endMs: Long,
    muted: Boolean,
    onComplete: (outputPath: String?) -> Unit,
) {
    val asset = AVURLAsset(NSURL.fileURLWithPath(sourcePath), options = null)
    val range = CMTimeRangeMake(
        start = CMTimeMakeWithSeconds(startMs / 1000.0, 600),
        duration = CMTimeMakeWithSeconds((endMs - startMs) / 1000.0, 600),
    )
    val zero = CMTimeMake(0, 1)

    val composition = AVMutableComposition()

    val sourceVideoTrack = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
    val compVideoTrack = composition.addMutableTrackWithMediaType(AVMediaTypeVideo, kCMPersistentTrackID_Invalid)
    if (sourceVideoTrack != null && compVideoTrack != null) {
        compVideoTrack.insertTimeRange(range, ofTrack = sourceVideoTrack, atTime = zero, error = null)
        compVideoTrack.preferredTransform = sourceVideoTrack.preferredTransform
    }

    if (!muted) {
        val sourceAudioTrack = asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack
        val compAudioTrack = composition.addMutableTrackWithMediaType(AVMediaTypeAudio, kCMPersistentTrackID_Invalid)
        if (sourceAudioTrack != null && compAudioTrack != null) {
            compAudioTrack.insertTimeRange(range, ofTrack = sourceAudioTrack, atTime = zero, error = null)
        }
    }

    val outputPath = NSTemporaryDirectory() + NSUUID().UUIDString() + ".mov"
    val exportSession = AVAssetExportSession(composition, AVAssetExportPresetHighestQuality)
    exportSession.outputURL = NSURL.fileURLWithPath(outputPath)
    exportSession.outputFileType = AVFileTypeQuickTimeMovie
    exportSession.exportAsynchronouslyWithCompletionHandler {
        if (exportSession.status == AVAssetExportSessionStatusCompleted) {
            onComplete(outputPath)
        } else {
            onComplete(null)
        }
    }
}
