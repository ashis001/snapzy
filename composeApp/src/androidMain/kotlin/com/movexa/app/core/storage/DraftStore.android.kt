package com.movexa.app.core.storage

import com.movexa.app.core.media.AndroidAppContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Android draft store: JSON files in the app's private files directory. */
actual fun createDraftStore(): DraftStore = FileDraftStore(createDraftFiles())

/** Wall clock time, in milliseconds since the Unix epoch. */
actual fun currentTimeMillis(): Long = System.currentTimeMillis()

internal actual fun createDraftFiles(): DraftFiles = AndroidDraftFiles()

/**
 * Drafts live in `filesDir/snapzy/drafts`, which is private to the app:
 * the drafts survive a restart and go away when the app is uninstalled.
 *
 * Everything happens on the IO dispatcher — the home screen lists drafts
 * from its composition scope.
 */
private class AndroidDraftFiles : DraftFiles {

    /**
     * The drafts folder.
     *
     * `AndroidAppContext.context` is set from `MainActivity.onCreate`. The
     * lookup is guarded so a draft store created before that cannot take the
     * app down: without a context there is simply nowhere to keep drafts.
     */
    private val directory: File?
        get() = runCatching { File(AndroidAppContext.context.filesDir, DRAFTS_FOLDER) }.getOrNull()

    override fun draftsDirectory(): String =
        directory?.absolutePath?.plus(File.separator).orEmpty()

    override suspend fun ensureDirectory() = withContext(Dispatchers.IO) {
        directory?.mkdirs()
        Unit
    }

    override suspend fun readText(path: String): String? = withContext(Dispatchers.IO) {
        runCatching { File(path).takeIf { it.isFile }?.readText() }.getOrNull()
    }

    override suspend fun writeTextAtomically(path: String, text: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(path)
                target.parentFile?.mkdirs()
                val temp = File(target.parentFile, target.name + TEMP_EXTENSION)
                temp.writeText(text)
                // renameTo replaces the target on POSIX; the second attempt
                // covers the case where the target could not be replaced.
                if (!temp.renameTo(target)) {
                    target.delete()
                    temp.renameTo(target)
                }
                true
            }.getOrDefault(false)
        }

    override suspend fun deleteFile(path: String) = withContext(Dispatchers.IO) {
        runCatching { File(path).delete() }
        Unit
    }

    override suspend fun listJsonFiles(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            directory?.listFiles()?.map { it.name }?.filter { it.endsWith(JSON_EXTENSION) }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val DRAFTS_FOLDER = "snapzy/drafts"
        const val JSON_EXTENSION = ".json"
        const val TEMP_EXTENSION = ".tmp"
    }
}
