package com.movexa.app.core.media

import com.movexa.app.core.editing.EditProject

/** Output settings for a video render. */
data class ExportSettings(
    val width: Int = 1080,
    val height: Int = 1920,
    val frameRate: Int = 30,
    val videoBitrate: Int = 10_000_000,
) {
    companion object {
        val P720 = ExportSettings(720, 1280, 30, 5_000_000)
        val P1080 = ExportSettings(1080, 1920, 30, 10_000_000)
        val P4K = ExportSettings(2160, 3840, 30, 24_000_000)
    }
}

/**
 * Renders the whole [project] (clips, overlays, colour, speed) to an mp4 file
 * in the app cache directory and returns its path, or `null` if the render
 * failed.
 *
 * This is the real thing — Media3 Transformer on Android (trim, speed, mute,
 * gain, rotation, LUT-free colour grading, text and bitmap overlays, output
 * resolution), AVFoundation on iOS. It is not a preview-quality approximation.
 */
expect suspend fun renderVideoProject(
    project: EditProject,
    settings: ExportSettings,
    onProgress: (Float) -> Unit = {},
): String?
