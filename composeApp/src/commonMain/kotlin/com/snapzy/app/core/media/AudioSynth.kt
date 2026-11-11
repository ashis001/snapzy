package com.snapzy.app.core.media

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * A tiny software synthesiser.
 *
 * The music and sound-effect catalogues are real audio, not placeholders: each
 * entry is rendered to a 16-bit PCM WAV the first time it is used. Nothing is
 * bundled in the binary and no asset pipeline is needed — the synth is about
 * 300 lines of maths and produces a genuinely usable soundtrack.
 *
 * Everything is generated in `commonMain` so Android and iOS produce byte-identical audio.
 */
object AudioSynth {

    /** Sample rate of every generated track. */
    const val SAMPLE_RATE = 44_100

    /** Length of the generated music loop, in seconds. The export repeats it. */
    const val MUSIC_LOOP_SECONDS = 8

    private const val TWO_PI = 2.0 * PI

    /* ------------------------------------------------------------- public -- */

    /** Renders the music track with this catalogue id to a WAV file's bytes. */
    fun renderMusic(trackId: String): ByteArray {
        val style = MUSIC_STYLES[trackId] ?: MUSIC_STYLES.getValue("morning")
        val total = SAMPLE_RATE * MUSIC_LOOP_SECONDS
        val out = FloatArray(total)

        val beatSeconds = 60.0 / style.bpm
        val barSeconds = beatSeconds * 4
        val bars = (MUSIC_LOOP_SECONDS / barSeconds).toInt().coerceAtLeast(1)

        for (bar in 0 until bars) {
            val chord = style.chords[bar % style.chords.size]
            val barStart = (bar * barSeconds * SAMPLE_RATE).toInt()

            // Pad: the chord, held for the bar with a soft attack.
            if (style.pad) {
                val padLength = (barSeconds * SAMPLE_RATE).toInt()
                for (i in 0 until padLength) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val env = envelope(i.toDouble() / padLength, 0.18, 0.25)
                    var sample = 0.0
                    for ((index, semitone) in chord.withIndex()) {
                        val hz = style.rootHz * 2.0.pow(semitone / 12.0)
                        sample += sin(TWO_PI * hz * t) * (0.9 - index * 0.13)
                        sample += sin(TWO_PI * hz * 2.0 * t) * 0.16 * style.brightness
                    }
                    out[barStart + i] += (sample / chord.size * env * style.padLevel).toFloat()
                }
            }

            // Bass: root note on the down beats.
            if (style.bass) {
                for (beat in intArrayOf(0, 2)) {
                    val start = barStart + (beat * beatSeconds * SAMPLE_RATE).toInt()
                    val length = (beatSeconds * 0.9 * SAMPLE_RATE).toInt()
                    val hz = style.rootHz * 0.5
                    for (i in 0 until length) {
                        val t = i.toDouble() / SAMPLE_RATE
                        val env = envelope(i.toDouble() / length, 0.01, 0.4)
                        out[start + i] += (sin(TWO_PI * hz * t) * env * 0.34).toFloat()
                    }
                }
            }

            // Arp: a plucked eighth-note figure walking the chord.
            if (style.arp) {
                val step = beatSeconds / 2
                for (stepIndex in 0 until 8) {
                    val start = barStart + (stepIndex * step * SAMPLE_RATE).toInt()
                    val length = (step * SAMPLE_RATE * 1.6).toInt()
                    val semitone = chord[stepIndex % chord.size] + if (stepIndex % 4 >= 2) 12 else 0
                    val hz = style.rootHz * 2.0 * 2.0.pow(semitone / 12.0)
                    for (i in 0 until length) {
                        val t = i.toDouble() / SAMPLE_RATE
                        val env = exp(-t * 5.5)
                        val triangle = 2.0 * abs(2.0 * ((t * hz) % 1.0) - 1.0)
                        out[start + i] += (triangle * env * 0.13).toFloat()
                    }
                }
            }

            // Kick: a pitch-swept sine on the beat.
            if (style.kick) {
                for (beat in 0 until 4) {
                    val start = barStart + (beat * beatSeconds * SAMPLE_RATE).toInt()
                    val length = (0.22 * SAMPLE_RATE).toInt()
                    for (i in 0 until length) {
                        val t = i.toDouble() / SAMPLE_RATE
                        val hz = 46.0 + 120.0 * exp(-t * 26.0)
                        val env = exp(-t * 13.0)
                        out[start + i] += (sin(TWO_PI * hz * t) * env * 0.55).toFloat()
                    }
                }
            }

            // Hat: a short filtered-noise tick.
            if (style.hat) {
                for (stepIndex in 0 until 8) {
                    if (stepIndex % 2 == 0 && style.hatOnDownbeat) continue
                    val start = barStart + (stepIndex * beatSeconds / 2 * SAMPLE_RATE).toInt()
                    val length = (0.05 * SAMPLE_RATE).toInt()
                    var previous = 0.0
                    for (i in 0 until length) {
                        val t = i.toDouble() / SAMPLE_RATE
                        val env = exp(-t * 70.0)
                        val noise = noiseAt(stepIndex * 7919 + i)
                        val high = noise - previous * 0.85
                        previous = noise
                        out[start + i] += (high * env * 0.10).toFloat()
                    }
                }
            }
        }

