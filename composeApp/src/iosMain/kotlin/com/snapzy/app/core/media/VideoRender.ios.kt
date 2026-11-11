@file:OptIn(ExperimentalForeignApi::class)

package com.snapzy.app.core.media

import com.snapzy.app.core.editing.EditProject
import com.snapzy.app.core.editing.Overlay
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFoundation.AVAssetReader
import platform.AVFoundation.AVAssetReaderVideoCompositionOutput
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVAssetWriter
import platform.AVFoundation.AVAssetWriterInput
import platform.AVFoundation.AVAssetWriterStatusCompleted
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMutableComposition
import platform.AVFoundation.AVMutableVideoComposition
import platform.AVFoundation.AVMutableVideoCompositionInstruction
import platform.AVFoundation.AVMutableVideoCompositionLayerInstruction
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVVideoAverageBitRateKey
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeH264
import platform.AVFoundation.AVVideoCompressionPropertiesKey
import platform.AVFoundation.AVVideoExpectedSourceFrameRateKey
import platform.AVFoundation.AVVideoHeightKey
import platform.AVFoundation.AVVideoMaxKeyFrameIntervalKey
import platform.AVFoundation.AVVideoProfileLevelH264HighAutoLevel
import platform.AVFoundation.AVVideoProfileLevelKey
import platform.AVFoundation.AVVideoWidthKey
import platform.AVFoundation.addMutableTrackWithMediaType
import platform.AVFoundation.naturalSize
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.tracksWithMediaType
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.CoreGraphics.CGAffineTransformMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreImage.CIImage
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.kCMPersistentTrackID_Invalid
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import kotlin.math.max

/**
 * The iOS render pipeline.
 *
 * The edit is put together as an `AVMutableComposition` (trimmed, time-scaled
 * clips) and rendered through an `AVMutableVideoComposition`, which turns every
 * frame into the canvas: the source's own rotation, the clip's rotation and
 * flip, and the scale-to-fit that letterboxes the frame onto the canvas.
 *
 * The frames are then read with an `AVAssetReader`, graded with Core Image
 * (the filter preset plus the adjustment sliders), composited with the
 * text / sticker / doodle layers and the picture-in-picture clip, and written
 * out with an `AVAssetWriter` as H.264.
 *
 * The audio is mixed by AVFoundation — the clips' own audio, the music and
 * the sound effects — and written next to the video as AAC.
 */
actual suspend fun renderVideoProject(
    project: EditProject,
    settings: ExportSettings,
    onProgress: (Float) -> Unit,
): String? {
    if (project.clips.isEmpty()) return null

    return withContext(Dispatchers.Default) {
        try {
            renderOnWorker(project, settings, onProgress)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            null
        }
    }
}

