@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.snapzy.app.core.media

import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.audio.ToInt16PcmAudioProcessor
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.snapzy.app.core.editing.EditProject
import com.snapzy.app.core.editing.Overlay
import com.snapzy.app.core.editing.TransitionKind
import com.snapzy.app.core.editing.VideoClip
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.math.min
import kotlin.math.roundToInt

/** How long a fade-to-black transition lasts, in milliseconds. */
private const val TRANSITION_MS = 400L

/**
 * The real render pipeline.
 *
 * Every edit the model expresses that Media3 supports natively is applied here:
 * per-clip trimming, speed (video + audio), mute, gain, rotation, flip and
 * colour grading; text, sticker and drawing overlays that honour their time
 * windows; a fade transition between clips; a mixed music and sound-effect
 * bed; and a picture-in-picture layer drawn over the finished frame.
 */
actual suspend fun renderVideoProject(
    project: EditProject,
    settings: ExportSettings,
    onProgress: (Float) -> Unit,
): String? {
    val context = AndroidAppContext.context
    if (project.clips.isEmpty()) return null

    val canvasSize = resolveCanvasSize(project, settings)
    val timelineMs = project.clips.sumOf { it.timelineDurationMs }.coerceAtLeast(1L)

    // The music and the sound effects are mixed into one bed so the export has
    // a single extra audio source to blend with the clip audio.
    val audioChannels = resolveAudioChannels(project)
    val bedFile = buildAudioBedFile(project, timelineMs, audioChannels)

    // A muted clip drops its audio track entirely, which leaves the sequence
    // with mixed audio presence — Media3 needs to be told to keep a track.
    val forceAudioTrack = project.clips.any { it.muted } && project.clips.any { !it.muted }

    // A picture-in-picture layer is drawn from frames pulled out of its clip,
    // because Media3 1.5 cannot composite a second video into an export.
    val pipLayers = buildPipLayers(project, canvasSize)

    val outputFile = File(context.cacheDir, "snapzy_render_${System.currentTimeMillis()}.mp4")

    return renderComposition(
        sequences = buildSequences(project, bedFile, audioChannels, canvasSize, pipLayers),
        effects = buildCompositionEffects(canvasSize),
        forceAudioTrack = forceAudioTrack,
        outputFile = outputFile,
        progressFrom = 0f,
        progressTo = 100f,
        onProgress = onProgress,
    )
}

/* ------------------------------------------------------------- sequences -- */

/** Builds the media sequences: the clips, plus the mixed audio bed. */
private fun buildSequences(
    project: EditProject,
    bedFile: File?,
    targetChannels: Int,
    canvasSize: Pair<Int, Int>,
    pipLayers: List<TextureOverlay>,
): List<EditedMediaItemSequence> {
    val sequences = mutableListOf<EditedMediaItemSequence>()

    val overlayLayers = project.overlays.filter { it !is Overlay.Pip }
    val clipItems = project.clips.mapIndexed { index, clip ->
        buildClipItem(
            clip = clip,
            targetChannels = targetChannels,
            overlays = overlayLayers,
            canvasSize = canvasSize,
            pipLayers = pipLayers,
            clipIndex = index,
            clips = project.clips,
            transition = project.transition,
            clipStartMs = clipStartMs(project.clips, index),
        )
    }
    sequences += EditedMediaItemSequence.Builder(clipItems).build()

    bedFile?.let { file ->
        val mediaItem = MediaItem.Builder().setUri(Uri.fromFile(file)).build()
        val item = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(listOf(ToInt16PcmAudioProcessor()), emptyList()))
            .build()
        sequences += EditedMediaItemSequence.Builder(item).build()
    }

    return sequences
}

/** Where a clip starts on the timeline, in milliseconds. */
private fun clipStartMs(clips: List<VideoClip>, index: Int): Long {
    var elapsed = 0L
    for (position in 0 until index) {
        elapsed += clips[position].timelineDurationMs
    }
    return elapsed
}

