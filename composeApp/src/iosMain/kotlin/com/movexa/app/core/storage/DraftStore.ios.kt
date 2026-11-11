@file:OptIn(ExperimentalForeignApi::class)

package com.movexa.app.core.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.rename

/** The iOS draft store: JSON files in the app's temporary directory. */
actual fun createDraftStore(): DraftStore = FileDraftStore(createDraftFiles())

/** Wall clock time, in milliseconds since the Unix epoch. */
actual fun currentTimeMillis(): Long =
    ((NSDate().timeIntervalSinceReferenceDate + EPOCH_OFFSET_SECONDS) * 1000.0).toLong()

/** Seconds between the Unix epoch and the reference date (1 Jan 2001). */
private const val EPOCH_OFFSET_SECONDS = 978307200.0

internal actual fun createDraftFiles(): DraftFiles = IosDraftFiles()

/**
 * Drafts live in `NSTemporaryDirectory()/snapzy/drafts`.
 *
 * The temporary directory is the one place iOS apps may write without any
 * entitlement, and it is where the rest of the app keeps its generated
 * media too (see `core/media/FileStore.ios.kt`).
 *
 * The file work runs on the main dispatcher, like the rest of the iOS
 * implementation, but every call is a single small file read or write.
 */
private class IosDraftFiles : DraftFiles {

    private val directory: String = NSTemporaryDirectory() + DRAFTS_FOLDER

    override fun draftsDirectory(): String = directory

    override suspend fun ensureDirectory() = withContext(Dispatchers.Main) {
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
        Unit
    }

    override suspend fun readText(path: String): String? = withContext(Dispatchers.Main) {
        runCatching { readFile(path) }.getOrNull()
    }

    override suspend fun writeTextAtomically(path: String, text: String): Boolean =
        withContext(Dispatchers.Main) {
            runCatching {
                val temp = path + TEMP_EXTENSION
                if (!writeFile(temp, text)) return@runCatching false
                // rename(2) puts the temporary file in place in one step.
                if (rename(temp, path) == 0) {
                    true
                } else {
                    writeFile(path, text)
                }
            }.getOrDefault(false)
        }

    override suspend fun deleteFile(path: String) = withContext(Dispatchers.Main) {
        runCatching { NSFileManager.defaultManager.removeItemAtPath(path, null) }
        Unit
    }

    override suspend fun listJsonFiles(): List<String> = withContext(Dispatchers.Main) {
        runCatching {
            NSFileManager.defaultManager.contentsOfDirectoryAtPath(directory, null)
                ?.mapNotNull { it as? String }
                ?.filter { it.endsWith(JSON_EXTENSION) }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    /** Reads a whole file as text, or `null` when it cannot be read. */
    private fun readFile(path: String): String? {
        val file = fopen(path, "rb") ?: return null
        try {
            fseek(file, 0, SEEK_END)
            val size = ftell(file).toInt()
            fseek(file, 0, SEEK_SET)
            if (size <= 0) return null
            val buffer = ByteArray(size)
            buffer.usePinned { pinned ->
                fread(pinned.addressOf(0), 1u, size.convert(), file)
            }
            return buffer.decodeToString()
        } finally {
            fclose(file)
        }
    }

    /** Writes [text] to [path], replacing anything already there. */
    private fun writeFile(path: String, text: String): Boolean {
        val bytes = text.encodeToByteArray()
        val file = fopen(path, "wb") ?: return false
        try {
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned ->
                    fwrite(pinned.addressOf(0), 1u, bytes.size.convert(), file)
                }
            }
        } finally {
            fclose(file)
        }
        return true
    }

    private companion object {
        const val DRAFTS_FOLDER = "snapzy/drafts/"
        const val JSON_EXTENSION = ".json"
        const val TEMP_EXTENSION = ".tmp"
        const val SEEK_SET = 0
        const val SEEK_END = 2
    }
}
