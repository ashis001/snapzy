package com.snapzy.app.core.storage

/**
 * The file primitives the shared draft store needs.
 *
 * The store itself is platform independent — it only knows about JSON and
 * draft ids. Where the drafts live and how a file is written is the
 * platform's business.
 */
internal interface DraftFiles {

    /** Absolute path of the folder the drafts live in, with a trailing separator. */
    fun draftsDirectory(): String

    /** Creates the drafts folder when it is not there yet. */
    suspend fun ensureDirectory()

    /** The contents of the file at [path], or `null` when it cannot be read. */
    suspend fun readText(path: String): String?

    /**
     * Writes [text] to [path], replacing anything already there.
     *
     * The write goes to a temporary file first and is then moved into place,
     * so a draft file is never half written.
     */
    suspend fun writeTextAtomically(path: String, text: String): Boolean

    /** Removes the file at [path]. */
    suspend fun deleteFile(path: String)

    /** The names of the JSON files in the drafts folder, not their paths. */
    suspend fun listJsonFiles(): List<String>
}

/** Creates the platform's draft file primitives. */
internal expect fun createDraftFiles(): DraftFiles
