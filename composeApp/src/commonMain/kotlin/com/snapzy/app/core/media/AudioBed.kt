package com.snapzy.app.core.media

import com.snapzy.app.core.editing.AudioKind
import com.snapzy.app.core.editing.AudioTrack
import kotlin.math.abs
import kotlin.math.max

/**
 * Mixes the project's music and sound effects into a single audio bed.
 *
 * The export pipeline gets one extra audio source instead of one per track:
 * the bed is rendered at exactly the timeline length, with the music looped
 * and each sound effect placed at its own start time, so nothing drifts.
 */
object AudioBed {

    /**
     * Builds the bed for [tracks] over a timeline of [timelineMs].
     *
     * Returns `null` when there is nothing to play.
     */
    suspend fun build(tracks: List<AudioTrack>, timelineMs: Long, channels: Int = 2): FloatArray? {
        val usable = tracks.filter {
            !it.muted && it.sourcePath != null && it.kind != AudioKind.VOICEOVER
        }
        if (usable.isEmpty()) return null

        val total = ((timelineMs * AudioSynth.SAMPLE_RATE) / 1000L).toInt().coerceAtLeast(1)
        val bed = FloatArray(total)
        var wroteAnything = false

        for (track in usable) {
            val path = track.sourcePath ?: continue
            val bytes = readBytesFromFile(path) ?: continue
            val pcm = AudioSynth.decodeWavMono(bytes)
            if (pcm.isEmpty()) continue

            val gain = track.volume.coerceIn(0f, 2f)
            val startSample = ((track.startMs * AudioSynth.SAMPLE_RATE) / 1000L).toInt()

            if (track.kind == AudioKind.MUSIC) {
                // Music repeats for as long as the timeline runs.
                var position = startSample
                var index = 0
                while (position < total) {
                    bed[position] += pcm[index % pcm.size] * gain
                    position++
                    index++
                }
            } else {
                // A sound effect plays once, where the user put it.
                var position = startSample
                var index = 0
                while (position < total && index < pcm.size) {
                    bed[position] += pcm[index] * gain
                    position++
                    index++
                }
            }
            wroteAnything = true
        }

        if (!wroteAnything) return null

        // Media3 wants every audio source in a composition to agree on the
        // channel count, so the bed is widened to match the clips.
        if (channels > 1) {
            val wide = FloatArray(bed.size * channels)
            for (i in bed.indices) {
                for (channel in 0 until channels) {
                    wide[i * channels + channel] = bed[i]
                }
            }
            return normalise(wide)
        }

        return normalise(bed)
    }

    /** Leaves a little headroom so the mix never clips. */
    private fun normalise(samples: FloatArray): FloatArray {
        var peak = 0f
        samples.forEach { peak = max(peak, abs(it)) }
        if (peak > 0.95f) {
            val scale = 0.95f / peak
            for (i in samples.indices) samples[i] *= scale
        }
        return samples
    }
}