/**
 * Builds one `EditedMediaItem` with every per-clip effect applied.
 *
 * The overlays and the transition are attached to the clip they belong to
 * rather than to the composition: Media3 applies an item's own effects on the
 * composition's timeline, so a time window means exactly what it says.
 */
private fun buildClipItem(
    clip: VideoClip,
    targetChannels: Int,
    overlays: List<Overlay>,
    canvasSize: Pair<Int, Int>,
    pipLayers: List<TextureOverlay>,
    clipIndex: Int,
    clips: List<VideoClip>,
    transition: TransitionKind,
    clipStartMs: Long,
): EditedMediaItem {
    val clipping = MediaItem.ClippingConfiguration.Builder()
        .setStartPositionMs(clip.trimStartMs.coerceAtLeast(0L))
        .setEndPositionMs(clip.trimEndMs.coerceAtLeast(clip.trimStartMs + 1L))
        .build()

    val mediaItem = MediaItem.Builder()
        .setUri(Uri.fromFile(File(clip.path)))
        .setClippingConfiguration(clipping)
        .build()

    val videoEffects = mutableListOf<Effect>()
    val audioProcessors = mutableListOf<AudioProcessor>()

    if (clip.rotation != 0 || clip.flipped) {
        videoEffects += ScaleAndRotateTransformation.Builder()
            .setScale(if (clip.flipped) -1f else 1f, 1f)
            .setRotationDegrees(clip.rotation.toFloat())
            .build()
    }

    videoEffects += colorEffects(clip.filter, clip.adjust)

    if (clip.speed != 1f) {
        videoEffects += SpeedChangeEffect(clip.speed.coerceIn(0.1f, 10f))
    }

    videoEffects += buildOverlayEffects(overlays, canvasSize, pipLayers)
    videoEffects += buildTransitionEffects(
        transition = transition,
        clipIndex = clipIndex,
        clipCount = clips.size,
        clipStartMs = clipStartMs,
        clipDurationMs = clip.timelineDurationMs,
        canvasSize = canvasSize,
    )

    if (!clip.muted) {
        audioProcessors += ToInt16PcmAudioProcessor()
        val channels = probeAudioChannels(clip.path)
        if (channels != null && channels != targetChannels) {
            audioProcessors += ChannelMixingAudioProcessor().apply {
                putChannelMixingMatrix(downMixMatrix(channels, targetChannels))
            }
        }
        if (clip.speed != 1f) {
            audioProcessors += SonicAudioProcessor().apply { setSpeed(clip.speed.coerceIn(0.1f, 10f)) }
        }
        if (clip.volume != 1f) {
            audioProcessors += GainAudioProcessor(clip.volume.coerceIn(0f, 2f))
        }
    }

    return EditedMediaItem.Builder(mediaItem)
        .setRemoveAudio(clip.muted)
        .setEffects(Effects(audioProcessors, videoEffects))
        .build()
}

/* -------------------------------------------------------------- effects -- */

/** Composition-level effects: the output resolution. */
private fun buildCompositionEffects(canvasSize: Pair<Int, Int>): Effects {
    val videoEffects = mutableListOf<Effect>()

    // The output shape follows the project's canvas.
    videoEffects += Presentation.createForWidthAndHeight(
        canvasSize.first,
        canvasSize.second,
        Presentation.LAYOUT_SCALE_TO_FIT,
    )

    return Effects(emptyList(), videoEffects)
}

/**
 * The overlays that are on screen during this clip.
 *
 * A global time window is translated into the composition's timeline, so an
 * overlay that starts four seconds into the video appears four seconds into
 * whichever clip is playing then.
 */
