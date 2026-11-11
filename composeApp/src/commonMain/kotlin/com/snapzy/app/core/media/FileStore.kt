package com.snapzy.app.core.media

/**
 * A tiny file-system shim so common code can materialise generated audio.
 *
 * The music and sound-effect catalogues are synthesised on demand (see
 * `AudioSynth`); they need somewhere to live before the export pipeline can
 * read them, and that place is platform-specific.
 */

/** An absolute path inside the platform's cache directory. */
expect fun appCachePath(name: String): String

/** Writes [bytes] to [path], replacing anything already there. */
expect suspend fun writeBytesToFile(path: String, bytes: ByteArray): Boolean

/** Reads the whole file at [path], or `null` when it is missing. */
expect suspend fun readBytesFromFile(path: String): ByteArray?
