package com.snapzy.app.core.storage

import com.snapzy.app.core.editing.EditKind
import com.snapzy.app.core.editing.EditProject

/**
 * A saved edit as the home screen sees it.
 *
 * The summary is deliberately small: it carries what a draft card needs and
 * nothing else, so listing drafts does not have to read (or decode) the
 * media a draft points at.
 */
data class DraftSummary(
    val id: String,
    val title: String,
    /** Epoch millis of the last save. */
    val updatedAt: Long,
    val durationMs: Long,
    val kind: EditKind,
    /** The file the poster frame comes from, or `null` when there is none. */
    val thumbnailPath: String?,
)

/**
 * Reads and writes saved edits.
 *
 * A draft is the whole [EditProject] plus the bits the home screen needs to
 * show it in a list. Saving the same id twice updates the draft in place,
 * keeping its creation time.
 *
 * Implementations never throw on unreadable data: a draft whose file is
 * corrupt is skipped rather than taking the app down with it.
 */
interface DraftStore {

    /** Every saved draft, newest first. */
    suspend fun list(): List<DraftSummary>

    /**
     * Saves [project] under [title].
     *
     * Pass an [id] to update an existing draft, or leave it out to start a
     * new one. Returns the id the draft was stored under.
     */
    suspend fun save(project: EditProject, title: String, id: String? = null): String

    /** The project saved under [id], or `null` when it is missing or unreadable. */
    suspend fun load(id: String): EditProject?

    /** Removes the draft saved under [id]. */
    suspend fun delete(id: String)

    /** The file the draft's poster frame comes from, or `null` when there is none. */
    suspend fun thumbnail(id: String): String?
}

/** Creates the platform's draft store. */
expect fun createDraftStore(): DraftStore

/**
 * Milliseconds since the Unix epoch.
 *
 * The Kotlin standard library has no common clock, so the platform supplies
 * one. Exposed here so the home screen can render relative draft
 * timestamps.
 */
expect fun currentTimeMillis(): Long
