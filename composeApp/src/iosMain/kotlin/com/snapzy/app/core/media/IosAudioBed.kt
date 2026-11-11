@file:OptIn(ExperimentalForeignApi::class)

package com.snapzy.app.core.media

import com.snapzy.app.core.editing.EditProject
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsBigEndianKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFoundation.AVAssetReader
import platform.AVFoundation.AVAssetReaderAudioMixOutput
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMutableAudioMix
import platform.AVFoundation.AVMutableAudioMixInputParameters
import platform.AVFoundation.AVMutableComposition
import platform.AVFoundation.AVMutableCompositionTrack
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addMutableTrackWithMediaType
import platform.AVFoundation.tracksWithMediaType
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.kCMPersistentTrackID_Invalid
import platform.Foundation.NSURL

/**
 * The audio that goes under the video.
 *
 * Two things are mixed:
 *
 * * the clips' own audio, trimmed and time-scaled to match the timeline and
 *   gained by the clip's `volume`;
 * * the music and sound effects, mixed by the shared `AudioBed` into a single
 *   WAV that is laid over the whole timeline.
 *
 * AVFoundation does the mixing: everything sits on one `AVMutableComposition`
 * and an `AVMutableAudioMix` applies each track's gain. The result is read
 * back as linear PCM so it can be written next to the video.
 */
internal class IosAudioBed(
    private val reader: AVAssetReader,
    private val output: AVAssetReaderAudioMixOutput,
) {
    /** The next PCM sample buffer, or null at the end of the bed. */
    fun nextSample(): CMSampleBufferRef? = output.copyNextSampleBuffer()

    fun close() {
        reader.cancelReading()
    }
}

/** The PCM format the mix is decoded to. */
internal val IosAudioPcmSettings: Map<Any?, Any?> = mapOf(
    AVFormatIDKey to kAudioFormatLinearPCM,
    AVSampleRateKey to 44_100.0,
    AVNumberOfChannelsKey to 2,
    AVLinearPCMBitDepthKey to 16,
    AVLinearPCMIsFloatKey to false,
    AVLinearPCMIsBigEndianKey to false,
)

/**
 * Builds the mixed audio bed for [project], or null when the project has no
 * audio at all.
 */
internal suspend fun buildIosAudioBed(
    project: EditProject,
    timeline: RenderTimeline,
): IosAudioBed? {
    val composition = AVMutableComposition()
    val mix = AVMutableAudioMix()
    val parameters = mutableListOf<AVMutableAudioMixInputParameters>()
    val totalSeconds = timeline.totalDurationMs / 1000.0
    if (totalSeconds <= 0.0) return null

    // The clips' own audio.
    timeline.clips.forEach { segment ->
        val clip = segment.clip
        if (clip.muted) return@forEach
        val sourceTrack = audioTrackOf(clip.path) ?: return@forEach
        val compTrack = composition.addMutableTrackWithMediaType(
            AVMediaTypeAudio,
            kCMPersistentTrackID_Invalid,
        ) ?: return@forEach

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
        if (!inserted) return@forEach

        // The clip's speed changes how long its audio lasts on the timeline.
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

        parameters += mixParameters(compTrack, clip.volume)
    }

    // The music and the sound effects, already mixed by the shared bed.
    val bedPath = writeAudioBed(project, timeline.totalDurationMs)
    if (bedPath != null) {
        val bedTrack = audioTrackOf(bedPath)
        if (bedTrack != null) {
            val compTrack = composition.addMutableTrackWithMediaType(
                AVMediaTypeAudio,
                kCMPersistentTrackID_Invalid,
            )
            if (compTrack != null) {
                compTrack.insertTimeRange(
                    timeRange = CMTimeRangeMake(
                        start = CMTimeMake(0, 1),
                        duration = CMTimeMakeWithSeconds(totalSeconds, TIMESCALE),
                    ),
                    ofTrack = bedTrack,
                    atTime = CMTimeMake(0, 1),
                    error = null,
                )
                parameters += mixParameters(compTrack, 1f)
            }
        }
    }

    if (parameters.isEmpty()) return null

    mix.setInputParameters(parameters)
    val audioTracks = composition.tracksWithMediaType(AVMediaTypeAudio)
    if (audioTracks.isEmpty()) return null

    val reader = AVAssetReader(composition, null)
    val output = AVAssetReaderAudioMixOutput(audioTracks, IosAudioPcmSettings)
    output.setAudioMix(mix)
    reader.addOutput(output)
    if (!reader.startReading()) return null

    return IosAudioBed(reader, output)
}

/**
 * Renders the shared music / sound-effect bed to a WAV file.
 *
 * The bed is exactly as long as the timeline, with the music looped and each
 * sound effect at its own start time, so it lines up with the video.
 */
private suspend fun writeAudioBed(project: EditProject, timelineMs: Long): String? {
    val bed = AudioBed.build(project.audioTracks, timelineMs, channels = 1) ?: return null
    if (bed.isEmpty()) return null
    val bytes = AudioSynth.encodeWav(bed)
    val path = appCachePath("snapzy_audio_bed.wav")
    return if (writeBytesToFile(path, bytes)) path else null
}

/** The mix parameters for one composition track. */
private fun mixParameters(
    track: AVMutableCompositionTrack,
    volume: Float,
): AVMutableAudioMixInputParameters {
    val parameters = AVMutableAudioMixInputParameters()
    parameters.setTrackID(track.trackID)
    parameters.setVolume(volume.coerceIn(0f, 2f), atTime = CMTimeMake(0, 1))
    return parameters
}

/** The first audio track of the file at [path]. */
private fun audioTrackOf(path: String): AVAssetTrack? {
    val asset = AVURLAsset(NSURL.fileURLWithPath(path), options = null)
    return asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack
}

private const val TIMESCALE = 600
