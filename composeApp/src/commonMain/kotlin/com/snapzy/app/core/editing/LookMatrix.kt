package com.snapzy.app.core.editing

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix

/**
 * Builds the preview colour matrix for a filter + adjust combination.
 *
 * This is deliberately the same maths the export pipeline uses
 * (`VideoRender.android.kt` -> `colorEffects`), so the preview and the
 * rendered file agree instead of drifting apart.
 */
fun buildLookMatrix(filter: FilterPreset, adjust: AdjustSpec): ColorMatrix {
    var matrix = ColorMatrix()

    val saturation = (adjust.saturation * filterSaturation(filter)).coerceIn(0f, 3f)
    val hue = adjust.hue + filterHue(filter)
    if (saturation != 1f || hue != 0f) {
        matrix = concat(matrix, saturationMatrix(saturation))
        if (hue != 0f) matrix = concat(matrix, hueMatrix(hue))
    }

    val tint = filterTint(filter)
    if (tint != null) {
        matrix = concat(matrix, scaleMatrix(tint[0], tint[1], tint[2]))
    }

    if (adjust.temperature != 0f) {
        val t = adjust.temperature
        matrix = concat(matrix, scaleMatrix(1f + t * 0.15f, 1f, 1f - t * 0.15f))
    }

    val contrast = (adjust.contrast * filterContrast(filter)).coerceIn(0f, 2f)
    if (contrast != 1f) matrix = concat(matrix, contrastMatrix(contrast))

    val brightness = adjust.brightness + filterBrightness(filter)
    if (brightness != 0f) matrix = concat(matrix, brightnessMatrix(brightness))

    return matrix
}

fun filterBrightness(filter: FilterPreset): Float = when (filter) {
    FilterPreset.BRIGHT -> 0.12f
    FilterPreset.FADE -> 0.06f
    FilterPreset.NIGHT -> -0.08f
    else -> 0f
}

fun filterContrast(filter: FilterPreset): Float = when (filter) {
    FilterPreset.VIVID -> 1.12f
    FilterPreset.FILM -> 0.92f
    FilterPreset.FADE -> 0.88f
    else -> 1f
}

fun filterSaturation(filter: FilterPreset): Float = when (filter) {
    FilterPreset.VIVID -> 1.35f
    FilterPreset.MONO -> 0f
    FilterPreset.FILM -> 0.85f
    FilterPreset.NIGHT -> 0.8f
    FilterPreset.SUNSET -> 1.15f
    else -> 1f
}

fun filterHue(filter: FilterPreset): Float = when (filter) {
    FilterPreset.SUNSET -> 8f
    FilterPreset.MINT -> -6f
    else -> 0f
}

fun filterTint(filter: FilterPreset): FloatArray? = when (filter) {
    FilterPreset.WARM -> floatArrayOf(1.08f, 1.02f, 0.9f)
    FilterPreset.COOL -> floatArrayOf(0.94f, 1.0f, 1.1f)
    FilterPreset.SUNSET -> floatArrayOf(1.12f, 1.0f, 0.95f)
    FilterPreset.MINT -> floatArrayOf(0.96f, 1.08f, 1.02f)
    FilterPreset.NIGHT -> floatArrayOf(0.95f, 0.98f, 1.12f)
    FilterPreset.SEPIA -> floatArrayOf(1.1f, 1.0f, 0.82f)
    else -> null
}

private fun concat(a: ColorMatrix, b: ColorMatrix): ColorMatrix {
    val result = ColorMatrix()
    result.timesAssign(a)
    result.timesAssign(b)
    return result
}

private fun saturationMatrix(saturation: Float): ColorMatrix = ColorMatrix().apply { setToSaturation(saturation) }

private fun hueMatrix(degrees: Float): ColorMatrix {
    val rad = kotlin.math.PI * degrees / 180.0
    val cos = kotlin.math.cos(rad).toFloat()
    val sin = kotlin.math.sin(rad).toFloat()
    val lr = 0.213f
    val lg = 0.715f
    val lb = 0.072f
    return ColorMatrix(
        floatArrayOf(
            lr + cos * (1 - lr) + sin * (-lr), lg - cos * lg + sin * (-lg), lb - cos * lb + sin * (1 - lb), 0f, 0f,
            lr - cos * lr + sin * 0.143f, lg + cos * (1 - lg) + sin * 0.140f, lb - cos * lb + sin * (-0.283f), 0f, 0f,
            lr - cos * lr + sin * (-(1 - lr)), lg - cos * lg + sin * lg, lb + cos * (1 - lb) + sin * lb, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

private fun contrastMatrix(contrast: Float): ColorMatrix {
    val translate = (1f - contrast) * 0.5f * 255f
    return ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

private fun brightnessMatrix(brightness: Float): ColorMatrix {
    val translate = brightness * 255f
    return ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, translate,
            0f, 1f, 0f, 0f, translate,
            0f, 0f, 1f, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

private fun scaleMatrix(red: Float, green: Float, blue: Float): ColorMatrix = ColorMatrix(
    floatArrayOf(
        red, 0f, 0f, 0f, 0f,
        0f, green, 0f, 0f, 0f,
        0f, 0f, blue, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/**
 * A two-colour swatch that stands in for a filter preview.
 *
 * Real filters need a thumbnail of the source clip, which the preview layer
 * cannot render cheaply, so each preset gets a representative gradient. It
 * lives next to the filter maths so the swatch and the exported frame agree.
 */
fun filterSwatch(filter: FilterPreset): List<Color> = when (filter) {
    FilterPreset.ORIGINAL -> listOf(Color(0xFF3A3E47), Color(0xFF1E212A))
    FilterPreset.BRIGHT -> listOf(Color(0xFFFFF3D0), Color(0xFFFFD98A))
    FilterPreset.VIVID -> listOf(Color(0xFFFF4FA3), Color(0xFF38E1D0))
    FilterPreset.WARM -> listOf(Color(0xFFFFB067), Color(0xFFE85D3C))
    FilterPreset.COOL -> listOf(Color(0xFF6FD8FF), Color(0xFF2C6BE0))
    FilterPreset.MONO -> listOf(Color(0xFFB9BDC6), Color(0xFF43474F))
    FilterPreset.FILM -> listOf(Color(0xFFD9C48A), Color(0xFF6B5B2E))
    FilterPreset.FADE -> listOf(Color(0xFFE8E2D6), Color(0xFFA79E92))
    FilterPreset.SEPIA -> listOf(Color(0xFFE8C89A), Color(0xFF7A4A28))
    FilterPreset.NIGHT -> listOf(Color(0xFF4C6BE0), Color(0xFF131A3C))
    FilterPreset.SUNSET -> listOf(Color(0xFFFF8A4C), Color(0xFFE23B6E))
    FilterPreset.MINT -> listOf(Color(0xFF7BE8C4), Color(0xFF1E8A70))
}
