@file:OptIn(ExperimentalForeignApi::class)

package com.snapzy.app.core.media

import com.snapzy.app.core.editing.EditProject
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAssetExportPresetHighestQuality
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVFileTypeMPEG4
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
import kotlin.coroutines.resume

/**
 * iOS render path.
 *
 * Uses AVFoundation to compose the trimmed clips (and, unless muted, their
 * audio) into one mp4. Text and sticker overlays are composited by the shared
 * Compose preview layer on iOS for now — the Android path bakes them into the
 * render via Media3 texture overlays.
 */
actual suspend fun renderVideoProject(
    project: EditProject,
    settings: ExportSettings,
    onProgress: (Float) -> Unit,
): String? {
    if (project.clips.isEmpty()) return null

    val composition = AVMutableComposition()
    val zero = CMTimeMake(0, 1)

    project.clips.forEach { clip ->
        val asset = AVURLAsset(NSURL.fileURLWithPath(clip.path), options = null)
        val range = CMTimeRangeMake(
            start = CMTimeMakeWithSeconds(clip.trimStartMs / 1000.0, 600),
            duration = CMTimeMakeWithSeconds(
                (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(1L) / 1000.0,
                600,
            ),
        )

        val sourceVideoTrack = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
        val compVideoTrack = composition.addMutableTrackWithMediaType(
            AVMediaTypeVideo,
            kCMPersistentTrackID_Invalid,
        )
        if (sourceVideoTrack != null && compVideoTrack != null) {
            compVideoTrack.insertTimeRange(range, ofTrack = sourceVideoTrack, atTime = zero, error = null)
            compVideoTrack.preferredTransform = sourceVideoTrack.preferredTransform
        }

        if (!clip.muted) {
            val sourceAudioTrack = asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack
            val compAudioTrack = composition.addMutableTrackWithMediaType(
                AVMediaTypeAudio,
                kCMPersistentTrackID_Invalid,
            )
            if (sourceAudioTrack != null && compAudioTrack != null) {
                compAudioTrack.insertTimeRange(range, ofTrack = sourceAudioTrack, atTime = zero, error = null)
            }
        }
    }

    val outputPath = NSTemporaryDirectory() + NSUUID().UUIDString() + ".mp4"
    val exportSession = AVAssetExportSession(composition, AVAssetExportPresetHighestQuality) ?: return null
    exportSession.outputURL = NSURL.fileURLWithPath(outputPath)
    exportSession.outputFileType = AVFileTypeMPEG4

    return suspendCancellableCoroutine { continuation ->
        exportSession.exportAsynchronouslyWithCompletionHandler {
            if (exportSession.status == AVAssetExportSessionStatusCompleted) {
                onProgress(100f)
                if (continuation.isActive) continuation.resume(outputPath)
            } else {
                if (continuation.isActive) continuation.resume(null)
            }
        }
        continuation.invokeOnCancellation { exportSession.cancelExport() }
    }
}