private fun buildOverlayEffects(
    overlays: List<Overlay>,
    canvasSize: Pair<Int, Int>,
    pipLayers: List<TextureOverlay>,
): List<Effect> {
    if (overlays.isEmpty() && pipLayers.isEmpty()) return emptyList()

    val layers = mutableListOf<TextureOverlay>()
    layers += pipLayers

    for (overlay in overlays) {
        val globalStart = overlay.startMs.coerceAtLeast(0L)
        val globalEnd = if (overlay.endMs == Long.MAX_VALUE) Long.MAX_VALUE else overlay.endMs

        val bitmap: Bitmap = when (overlay) {
            is Overlay.Text -> renderTextBitmap(overlay, canvasSize.first, canvasSize.second)
            is Overlay.Sticker -> renderEmojiBitmap(
                overlay.emoji,
                overlay.color,
                canvasSize.first,
                canvasSize.second,
            )
            is Overlay.Drawing -> {
                if (overlay.points.size < 2) continue
                renderStrokeBitmap(overlay, canvasSize.first, canvasSize.second)
            }
            is Overlay.Pip -> continue
        }

        layers += WindowedBitmapOverlay(
            bitmap = bitmap,
            base = overlaySettings(overlay),
            startUs = globalStart * 1000L,
            endUs = if (globalEnd == Long.MAX_VALUE) Long.MAX_VALUE else globalEnd * 1000L,
        )
    }

    if (layers.isEmpty()) return emptyList()
    return listOf(OverlayEffect(layers))
}

/**
 * The dip to black that plays between two clips.
 *
 * The outgoing clip fades down over its last moment and the incoming clip
 * fades up over its first, so the frame dips to black at the boundary — the
 * classic fade transition.
 */
private fun buildTransitionEffects(
    transition: TransitionKind,
    clipIndex: Int,
    clipCount: Int,
    clipStartMs: Long,
    clipDurationMs: Long,
    canvasSize: Pair<Int, Int>,
): List<Effect> {
    if (transition == TransitionKind.NONE || clipCount < 2) return emptyList()

    val half = TRANSITION_MS / 2
    if (half <= 0L) return emptyList()

    val layers = mutableListOf<TextureOverlay>()
    val frame = blackFrame(canvasSize.first, canvasSize.second)

    val clipEndMs = clipStartMs + clipDurationMs

    if (clipIndex < clipCount - 1) {
        // Fading out towards the next clip.
        layers += FadingBitmapOverlay(
            bitmap = frame,
            startUs = (clipEndMs - half).coerceAtLeast(0L) * 1000L,
            endUs = clipEndMs * 1000L,
            fadeIn = true,
        )
    }
    if (clipIndex > 0) {
        // Fading in from the previous clip.
        layers += FadingBitmapOverlay(
            bitmap = frame,
            startUs = clipStartMs * 1000L,
            endUs = (clipStartMs + half) * 1000L,
            fadeIn = false,
        )
    }

    if (layers.isEmpty()) return emptyList()
    return listOf(OverlayEffect(layers))
}

/** Maps our normalised 0..1 overlay position onto Media3's -1..1 frame anchors. */
private fun overlaySettings(overlay: Overlay): OverlaySettings = OverlaySettings.Builder()
    .setAlphaScale(overlay.opacity.coerceIn(0f, 1f))
    .setBackgroundFrameAnchor(
        (overlay.position.x * 2f - 1f).coerceIn(-1f, 1f),
        (1f - overlay.position.y * 2f).coerceIn(-1f, 1f),
    )
    .setOverlayFrameAnchor(0f, 0f)
    .setScale(overlay.scale.coerceIn(0.05f, 4f), overlay.scale.coerceIn(0.05f, 4f))
    .setRotationDegrees(overlay.rotation)
    .build()

/* ------------------------------------------------------------------ pip -- */

/**
 * The picture-in-picture layers, ready to be drawn over the frame.
 *
 * The frames of the clip are pulled out ahead of the render, scaled to the
 * size the layer occupies on the canvas.
 */