private suspend fun renderOnWorker(
    project: EditProject,
    settings: ExportSettings,
    onProgress: (Float) -> Unit,
): String? {
    val sourceAspect = firstClipAspect(project)
    val (canvasWidth, canvasHeight) = canvasSize(project, settings, sourceAspect)
    val timeline = buildTimeline(project, canvasWidth, canvasHeight, settings.frameRate)
    if (timeline.totalDurationMs <= 0L) return null

    val outputPath = NSTemporaryDirectory() + "snapzy_render_" + NSUUID().UUIDString() + ".mp4"
    val writer = AVAssetWriter(NSURL.fileURLWithPath(outputPath), AVFileTypeMPEG4, null) ?: return null

    val videoInput = AVAssetWriterInput(
        AVMediaTypeVideo,
        mapOf<Any?, Any?>(
            AVVideoCodecKey to AVVideoCodecTypeH264,
            AVVideoWidthKey to canvasWidth,
            AVVideoHeightKey to canvasHeight,
            AVVideoCompressionPropertiesKey to mapOf<Any?, Any?>(
                AVVideoAverageBitRateKey to settings.videoBitrate,
                AVVideoExpectedSourceFrameRateKey to settings.frameRate,
                AVVideoMaxKeyFrameIntervalKey to settings.frameRate * 2,
                AVVideoProfileLevelKey to AVVideoProfileLevelH264HighAutoLevel,
            ),
        ),
    )
    videoInput.expectsMediaDataInRealTime = false
    if (!writer.canAddInput(videoInput)) return null
    writer.addInput(videoInput)

    // The audio bed is optional: a project with everything muted has none.
    val audioBed = buildIosAudioBed(project, timeline)
    var audioInput: AVAssetWriterInput? = null
    if (audioBed != null) {
        val candidate = AVAssetWriterInput(
            AVMediaTypeAudio,
            mapOf<Any?, Any?>(
                AVFormatIDKey to kAudioFormatMPEG4AAC,
                AVSampleRateKey to AUDIO_SAMPLE_RATE,
                AVNumberOfChannelsKey to 2,
                AVEncoderBitRateKey to AUDIO_BIT_RATE,
            ),
        )
        candidate.expectsMediaDataInRealTime = false
        if (writer.canAddInput(candidate)) {
            writer.addInput(candidate)
            audioInput = candidate
        }
    }

    // The composition: one video track per clip so the clips can overlap.
    val composition = AVMutableComposition()
    val clipTracks = mutableListOf<AVAssetTrack>()
    val clipSources = mutableListOf<ClipSource>()
    for (segment in timeline.clips) {
        val clip = segment.clip
        val asset = AVURLAsset(NSURL.fileURLWithPath(clip.path), options = null)
        val sourceTrack = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
        val compTrack = composition.addMutableTrackWithMediaType(
            AVMediaTypeVideo,
            kCMPersistentTrackID_Invalid,
        )
        // A clip whose file cannot be read would break the timeline, so the
        // whole render gives up rather than writing a broken file.
        if (sourceTrack == null || compTrack == null) return null

        val trimSeconds = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(1L) / 1000.0
        val at = CMTimeMakeWithSeconds(segment.startMs / 1000.0, TIMESCALE)
        val inserted = compTrack.insertTimeRange(
            timeRange = CMTimeRangeMake(
                start = CMTimeMakeWithSeconds(clip.trimStartMs.coerceAtLeast(0L) / 1000.0, TIMESCALE),
                duration = CMTimeMakeWithSeconds(trimSeconds, TIMESCALE),
            ),
            ofTrack = sourceTrack,
            atTime = at,
            error = null,
        )
        if (!inserted) return null

        // The clip's speed changes how long it lasts on the timeline.
        val speed = clip.speed.coerceAtLeast(0.05f)
        if (speed != 1f) {
            compTrack.scaleTimeRange(
                timeRange = CMTimeRangeMake(
                    start = at,
                    duration = CMTimeMakeWithSeconds(trimSeconds, TIMESCALE),
                ),
                toDuration = CMTimeMakeWithSeconds(trimSeconds / speed, TIMESCALE),
            )
        }
        clipTracks += compTrack
        clipSources += ClipSource(
            track = sourceTrack,
            layerTransform = layerTransform(
                preferred = sourceTrack.preferredTransformValue(),
                naturalWidth = sourceTrack.naturalWidth(),
                naturalHeight = sourceTrack.naturalHeight(),
                rotationDegrees = clip.rotation,
                flipped = clip.flipped,
                canvasWidth = canvasWidth.toDouble(),
                canvasHeight = canvasHeight.toDouble(),
            ),
        )
    }
    if (clipTracks.isEmpty()) return null

    val videoComposition = buildVideoComposition(timeline, clipSources, canvasWidth, canvasHeight, settings.frameRate)

    if (!writer.startWriting()) return null
    writer.startSessionAtSourceTime(CMTimeMake(0, 1))

    val canvasRect = CGRectMake(0.0, 0.0, canvasWidth.toDouble(), canvasHeight.toDouble())
    val overlayRenderer = IosOverlayRenderer(project.overlays, canvasWidth, canvasHeight)
    val pipOverlays = project.overlays.filterIsInstance<Overlay.Pip>()
    val pipSources = pipOverlays.map { PipFrameSource(it) }

    val reader = AVAssetReader(composition, null)
    val videoOutput = AVAssetReaderVideoCompositionOutput(
        composition.tracksWithMediaType(AVMediaTypeVideo),
        mapOf<Any?, Any?>(
            kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA,
        ),
    )
    videoOutput.videoComposition = videoComposition
    reader.addOutput(videoOutput)
    if (!reader.startReading()) return null

    val ciContext = platform.CoreImage.CIContext(options = null)
    val totalDuration = timeline.totalDurationMs.toDouble().coerceAtLeast(1.0)
    var lastReported = -1
    var pendingAudio: platform.CoreMedia.CMSampleBufferRef? = null
    var nextAudio: platform.CoreMedia.CMSampleBufferRef? = audioBed?.nextSample()

    while (true) {
        val sample = videoOutput.copyNextSampleBuffer() ?: break
        val pixelBuffer = CMSampleBufferGetImageBuffer(sample) ?: continue
        val timeMs = (CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sample)) * 1000.0).toLong()
        val visible = timeline.visibleAt(timeMs) ?: continue

        var image = CIImage(cVPixelBuffer = pixelBuffer)
        image = IosColorGrade.apply(image, visible.first.clip)
        if (visible.second < 0.999f) {
            image = IosColorGrade.fade(image, visible.second)
        }

        pipOverlays.forEachIndexed { index, overlay ->
            if (!overlayVisible(overlay, timeMs)) return@forEachIndexed
            val pipSource = pipSources.getOrNull(index) ?: return@forEachIndexed
            val pipTrack = pipSource.track ?: return@forEachIndexed
            val pipFrame = pipSource.frameAt((timeMs - overlay.startMs) / 1000.0)
                ?: return@forEachIndexed
            val transform = pipTransform(
                preferred = pipTrack.preferredTransformValue(),
                naturalWidth = pipTrack.naturalWidth(),
                naturalHeight = pipTrack.naturalHeight(),
                rotationDegrees = overlay.rotation.toDouble(),
                scale = overlay.scale.toDouble(),
                canvasWidth = canvasWidth.toDouble(),
                canvasHeight = canvasHeight.toDouble(),
                centreX = overlay.position.x.toDouble() * canvasWidth,
                centreY = overlay.position.y.toDouble() * canvasHeight,
            )
            image = pipFrame.imageByApplyingTransform(transform.toCGAffineTransform())
                .imageByCompositingOverImage(image)
        }

        image = overlayRenderer.composite(image, timeMs)
        image = image.imageByCroppingToRect(canvasRect)
        ciContext.render(image, toCVPixelBuffer = pixelBuffer, bounds = canvasRect, colorSpace = null)

        while (!videoInput.isReadyForMoreMediaData()) delay(2)
        if (!videoInput.appendSampleBuffer(sample)) break

        // Keep the audio roughly in step with the video.
        val input = audioInput
        if (input != null && audioBed != null) {
            val frameSeconds = timeMs / 1000.0
            while (true) {
                val audioSample = pendingAudio ?: nextAudio ?: break
                if (!input.isReadyForMoreMediaData()) break
                val sampleTime = CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(audioSample))
                if (sampleTime > frameSeconds) {
                    pendingAudio = audioSample
                    break
                }
                if (!input.appendSampleBuffer(audioSample)) break
                pendingAudio = null
                nextAudio = audioBed.nextSample()
            }
        }

        val percent = ((timeMs * 100) / totalDuration).toInt().coerceIn(0, 99)
        if (percent != lastReported) {
            lastReported = percent
            withContext(Dispatchers.Main) { onProgress(percent.toFloat()) }
        }
    }

    // Whatever is left of the audio.
    val input = audioInput
    if (input != null && audioBed != null) {
        while (true) {
            val audioSample = pendingAudio ?: nextAudio ?: break
            if (!input.isReadyForMoreMediaData()) break
            if (!input.appendSampleBuffer(audioSample)) break
            pendingAudio = null
            nextAudio = audioBed.nextSample()
        }
    }

    pipSources.forEach { it.close() }
    audioBed?.close()
    reader.cancelReading()

    videoInput.markAsFinished()
    input?.markAsFinished()
    writer.endSessionAtSourceTime(CMTimeMake(timeline.totalDurationMs, TIMESCALE))
    writer.finishWriting()

    if (writer.status != AVAssetWriterStatusCompleted) return null
    withContext(Dispatchers.Main) { onProgress(100f) }
    return outputPath
}

