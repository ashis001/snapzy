@file:OptIn(ExperimentalForeignApi::class)

package com.movexa.app.core.media

import com.movexa.app.core.editing.AdjustSpec
import com.movexa.app.core.editing.FilterPreset
import com.movexa.app.core.editing.VideoClip
import com.movexa.app.core.editing.filterBrightness
import com.movexa.app.core.editing.filterContrast
import com.movexa.app.core.editing.filterHue
import com.movexa.app.core.editing.filterSaturation
import com.movexa.app.core.editing.filterTint
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreImage.CIImage
import platform.CoreImage.CIVector
import kotlin.math.PI

/**
 * The colour grade, expressed as Core Image filters.
 *
 * The maths is the one in `LookMatrix.kt` — the same one the preview and the
 * Android renderer use — so the exported file matches what the editor shows:
 *
 * 1. saturation and hue (from the preset and the sliders),
 * 2. the preset's RGB tint and the temperature shift (one colour matrix),
 * 3. contrast and brightness.
 *
 * `filterIntensity` blends the preset towards the untouched image, so a
 * half-strength preset is half the way from "original" to "vivid".
 */
internal object IosColorGrade {

    /** Applies the clip's filter + adjustments to a frame. */
    fun apply(image: CIImage, clip: VideoClip): CIImage =
        apply(image, clip.filter, clip.adjust, clip.filterIntensity)

    /** Applies a filter + adjustments to a frame. */
    fun apply(image: CIImage, filter: FilterPreset, adjust: AdjustSpec, intensity: Float): CIImage {
        val strength = intensity.coerceIn(0f, 1f)
        var result = image

        val saturation = (adjust.saturation * blend(1f, filterSaturation(filter), strength)).coerceIn(0f, 3f)
        val hue = adjust.hue + filterHue(filter) * strength
        if (saturation != 1f || hue != 0f) {
            result = result.imageByApplyingFilter(
                COLOR_CONTROLS,
                mapOf<Any?, Any?>(
                    "inputSaturation" to saturation.toDouble(),
                    "inputHue" to hue.toDouble() * PI / 180.0,
                ),
            )
        }

        val tint = filterTint(filter)
        val redScale = blend(1f, tint?.getOrNull(0) ?: 1f, strength) * (1f + adjust.temperature * 0.15f)
        val greenScale = blend(1f, tint?.getOrNull(1) ?: 1f, strength)
        val blueScale = blend(1f, tint?.getOrNull(2) ?: 1f, strength) * (1f - adjust.temperature * 0.15f)
        if (redScale != 1f || greenScale != 1f || blueScale != 1f) {
            result = result.imageByApplyingFilter(
                COLOR_MATRIX,
                mapOf<Any?, Any?>(
                    "inputRVector" to vectorOf(redScale.toDouble(), 0.0, 0.0, 0.0, 0.0),
                    "inputGVector" to vectorOf(0.0, greenScale.toDouble(), 0.0, 0.0, 0.0),
                    "inputBVector" to vectorOf(0.0, 0.0, blueScale.toDouble(), 0.0, 0.0),
                    "inputAVector" to vectorOf(0.0, 0.0, 0.0, 1.0, 0.0),
                ),
            )
        }

        val contrast = (adjust.contrast * blend(1f, filterContrast(filter), strength)).coerceIn(0.5f, 2f)
        val brightness = adjust.brightness + filterBrightness(filter) * strength
        if (contrast != 1f || brightness != 0f) {
            result = result.imageByApplyingFilter(
                COLOR_CONTROLS,
                mapOf<Any?, Any?>(
                    "inputContrast" to contrast.toDouble(),
                    "inputBrightness" to brightness.toDouble(),
                ),
            )
        }

        return result
    }

    /**
     * Multiplies every channel by [factor].
     *
     * Used by the fade-through-black transition: the canvas behind the video
     * is black, so scaling the colour down *is* a fade to black.
     */
    fun fade(image: CIImage, factor: Float): CIImage {
        if (factor >= 0.999f) return image
        val value = factor.coerceIn(0f, 1f).toDouble()
        return image.imageByApplyingFilter(
            COLOR_MATRIX,
            mapOf<Any?, Any?>(
                "inputRVector" to vectorOf(value, 0.0, 0.0, 0.0, 0.0),
                "inputGVector" to vectorOf(0.0, value, 0.0, 0.0, 0.0),
                "inputBVector" to vectorOf(0.0, 0.0, value, 0.0, 0.0),
                "inputAVector" to vectorOf(0.0, 0.0, 0.0, 1.0, 0.0),
            ),
        )
    }

    /**
     * Scales the alpha of an image, which is how an overlay's opacity is
     * applied.
     */
    fun applyOpacity(image: CIImage, opacity: Float): CIImage {
        val value = opacity.coerceIn(0f, 1f).toDouble()
        if (value >= 0.999) return image
        return image.imageByApplyingFilter(
            COLOR_MATRIX,
            mapOf<Any?, Any?>(
                "inputRVector" to vectorOf(0.0, 0.0, 0.0, 0.0, 0.0),
                "inputGVector" to vectorOf(0.0, 0.0, 0.0, 0.0, 0.0),
                "inputBVector" to vectorOf(0.0, 0.0, 0.0, 0.0, 0.0),
                "inputAVector" to vectorOf(0.0, 0.0, 0.0, value, 0.0),
            ),
        )
    }

    /** `value` moved from 1 towards [target] by [intensity]. */
    private fun blend(base: Float, target: Float, intensity: Float): Float =
        base + (target - base) * intensity

    /** Core Image's colour matrix takes five-component vectors. */
    private fun vectorOf(
        r: Double,
        g: Double,
        b: Double,
        a: Double,
        bias: Double,
    ): CIVector {
        val values = doubleArrayOf(r, g, b, a, bias)
        return values.usePinned { pinned -> CIVector(pinned.addressOf(0), values.size.toULong()) }
    }

    private const val COLOR_CONTROLS = "CIColorControls"
    private const val COLOR_MATRIX = "CIColorMatrix"
}