        return toWav(normalise(out))
    }

    /** Renders the sound effect with this catalogue id to a WAV file's bytes. */
    fun renderSfx(trackId: String): ByteArray = when (trackId) {
        "whoosh" -> whoosh(0.9, 900.0, 6_000.0)
        "swoosh" -> whoosh(0.6, 1_400.0, 8_000.0)
        "pop" -> pop()
        "click" -> toWav(normalise(clickBurst(0.03, 0.5)))
        "camera" -> cameraShutter()
        "typewriter" -> typewriter()
        "glitch" -> glitch()
        "boom" -> boom()
        "bell" -> bell()
        "coin" -> coin()
        "sparkle" -> sparkle()
        "drum" -> drumHit()
        else -> toWav(normalise(clickBurst(0.12, 0.4)))
    }

    /* ------------------------------------------------------------- codec -- */

    /**
     * Wraps mono float samples in a 16-bit PCM WAV container.
     *
     * Public so the export pipeline can write a mixed audio bed in exactly the
     * same format the synth produces.
     */
    fun encodeWav(samples: FloatArray): ByteArray = toWav(samples)

    /**
     * Reads back a mono 16-bit PCM WAV produced by [encodeWav].
     *
     * Returns an empty array when the bytes are not a WAV this synth wrote.
     */
    fun decodeWavMono(bytes: ByteArray): FloatArray {
        if (bytes.size < 44) return FloatArray(0)
        fun asciiAt(offset: Int): String =
            (0 until 4).map { (bytes[offset + it].toInt() and 0xFF).toChar() }.joinToString("")

        if (asciiAt(0) != "RIFF" || asciiAt(8) != "WAVE") return FloatArray(0)

        // Find the `data` chunk rather than assuming a 44-byte header.
        var offset = 12
        var dataStart = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val id = asciiAt(offset)
            val size = (bytes[offset + 4].toInt() and 0xFF) or
                ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 6].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 7].toInt() and 0xFF) shl 24)
            if (id == "data") {
                dataStart = offset + 8
                dataSize = size.coerceAtMost(bytes.size - dataStart)
                break
            }
            offset += 8 + size + (size and 1)
        }
        if (dataStart < 0) return FloatArray(0)

        val count = dataSize / 2
        val out = FloatArray(count)
        for (i in 0 until count) {
            val low = bytes[dataStart + i * 2].toInt() and 0xFF
            val high = bytes[dataStart + i * 2 + 1].toInt()
            out[i] = ((high shl 8) or low).toShort() / 32768f
        }
        return out
    }

    /* -------------------------------------------------------------- sfx -- */

    private fun whoosh(seconds: Double, fromHz: Double, toHz: Double): ByteArray {
        val n = (SAMPLE_RATE * seconds).toInt()
        val out = FloatArray(n)
        var low = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val cutoff = fromHz + (toHz - fromHz) * t
            val raw = noiseAt(i * 31 + 7)
            low += (raw - low) * min(1.0, cutoff / SAMPLE_RATE * 6)
            val env = sin(PI * t) * sin(PI * t)
            out[i] = (low * env * 2.6).toFloat()
        }
        return toWav(normalise(out))
    }

    private fun pop(): ByteArray {
        val n = (SAMPLE_RATE * 0.4).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val hz = 880.0 - 620.0 * min(1.0, t / 0.09)
            val env = exp(-t * 16.0)
            val click = if (i < 120) noiseAt(i) * 0.5 else 0.0
            out[i] = (sin(TWO_PI * hz * t) * env * 0.6 + click).toFloat()
        }
        return toWav(normalise(out))
    }

    private fun clickBurst(seconds: Double, level: Double): FloatArray {
        val n = (SAMPLE_RATE * seconds).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-t * 180.0)
            out[i] = (noiseAt(i) * env * level).toFloat()
        }
        return out
    }

    private fun cameraShutter(): ByteArray {
        val out = FloatArray((SAMPLE_RATE * 0.7).toInt())
        val first = clickBurst(0.035, 0.7)
        val second = clickBurst(0.05, 0.5)
        mixInto(out, first, 0)
        mixInto(out, second, (SAMPLE_RATE * 0.11).toInt())
        return toWav(normalise(out))
    }

    private fun typewriter(): ByteArray {
        val out = FloatArray((SAMPLE_RATE * 2.4).toInt())
        var offset = 0
        while (offset < out.size - 2000) {
            val burst = clickBurst(0.022, 0.55)
            mixInto(out, burst, offset)
            offset += (SAMPLE_RATE * 0.18).toInt() + (noiseAt(offset) * 900).toInt()
        }
        return toWav(normalise(out))
    }

    private fun glitch(): ByteArray {
        val n = (SAMPLE_RATE * 0.8).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val step = if ((i / 1400) % 3 == 0) 0.055 else 0.011
            val square = if (((i * step).toInt()) % 2 == 0) 1.0 else -1.0
            val env = exp(-t * 3.2) * (0.55 + 0.45 * sin(TWO_PI * 11.0 * t))
            out[i] = (square * env * 0.4).toFloat()
        }
        return toWav(normalise(out))
    }

    private fun boom(): ByteArray {
        val n = (SAMPLE_RATE * 1.6).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val hz = 92.0 - 62.0 * min(1.0, t / 0.5)
            val env = exp(-t * 3.0)
            val body = sin(TWO_PI * hz * t) * env
            val grit = noiseAt(i) * exp(-t * 9.0) * 0.4
            out[i] = (body * 0.8 + grit).toFloat()
        }
        return toWav(normalise(out))
    }

    private fun bell(): ByteArray {
        val n = (SAMPLE_RATE * 1.2).toInt()
        val out = FloatArray(n)
        val partials = listOf(1.0 to 1.0, 2.76 to 0.45, 5.4 to 0.22, 8.9 to 0.1)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            var sample = 0.0
            for ((ratio, level) in partials) {
                sample += sin(TWO_PI * 880.0 * ratio * t) * level * exp(-t * (2.4 + ratio))
            }
            out[i] = (sample * 0.4).toFloat()
        }
        return toWav(normalise(out))
    }

    private fun coin(): ByteArray {
        val out = FloatArray((SAMPLE_RATE * 0.7).toInt())
        val first = tone(988.0, 0.06, 0.5)
        val second = tone(1319.0, 0.22, 0.5)
        mixInto(out, first, 0)
        mixInto(out, second, (SAMPLE_RATE * 0.055).toInt())
        return toWav(normalise(out))
    }

    private fun sparkle(): ByteArray {
        val n = (SAMPLE_RATE * 1.0).toInt()
        val out = FloatArray(n)
        val notes = listOf(1046.5, 1318.5, 1568.0, 2093.0)
        notes.forEachIndexed { index, hz ->
            val start = (index * SAMPLE_RATE * 0.12).toInt()
            val length = (SAMPLE_RATE * 0.5).toInt()
            for (i in 0 until length) {
                if (start + i >= out.size) break
                val t = i.toDouble() / SAMPLE_RATE
                val env = exp(-t * 7.0)
                out[start + i] += (sin(TWO_PI * hz * t) * env * 0.35).toFloat()
            }
        }
        return toWav(normalise(out))
    }

    private fun drumHit(): ByteArray {
        val n = (SAMPLE_RATE * 0.5).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val hz = 180.0 - 120.0 * min(1.0, t / 0.12)
            val env = exp(-t * 22.0)
            out[i] = (sin(TWO_PI * hz * t) * env * 0.8 + noiseAt(i) * exp(-t * 60.0) * 0.25).toFloat()
        }
        return toWav(normalise(out))
    }

    private fun tone(hz: Double, seconds: Double, level: Double): FloatArray {
        val n = (SAMPLE_RATE * seconds).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            out[i] = (sin(TWO_PI * hz * t) * exp(-t * 22.0) * level).toFloat()
        }
        return out
    }

    /* ------------------------------------------------------------ helpers -- */

    private fun envelope(position: Double, attack: Double, release: Double): Double {
        if (position < attack) return position / attack
        if (position > 1.0 - release) return max(0.0, (1.0 - position) / release)
        return 1.0
    }

    /** Deterministic noise so every build produces the same audio. */
    private fun noiseAt(index: Int): Double {
        var x = index * 1103515245 + 12345
        x = x xor (x shr 16)
        x *= 0x45d9f3b
        x = x xor (x shr 16)
        return ((x and 0x7fffffff) / 1073741823.5) - 1.0
    }

    private fun mixInto(target: FloatArray, source: FloatArray, offset: Int) {
        for (i in source.indices) {
            val index = offset + i
            if (index >= target.size) break
            target[index] += source[i]
        }
    }

    private fun normalise(samples: FloatArray): FloatArray {
        var peak = 0f
        samples.forEach { peak = max(peak, abs(it)) }
        if (peak < 0.0001f) return samples
        val gain = 0.89f / peak
        for (i in samples.indices) samples[i] *= gain
        return samples
    }

    /** Wraps 16-bit mono samples in a canonical 44-byte RIFF/WAVE header. */
    private fun toWav(samples: FloatArray): ByteArray {
        val dataSize = samples.size * 2
        val out = ByteArray(44 + dataSize)
        val byteRate = SAMPLE_RATE * 2

        fun writeAscii(offset: Int, text: String) {
            text.forEachIndexed { i, c -> out[offset + i] = c.code.toByte() }
        }

        fun writeLe32(offset: Int, value: Int) {
            out[offset] = (value and 0xFF).toByte()
            out[offset + 1] = ((value shr 8) and 0xFF).toByte()
            out[offset + 2] = ((value shr 16) and 0xFF).toByte()
            out[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }

        fun writeLe16(offset: Int, value: Int) {
            out[offset] = (value and 0xFF).toByte()
            out[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }

        writeAscii(0, "RIFF")
        writeLe32(4, 36 + dataSize)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeLe32(16, 16)
        writeLe16(20, 1) // PCM
        writeLe16(22, 1) // mono
        writeLe32(24, SAMPLE_RATE)
        writeLe32(28, byteRate)
        writeLe16(32, 2) // block align
        writeLe16(34, 16) // bits per sample
        writeAscii(36, "data")
        writeLe32(40, dataSize)

        for (i in samples.indices) {
            val value = (samples[i].coerceIn(-1f, 1f) * 32767).toInt()
            out[44 + i * 2] = (value and 0xFF).toByte()
            out[44 + i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return out
    }

    /* -------------------------------------------------------------- styles -- */

    private data class MusicStyle(
        val bpm: Int,
        val rootHz: Double,
        /** Chord voicings as semitone offsets from the root. */
        val chords: List<List<Int>>,
        val pad: Boolean = true,
        val padLevel: Double = 0.30,
        val bass: Boolean = true,
        val arp: Boolean = false,
        val kick: Boolean = false,
        val hat: Boolean = false,
        val hatOnDownbeat: Boolean = false,
        val brightness: Double = 1.0,
    )

    private val MUSIC_STYLES: Map<String, MusicStyle> = mapOf(
        // Lo-fi, soft and unhurried.
        "morning" to MusicStyle(
            bpm = 78, rootHz = 220.0,
            chords = listOf(listOf(0, 3, 7, 10), listOf(-2, 2, 5, 9), listOf(-4, 0, 3, 7), listOf(0, 4, 7, 11)),
            padLevel = 0.32, arp = true, kick = true, hat = true, brightness = 0.7,
        ),
        // Synthwave: driving bass, wide pad.
        "drive" to MusicStyle(
            bpm = 112, rootHz = 196.0,
            chords = listOf(listOf(0, 3, 7), listOf(0, 3, 10), listOf(-2, 2, 5), listOf(-4, 0, 3)),
            padLevel = 0.30, arp = true, kick = true, hat = true, brightness = 1.35,
        ),
        // Ambient: pad only, no rhythm at all.
        "focus" to MusicStyle(
            bpm = 60, rootHz = 174.6,
            chords = listOf(listOf(0, 7, 12, 16), listOf(-3, 4, 11, 14)),
            padLevel = 0.40, bass = false, brightness = 0.55,
        ),
        // Pop: bright, bouncy.
        "happy" to MusicStyle(
            bpm = 124, rootHz = 261.6,
            chords = listOf(listOf(0, 4, 7), listOf(5, 9, 12), listOf(-3, 2, 5), listOf(0, 4, 7)),
            padLevel = 0.24, arp = true, kick = true, hat = true, brightness = 1.2,
        ),
        // Epic: big fifths, slow build.
        "cinema" to MusicStyle(
            bpm = 72, rootHz = 130.8,
            chords = listOf(listOf(0, 7, 12), listOf(0, 7, 16), listOf(-5, 2, 7), listOf(0, 7, 12)),
            padLevel = 0.44, kick = true, brightness = 0.9,
        ),
        // Acoustic: light, warm.
        "chill" to MusicStyle(
            bpm = 92, rootHz = 246.9,
            chords = listOf(listOf(0, 4, 7, 11), listOf(-3, 2, 5, 9)),
            padLevel = 0.26, arp = true, brightness = 0.85,
        ),
        // Trap: half-time drums, dark.
        "beat" to MusicStyle(
            bpm = 140, rootHz = 146.8,
            chords = listOf(listOf(0, 3, 7), listOf(0, 3, 10)),
            padLevel = 0.22, bass = true, kick = true, hat = true, hatOnDownbeat = true, brightness = 0.6,
        ),
        // Lofi study: dusty chords.
        "lofi" to MusicStyle(
            bpm = 84, rootHz = 233.1,
            chords = listOf(listOf(0, 3, 7, 10), listOf(-2, 2, 5, 9)),
            padLevel = 0.30, arp = true, kick = true, brightness = 0.65,
        ),
        // Rock: power chords and a driving beat.
        "rock" to MusicStyle(
            bpm = 132, rootHz = 196.0,
            chords = listOf(listOf(0, 7, 12), listOf(-2, 5, 10), listOf(0, 7, 12), listOf(-4, 3, 8)),
            padLevel = 0.26, bass = true, kick = true, hat = true, brightness = 1.25,
        ),
        // Soft piano: sparse and delicate.
        "piano" to MusicStyle(
            bpm = 68, rootHz = 261.6,
            chords = listOf(listOf(0, 4, 7, 11), listOf(3, 7, 10, 14)),
            padLevel = 0.32, arp = true, brightness = 0.75,
        ),
        // Tropical: bright major, light rhythm.
        "summer" to MusicStyle(
            bpm = 104, rootHz = 293.7,
            chords = listOf(listOf(0, 4, 7), listOf(0, 5, 9), listOf(-2, 2, 7), listOf(0, 4, 9)),
            padLevel = 0.24, arp = true, kick = true, hat = true, brightness = 1.15,
        ),
        // EDM: four-on-the-floor.
        "gym" to MusicStyle(
            bpm = 128, rootHz = 220.0,
            chords = listOf(listOf(0, 3, 7), listOf(-2, 2, 5), listOf(0, 3, 10), listOf(-5, 0, 4)),
            padLevel = 0.24, arp = true, kick = true, hat = true, brightness = 1.3,
        ),
    )
}
