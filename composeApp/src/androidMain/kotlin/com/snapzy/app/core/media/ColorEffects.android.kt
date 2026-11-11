package com.snapzy.app.core.media

import androidx.media3.common.Effect
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import com.snapzy.app.core.editing.AdjustSpec
import com.snapzy.app.core.editing.FilterPreset

/**
 * Maps a filter and an adjustment onto Media3's colour effects.
 *
 * Deliberately LUT-free: the same maths powers the preview and the export,
 * so what you see is what you get.
 */
internal fun colorEffects(filter: FilterPreset, adjust: AdjustSpec): List<Effect> {
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
