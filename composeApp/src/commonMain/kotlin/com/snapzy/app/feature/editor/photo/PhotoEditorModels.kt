package com.snapzy.app.feature.editor.photo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

enum class PhotoFilter(val label: String) {
    ORIGINAL("Original"),
    MONO("Mono"),
    WARM("Warm"),
    COOL("Cool"),
    VIVID("Vivid"),
}

enum class PhotoTool(val label: String) {
    CROP("Crop"),
    FILTER("Filter"),
    ADJUST("Adjust"),
    TEXT("Text"),
    DRAW("Draw"),
}

enum class CropAspect(val label: String, val ratio: Float?) {
    FREE("Free", null),
    SQUARE("1:1", 1f),
    PORTRAIT("4:5", 4f / 5f),
    WIDE("16:9", 16f / 9f),
    STORY("9:16", 9f / 16f),
}

class TextOverlayItem(
    val id: Long,
    text: String,
    offset: Offset,
    color: Color,
) {
    var text by mutableStateOf(text)
    var offset by mutableStateOf(offset)
    var color by mutableStateOf(color)
}

/**
 * Points, not a Path: a Compose Path is a plain mutable object, not observable state,
 * so appending to it with .lineTo() never triggers recomposition mid-drag. The Canvas
 * draw block rebuilds a Path from [points] each time, and mutableStateListOf is what
 * actually notifies Compose as points are added while dragging.
 */
class DrawPathItem(
    val color: Color,
    val strokeWidthPx: Float,
) {
    val points = mutableStateListOf<Offset>()
}