/**
 * The video composition that puts every frame on the canvas.
 *
 * One instruction per part of the timeline: the clips themselves and the two
 * halves of each transition.
 */
private fun buildVideoComposition(
    timeline: RenderTimeline,
    clipSources: List<ClipSource>,
    canvasWidth: Int,
    canvasHeight: Int,
    frameRate: Int,
): AVMutableVideoComposition {
    val composition = AVMutableVideoComposition()
    composition.setRenderSize(CGSizeMake(canvasWidth.toDouble(), canvasHeight.toDouble()))
    composition.setFrameDuration(CMTimeMake(1, frameRate))

    val instructions = mutableListOf<AVMutableVideoCompositionInstruction>()

    fun instruction(
        startMs: Long,
        endMs: Long,
        trackIndex: Int,
    ): AVMutableVideoCompositionInstruction? {
        if (endMs <= startMs) return null
        val track = clipSources.getOrNull(trackIndex)?.track ?: return null
        val source = clipSources.getOrNull(trackIndex) ?: return null
        val result = AVMutableVideoCompositionInstruction()
        result.setTimeRange(
            CMTimeRangeMake(
                start = CMTimeMakeWithSeconds(startMs / 1000.0, TIMESCALE),
                duration = CMTimeMakeWithSeconds((endMs - startMs) / 1000.0, TIMESCALE),
            ),
        )
        val layer = AVMutableVideoCompositionLayerInstruction.videoCompositionLayerInstructionWithAssetTrack(track)
        val matrix = source.layerTransform
        layer.setTransform(
            transform = CGAffineTransformMake(matrix.a, matrix.b, matrix.c, matrix.d, matrix.tx, matrix.ty),
            atTime = CMTimeMake(0, 1),
        )
        result.setLayerInstructions(listOf(layer))
        return result
    }

    // The instructions have to be in chronological order and cover the whole
    // timeline without a gap.
    timeline.clips.forEachIndexed { index, segment ->
        val transition = timeline.transitions.firstOrNull { it.incomingIndex == index }
        if (transition == null) {
            instruction(segment.startMs, segment.normalEndMs, index)?.let { instructions += it }
            return@forEachIndexed
        }
        // The outgoing clip fades out, then the incoming one fades in, and
        // the incoming clip carries on with its normal part.
        instruction(transition.startMs, transition.boundaryMs, transition.outgoingIndex)
            ?.let { instructions += it }
        instruction(transition.boundaryMs, transition.endMs, transition.incomingIndex)
            ?.let { instructions += it }
        instruction(transition.endMs, segment.normalEndMs, index)?.let { instructions += it }
    }

    composition.setInstructions(instructions)
    return composition
}

/** True when [overlay]'s time window covers [timeMs]. */
private fun overlayVisible(overlay: Overlay, timeMs: Long): Boolean {
    if (timeMs < overlay.startMs) return false
    val end = overlay.endMs
    if (end != Long.MAX_VALUE && timeMs >= end) return false
    return true
}

private const val TIMESCALE = 600
private const val AUDIO_SAMPLE_RATE = 44_100.0
private const val AUDIO_BIT_RATE = 128_000