private fun buildPipLayers(
    project: EditProject,
    canvasSize: Pair<Int, Int>,
): List<TextureOverlay> {
    val layers = mutableListOf<TextureOverlay>()
    project.overlays.filterIsInstance<Overlay.Pip>().forEach { pip ->
        val (frames, interval) = extractPipFrames(
            path = pip.path,
            sourceStartMs = pip.trimStartMs,
            sourceEndMs = pip.trimEndMs,
            scale = pip.scale.coerceIn(0.05f, 4f),
        )
        if (frames.isEmpty()) return@forEach
        layers += PipFrameOverlay(
            frames = frames,
            frameIntervalMs = interval,
            startMs = pip.startMs,
            endMs = if (pip.endMs == Long.MAX_VALUE) null else pip.endMs,
            base = pipOverlaySettings(pip),
        )
    }
    return layers
}

/* --------------------------------------------------------------- render -- */

/** Runs the Transformer over [composition] and writes it to [outputFile]. */
private suspend fun renderComposition(
    sequences: List<EditedMediaItemSequence>,
    effects: Effects,
    forceAudioTrack: Boolean,
    outputFile: File,
    progressFrom: Float,
    progressTo: Float,
    onProgress: (Float) -> Unit,
): String? {
    val builder = Composition.Builder(sequences).setEffects(effects)
    if (forceAudioTrack) builder.experimentalSetForceAudioTrack(true)
    return startTransformer(builder.build(), outputFile, progressFrom, progressTo, onProgress)
}

private suspend fun startTransformer(
    composition: Composition,
    outputFile: File,
    progressFrom: Float,
    progressTo: Float,
    onProgress: (Float) -> Unit,
): String? {
    val context = AndroidAppContext.context
    val encoderFactory = DefaultEncoderFactory.Builder(context)
        .setRequestedVideoEncoderSettings(
            VideoEncoderSettings.Builder()
                .setBitrate(10_000_000)
                .build(),
        )
        .build()

    return suspendCancellableCoroutine { continuation ->
        val progressHolder = ProgressHolder()
        val handler = Handler(Looper.getMainLooper())
        lateinit var transformer: Transformer

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                transformer.removeListener(this)
                if (continuation.isActive) continuation.resume(outputFile.absolutePath)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                transformer.removeListener(this)
                if (continuation.isActive) continuation.resume(null)
            }
        }

        transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .addListener(listener)
            .build()

        // Progress is polled on the main looper — Transformer requires main-thread calls.
        val progressRunnable = object : Runnable {
            override fun run() {
                if (!continuation.isActive) return
                runCatching { transformer.getProgress(progressHolder) }
                val raw = progressHolder.progress.toFloat().coerceIn(0f, 100f)
                onProgress(progressFrom + (progressTo - progressFrom) * raw / 100f)
                handler.postDelayed(this, 250)
            }
        }
        handler.post(progressRunnable)

        continuation.invokeOnCancellation {
            runCatching { transformer.cancel() }
            handler.removeCallbacks(progressRunnable)
        }

        runCatching { transformer.start(composition, outputFile.absolutePath) }
            .onFailure {
                handler.removeCallbacks(progressRunnable)
                if (continuation.isActive) continuation.resume(null)
            }
    }
}

/* ---------------------------------------------------------------- audio -- */

/** Renders the project's music and sound effects into a single WAV file. */
private suspend fun buildAudioBedFile(
    project: EditProject,
    timelineMs: Long,
    channels: Int,
): File? {
    val bed = AudioBed.build(project.audioTracks, timelineMs, channels) ?: return null
    val bytes = AudioSynth.encodeWav(bed)
    val path = appCachePath("snapzy_bed_${System.currentTimeMillis()}.wav")
    return if (writeBytesToFile(path, bytes)) File(path) else null
}

/**
 * The channel count every audio source in the export has to agree on.
 *
 * The first clip that carries audio decides it, and everything else is mixed
 * to match.
 */
private fun resolveAudioChannels(project: EditProject): Int =
    project.clips.firstOrNull { !it.muted }
        ?.let { probeAudioChannels(it.path) }
        ?.coerceAtLeast(1)
        ?: 2

