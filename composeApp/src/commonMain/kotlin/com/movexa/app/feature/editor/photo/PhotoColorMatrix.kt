package com.movexa.app.feature.editor.photo

import androidx.compose.ui.graphics.ColorMatrix

/** Combines the selected filter preset with the live brightness/contrast/saturation sliders into one matrix. */
fun buildColorMatrix(
    filter: PhotoFilter,
    brightness: Float, // -1f..1f
    contrast: Float, // 0.5f..1.5f
    saturation: Float, // 0f..2f
): ColorMatrix {
    var matrix = ColorMatrix()

    val filterSaturation = when (filter) {
        PhotoFilter.MONO -> 0f
        PhotoFilter.VIVID -> 1.4f
        else -> 1f
    }
    matrix = concat(matrix, saturationMatrix(filterSaturation * saturation))

    when (filter) {
        PhotoFilter.WARM -> matrix = concat(matrix, tintMatrix(red = 1.12f, green = 1.03f, blue = 0.88f))
        PhotoFilter.COOL -> matrix = concat(matrix, tintMatrix(red = 0.88f, green = 1.0f, blue = 1.15f))
        PhotoFilter.VIVID -> matrix = concat(matrix, contrastMatrix(1.12f))
        else -> {}
    }

    matrix = concat(matrix, contrastMatrix(contrast))
    matrix = concat(matrix, brightnessMatrix(brightness))
    return matrix
}

private fun concat(a: ColorMatrix, b: ColorMatrix): ColorMatrix {
    val result = ColorMatrix()
    result.timesAssign(a)
    result.timesAssign(b)
    return result
}

private fun saturationMatrix(saturation: Float): ColorMatrix = ColorMatrix().apply { setToSaturation(saturation) }

private fun contrastMatrix(contrast: Float): ColorMatrix {
    val translate = (1f - contrast) * 0.5f * 255f
    return ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
        )
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
        )
    )
}

private fun tintMatrix(red: Float, green: Float, blue: Float): ColorMatrix = ColorMatrix(
    floatArrayOf(
        red, 0f, 0f, 0f, 0f,
        0f, green, 0f, 0f, 0f,
        0f, 0f, blue, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
)
