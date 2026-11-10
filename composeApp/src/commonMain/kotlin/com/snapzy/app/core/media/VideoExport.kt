package com.snapzy.app.core.media

/** Trim [sourcePath] to [startMs]..[endMs] (optionally dropping audio) and write a new video file. */
expect fun trimAndExportVideo(
    sourcePath: String,
    startMs: Long,
    endMs: Long,
    muted: Boolean,
    onComplete: (outputPath: String?) -> Unit,
)
