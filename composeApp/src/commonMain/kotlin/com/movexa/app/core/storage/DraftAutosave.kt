package com.movexa.app.core.storage

import com.movexa.app.core.editing.EditKind
import com.movexa.app.core.editing.EditProject
import com.movexa.app.core.media.EditorSessionStore
import kotlinx.coroutines.delay

/**
 * Saves an edit as a draft.
 *
 * Every change in the editor is written to disk, so the work survives leaving
 * the app — and the home screen can list it again.
 */
object DraftAutosave {

    /** How long the editor waits after a change before writing. */
    const val DEBOUNCE_MS = 700L

    /**
     * Writes [project] out as a draft.
     *
     * The id is kept on the session, so a session keeps updating the same
     * draft rather than filling the list with copies.
     */
    suspend fun save(project: EditProject, title: String) {
        val id = EditorSessionStore.draftId
            ?: "draft-${currentTimeMillis()}".also { EditorSessionStore.draftId = it }
        createDraftStore().save(project, title, id)
    }

    /** Waits for the user to stop fiddling, then saves. */
    suspend fun saveAfterPause(project: EditProject, title: String) {
        delay(DEBOUNCE_MS)
        if (project.isEmpty) return
        save(project, title)
    }

    /** A readable name for a draft. */
    fun title(project: EditProject): String {
        val kind = if (project.kind == EditKind.VIDEO) "Video" else "Photo"
        val seconds = (project.durationMs / 1000L).coerceAtLeast(0L)
        return "$kind · ${seconds}s"
    }
}
