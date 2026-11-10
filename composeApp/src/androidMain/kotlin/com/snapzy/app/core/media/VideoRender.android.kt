@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.snapzy.app.core.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextOverlay
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
import com.snapzy.app.core.editing.AdjustSpec
import com.snapzy.app.core.editing.EditProject
import com.snapzy.app.core.editing.FilterPreset
import com.snapzy.app.core.editing.Overlay
import com.snapzy.app.core.editing.VideoClip
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * The real render pipeline.
 *
 * Every edit the model expresses that Media3 supports natively is applied here:
 * per-clip trimming, speed (video + audio), mute, gain, rotation, flip and
 * colour grading, plus composition-level output resolution and text / sticker
 * overlays.
 */
actual suspend fun renderVideoProject(
    project: EditProject,
    settings: ExportSettings,
    onProgress: (Float) -> Unit,
): String? {
    val context = AndroidAppContext.context
    if (project.clips.isEmpty()) return null

    val outputFile = File(context.cacheDir, "snapzy_render_${System.currentTimeMillis()}.mp4")

    val editedItems = project.clips.map { clip -> buildEditedMediaItem(clip) }
    val sequence = EditedMediaItemSequence.Builder(editedItems).build()

    val composition = Composition.Builder(sequence)
        .setEffects(buildCompositionEffects(project, settings))
        .build()

    val encoderFactory = DefaultEncoderFactory.Builder(context)
        .setRequestedVideoEncoderSettings(
            VideoEncoderSettings.Builder()
                .setBitrate(settings.videoBitrate)
                .build()
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
                onProgress(progressHolder.progress.toFloat().coerceIn(0f, 100f))
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

/** Builds one `EditedMediaItem` with every per-clip effect applied. */
private fun buildEditedMediaItem(clip: VideoClip): EditedMediaItem {
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
        val speed = clip.speed.coerceIn(0.1f, 10f)
        videoEffects += SpeedChangeEffect(speed)
        audioProcessors += SonicAudioProcessor().apply { setSpeed(speed) }
    }

    if (clip.volume != 1f) {
        audioProcessors += GainAudioProcessor(clip.volume.coerceIn(0f, 2f))
    }

    return EditedMediaItem.Builder(mediaItem)
        .setRemoveAudio(clip.muted)
        .setEffects(Effects(audioProcessors, videoEffects))
        .build()
}

/** Composition-level effects: output resolution + overlays drawn over everything. */
private fun buildCompositionEffects(project: EditProject, settings: ExportSettings): Effects {
    val videoEffects = mutableListOf<Effect>()

    // The output shape follows the project's canvas.
    //
    // Media3's `createForAspectRatio` fits the ratio inside the source and
    // never upscales, so the output size is computed here instead: the short
    // side of the chosen resolution preset is kept, and the long side follows
    // the canvas ratio. A 9:16 canvas at 1080p therefore renders 1080x1920.
    val targetRatio = project.canvas.aspect.ratio
    val shortSide = minOf(settings.width, settings.height)
    val presentation = if (targetRatio != null) {
        val (outWidth, outHeight) = if (targetRatio < 1f) {
            shortSide to (shortSide / targetRatio).toInt()
        } else {
            (shortSide * targetRatio).toInt() to shortSide
        }
        Presentation.createForWidthAndHeight(
            outWidth,
            outHeight,
            Presentation.LAYOUT_SCALE_TO_FIT,
        )
    } else {
        // "Original" canvas: keep the source's own aspect, scaled to the
        // requested height.
        Presentation.createForHeight(settings.height)
    }
    videoEffects += presentation

    val overlays = buildOverlayList(project)
    if (overlays.isNotEmpty()) {
        videoEffects += OverlayEffect(overlays)
    }

    return Effects(emptyList(), videoEffects)
}

/** Converts our text/sticker/doodle layers into Media3 texture overlays. */
private fun buildOverlayList(project: EditProject): List<TextureOverlay> {
    val result = mutableListOf<TextureOverlay>()

    project.overlays.forEach { overlay ->
        when (overlay) {
            is Overlay.Text -> {
                val spannable = SpannableString(overlay.text)
                if (overlay.bold && spannable.isNotEmpty()) {
                    spannable.setSpan(
                        StyleSpan(Typeface.BOLD),
                        0,
                        spannable.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                result += TextOverlay.createStaticTextOverlay(spannable, overlaySettings(overlay))
            }

            is Overlay.Sticker -> {
                val bitmap = renderEmojiBitmap(overlay.emoji, overlay.color)
                result += BitmapOverlay.createStaticBitmapOverlay(bitmap, overlaySettings(overlay))
            }

            is Overlay.Drawing -> {
                if (overlay.points.size < 2) return@forEach
                val bitmap = renderStrokeBitmap(overlay)
                result += BitmapOverlay.createStaticBitmapOverlay(bitmap, overlaySettings(overlay))
            }

            is Overlay.Pip -> Unit // PIP needs a second decoder; not part of the v1 export.
        }
    }

    return result
}

/** Maps our normalised 0..1 overlay position onto Media3's -1..1 frame anchors. */
private fun overlaySettings(overlay: Overlay): OverlaySettings = OverlaySettings.Builder()
    .setAlphaScale(overlay.opacity.coerceIn(0f, 1f))
    .setBackgroundFrameAnchor(
        (overlay.position.x * 2f - 1f).coerceIn(-1f, 1f),
        (overlay.position.y * 2f - 1f).coerceIn(-1f, 1f),
    )
    .setOverlayFrameAnchor(0.5f, 0.5f)
    .setScale(overlay.scale.coerceIn(0.05f, 4f), overlay.scale.coerceIn(0.05f, 4f))
    .setRotationDegrees(overlay.rotation)
    .build()

/** Renders an emoji sticker into a bitmap the overlay pipeline can texture. */
private fun renderEmojiBitmap(emoji: String, tint: androidx.compose.ui.graphics.Color): Bitmap {
    val size = 256
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(
            (tint.alpha * 255).toInt(),
            (tint.red * 255).toInt(),
            (tint.green * 255).toInt(),
            (tint.blue * 255).toInt(),
        )
        textSize = 180f
        textAlign = Paint.Align.CENTER
    }
    val metrics = paint.fontMetrics
    val baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f
    canvas.drawText(emoji, size / 2f, baseline, paint)
    return bitmap
}

/** Renders a freehand stroke into a bitmap the overlay pipeline can texture. */
private fun renderStrokeBitmap(overlay: Overlay.Drawing): Bitmap {
    val size = 512
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(
            (overlay.opacity.coerceIn(0f, 1f) * 255).toInt(),
            (overlay.color.red * 255).toInt(),
            (overlay.color.green * 255).toInt(),
            (overlay.color.blue * 255).toInt(),
        )
        style = Paint.Style.STROKE
        strokeWidth = overlay.strokeWidth * 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val path = android.graphics.Path()
    overlay.points.forEachIndexed { index, point ->
        val x = point.x.coerceIn(0f, 1f) * size
        val y = point.y.coerceIn(0f, 1f) * size
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    canvas.drawPath(path, paint)
    return bitmap
}

/**
 * Maps our filter + adjust model onto Media3's colour effects.
 *
 * Deliberately LUT-free: the same maths powers the preview (via
 * `buildLookMatrix`), so what you see is what you get.
 */
private fun colorEffects(filter: FilterPreset, adjust: AdjustSpec): List<Effect> {
    val effects = mutableListOf<Effect>()

    val brightness = adjust.brightness + com.snapzy.app.core.editing.filterBrightness(filter)
    if (brightness != 0f) effects += Brightness(brightness.coerceIn(-1f, 1f))

    val contrast = (adjust.contrast * com.snapzy.app.core.editing.filterContrast(filter)).coerceIn(0f, 2f)
    if (contrast != 1f) effects += Contrast(contrast)

    val saturation = (adjust.saturation * com.snapzy.app.core.editing.filterSaturation(filter)).coerceIn(0f, 3f)
    val hue = adjust.hue + com.snapzy.app.core.editing.filterHue(filter)
    if (saturation != 1f || hue != 0f) {
        effects += HslAdjustment.Builder()
            .adjustSaturation(saturation)
            .adjustHue(hue.coerceIn(-180f, 180f))
            .build()
    }

    val tint = com.snapzy.app.core.editing.filterTint(filter)
    if (tint != null) {
        effects += RgbAdjustment.Builder()
            .setRedScale(tint[0])
            .setGreenScale(tint[1])
            .setBlueScale(tint[2])
            .build()
    }

    if (adjust.temperature != 0f) {
        val t = adjust.temperature
        effects += RgbAdjustment.Builder()
            .setRedScale(1f + t * 0.15f)
            .setBlueScale(1f - t * 0.15f)
            .build()
    }

    return effects
}

/**
 * A minimal 16-bit PCM gain stage.
 *
 * Media3 has no built-in gain processor, and `ChannelMixingAudioProcessor`
 * needs the exact source channel count up front — this avoids both problems.
 */
private class GainAudioProcessor(private val gain: Float) : BaseAudioProcessor() {

    private var outputBuffer: java.nio.ByteBuffer? = null

    override fun onConfigure(
        inputFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        if (inputFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputFormat)
        }
        return inputFormat
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
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

    override fun getOutput(): java.nio.ByteBuffer = outputBuffer ?: java.nio.ByteBuffer.allocateDirect(0)

    override fun isActive(): Boolean = gain != 1f

    override fun onFlush() {
        outputBuffer?.clear()
        outputBuffer = null
    }

    override fun onReset() {
        onFlush()
    }
}
