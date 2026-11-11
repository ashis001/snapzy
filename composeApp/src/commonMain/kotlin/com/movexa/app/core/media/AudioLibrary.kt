package com.movexa.app.core.media

import com.movexa.app.core.editing.AudioKind
import com.movexa.app.core.editing.AudioTrack
import com.movexa.app.core.editing.CatalogueItem

/**
 * Turns catalogue entries into real, playable audio on disk.
 *
 * The catalogue is a list of names; picking one synthesises the WAV once,
 * caches it, and hands the editor a track that points at the file. The export
 * pipeline then mixes that file into the render.
 */
object AudioLibrary {

    /**
     * Materialises [item] as a WAV file and returns a track pointing at it.
     *
     * The file is cached by catalogue id, so choosing the same track twice
     * costs nothing the second time.
     */
    suspend fun materialise(item: CatalogueItem, kind: AudioKind): AudioTrack? {
        val bytes = when (kind) {
            AudioKind.MUSIC -> AudioSynth.renderMusic(item.id)
            AudioKind.SOUND_EFFECT -> AudioSynth.renderSfx(item.id)
            // Voice-over is recorded, not synthesised.
            AudioKind.VOICEOVER -> return null
        }

        val path = appCachePath("audio_${item.id}.wav")
        if (!writeBytesToFile(path, bytes)) return null

        return AudioTrack(
            id = item.id.hashCode().toLong(),
            label = item.title,
            kind = kind,
            sourcePath = path,
            startMs = 0L,
            durationMs = if (kind == AudioKind.MUSIC) {
                AudioSynth.MUSIC_LOOP_SECONDS * 1000L
            } else {
                item.durationMs
            },
            volume = 1f,
            muted = false,
        )
    }

    /** True when the track points at a file that exists. */
    suspend fun hasFile(track: AudioTrack): Boolean {
        val path = track.sourcePath ?: return false
        return readBytesFromFile(path)?.isNotEmpty() == true
    }
}