/**
 * Reads the number of audio channels in a file, without decoding it.
 *
 * Media3 needs every audio source in a composition to have the same channel
 * count, so this is how the mixed bed is built to match the clips.
 */
private fun probeAudioChannels(path: String): Int? = runCatching {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(path)
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (!mime.startsWith("audio/")) continue
            return@runCatching format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        }
        null
    } finally {
        extractor.release()
    }
}.getOrNull()

/**
 * A matrix that folds [inputChannels] into [outputChannels].
 *
 * Every input channel is copied into the first output; a stereo source keeps
 * its left and right apart, a mono source is sent to both.
 */
private fun downMixMatrix(inputChannels: Int, outputChannels: Int): ChannelMixingMatrix {
    val coefficients = FloatArray(inputChannels * outputChannels)
    for (input in 0 until inputChannels) {
        for (output in 0 until outputChannels) {
            val keepSeparate = inputChannels >= 2 && outputChannels >= 2 && input == output
            val duplicate = inputChannels == 1
            coefficients[input * outputChannels + output] =
                if (keepSeparate || duplicate) 1f else 0f
        }
    }
    return ChannelMixingMatrix(inputChannels, outputChannels, coefficients)
}

/* ---------------------------------------------------------------- misc -- */

/**
 * The size of the finished frame.
 *
 * The short side follows the requested resolution and the long side follows
 * the canvas ratio, so a 9:16 canvas at 1080x1920 renders 1080x1920.
 */
private fun resolveCanvasSize(project: EditProject, settings: ExportSettings): Pair<Int, Int> {
    val ratio = project.canvas.aspect.ratio
    val shortSide = min(settings.width, settings.height)
    if (ratio != null) {
        return if (ratio < 1f) {
            shortSide to (shortSide / ratio).roundToInt()
        } else {
            (shortSide * ratio).roundToInt() to shortSide
        }
    }

    // "Original": keep the source's own aspect, scaled to the requested height.
    val source = project.clips.firstOrNull()?.let { probeVideoSize(it.path) }
    return if (source != null && source.second > 0) {
        val width = (settings.height.toFloat() * source.first / source.second).roundToInt()
        width.coerceAtLeast(2) to settings.height
    } else {
        settings.width to settings.height
    }
}

/** Reads the pixel size of a video file without decoding it. */
private fun probeVideoSize(path: String): Pair<Int, Int>? = runCatching {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(path)
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (!mime.startsWith("video/")) continue
            if (format.containsKey(MediaFormat.KEY_WIDTH) && format.containsKey(MediaFormat.KEY_HEIGHT)) {
                return@runCatching format.getInteger(MediaFormat.KEY_WIDTH) to
                    format.getInteger(MediaFormat.KEY_HEIGHT)
            }
        }
        null
    } finally {
        extractor.release()
    }
}.getOrNull()

/**
 * A minimal 16-bit PCM gain stage.
 *
 * Media3 has no built-in gain processor, and `ChannelMixingAudioProcessor`
 * needs the exact source channel count up front — this avoids both problems.
 */
private class GainAudioProcessor(private val gain: Float) : BaseAudioProcessor() {

    private var outputBuffer: ByteBuffer? = null

    override fun onConfigure(
        inputFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        if (inputFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputFormat)
        }
        return inputFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val out = replaceOutputBuffer(inputBuffer.remaining())
        val shorts = inputBuffer.asShortBuffer()
        val samples = ShortArray(shorts.remaining())
        shorts.get(samples)
        for (i in samples.indices) {
            val scaled = (samples[i] * gain).toInt()
            samples[i] = scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        out.asShortBuffer().put(samples)
        outputBuffer = out
    }

    override fun getOutput(): ByteBuffer = outputBuffer ?: ByteBuffer.allocateDirect(0)

    override fun isActive(): Boolean = gain != 1f

    override fun onFlush() {
        outputBuffer?.clear()
        outputBuffer = null
    }

    override fun onReset() {
        onFlush()
    }
}
