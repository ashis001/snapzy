package com.snapzy.app.core.editing

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Holds the current [EditProject] plus an undo/redo history.
 *
 * State is a Compose snapshot state so screens recompose automatically
 * without a StateFlow/collectAsState round trip, and so slider drags stay
 * at 60fps while the undo stack only records the value the gesture started
 * from (see [updateLive]).
 */
@Stable
class EditorController(initial: EditProject) {

    var project by mutableStateOf(initial)
        private set

    private val undoStack = ArrayDeque<EditProject>()
    private val redoStack = ArrayDeque<EditProject>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Applies a change and records the previous state on the undo stack. */
    fun update(transform: (EditProject) -> EditProject) {
        val previous = project
        val next = transform(previous)
        if (next == previous) return
        undoStack.addLast(previous)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        project = next
    }

    /**
     * Applies a change without touching the undo stack. Used for continuous
     * gestures (sliders, dragging a clip) where the caller already pushed a
     * single undo entry at gesture start via [update].
     */
    fun updateLive(transform: (EditProject) -> EditProject) {
        project = transform(project)
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(project)
        project = previous
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(project)
        project = next
    }

    /** Resets the whole project (used when swapping the media behind an editor). */
    fun reset(next: EditProject) {
        undoStack.clear()
        redoStack.clear()
        project = next
    }

    companion object {
        const val MAX_HISTORY = 50
    }
}

/** Recomputes the total timeline length from the clip list. */
fun EditProject.withRecomputedDuration(): EditProject =
    copy(durationMs = clips.sumOf { it.timelineDurationMs })
