package com.snapzy.app.feature.editor.video

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.snapzy.app.core.editing.AudioKind
import com.snapzy.app.core.editing.AudioTrack
import com.snapzy.app.core.editing.CatalogueItem
import com.snapzy.app.core.editing.EditProject
import com.snapzy.app.core.editing.EditorController
import com.snapzy.app.core.editing.Overlay
import com.snapzy.app.core.editing.StickerItem
import com.snapzy.app.core.editing.VideoClip
import com.snapzy.app.core.editing.withRecomputedDuration
import com.snapzy.app.core.media.PickedMedia
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The editing session behind the video editor.
 *
 * It owns the [EditorController] (so every mutation is undoable) plus the
 * selection state the screens need: which clip is active, which overlay layer
 * is active and where the preview should seek to.
 *
 * The look (filter / adjustments) lives on the clip, the transition lives on
 * the project and the blend mode lives on the PIP layer, so nothing has to be
 * mirrored here.
 */
@Stable
class VideoEditSession(
    initial: EditProject,
    /** The videos the user picked; the "+" button adds the ones still unused. */
    private val sources: List<PickedMedia.Video> = emptyList(),
) {

    val controller = EditorController(initial)

    /** Clip the tools act on. */
    var selectedClipIndex by mutableStateOf(0)
        private set

    /** Overlay the tools act on (text, sticker, drawing or PIP). */
    var selectedOverlayId by mutableStateOf<Long?>(null)
        private set

    /**
     * A one-shot seek request for the preview player.
     *
     * The player is owned by the screen, so the panel asks the session for a
     * seek and the screen picks it up.
     */
    var seekRequestMs by mutableStateOf<Long?>(null)
        private set

    /** Requests the preview to move to [ms]. */
    fun seek(ms: Long) {
        seekRequestMs = ms
    }

    /** Clears the pending seek once the screen has applied it. */
    fun consumeSeek() {
        seekRequestMs = null
    }

    private val clock = TimeSource.Monotonic
    private var lastChangeAt = clock.markNow()
    private var idCounter = FIRST_GENERATED_ID

    val project: EditProject get() = controller.project

    /** A fresh id for a clip, overlay or audio track created by the editor. */
    fun nextId(): Long = idCounter++

    /**
     * Applies a change, collapsing a continuous gesture (slider drag, trim
     * handle drag, overlay drag) into a single undo entry.
     *
     * Consecutive changes that land within [GESTURE_WINDOW] of each other are
     * treated as one gesture: the first one is recorded on the undo stack,
     * the rest only mutate the live project.
     */
    fun apply(transform: (EditProject) -> EditProject) {
        if (lastChangeAt.elapsedNow() < GESTURE_WINDOW) {
            controller.updateLive(transform)
        } else {
            controller.update(transform)
        }
        lastChangeAt = clock.markNow()
    }

    /** Applies a discrete change (chip tap, button press) as its own undo entry. */
    fun commit(transform: (EditProject) -> EditProject) {
        controller.update(transform)
    }

    /** Updates one clip on the timeline and refreshes the project duration. */
    fun updateClip(index: Int, transform: (VideoClip) -> VideoClip) {
        apply { project -> project.replaceClip(index, transform) }
    }

    /** Updates one clip on the timeline as a discrete change. */
    fun commitClip(index: Int, transform: (VideoClip) -> VideoClip) {
        commit { project -> project.replaceClip(index, transform) }
    }

    /** Updates one overlay layer. */
    fun updateOverlay(id: Long, transform: (Overlay) -> Overlay) {
        apply { project ->
            project.copy(overlays = project.overlays.map { overlay -> if (overlay.id == id) transform(overlay) else overlay })
        }
    }

    /** Updates one overlay layer as a discrete change (a style tap). */
    fun commitOverlay(id: Long, transform: (Overlay) -> Overlay) {
        commit { project ->
            project.copy(overlays = project.overlays.map { overlay -> if (overlay.id == id) transform(overlay) else overlay })
        }
    }

    /* ------------------------------------------------------------ selection -- */

    fun selectClip(index: Int) {
        selectedClipIndex = index.coerceIn(0, project.clips.lastIndex.coerceAtLeast(0))
    }

    fun selectOverlay(id: Long?) {
        selectedOverlayId = id
    }

    /* --------------------------------------------------------------- clips -- */

    /** Splits the clip at [index] at [positionMs] (a position inside the source file). */
    fun splitClip(index: Int, positionMs: Long) {
        val clip = project.clips.getOrNull(index) ?: return
        val splitAt = positionMs.coerceIn(clip.trimStartMs + MIN_CLIP_MS, clip.trimEndMs - MIN_CLIP_MS)
        if (splitAt <= clip.trimStartMs || splitAt >= clip.trimEndMs) return
        val right = clip.copy(id = nextId(), trimStartMs = splitAt)
        commit { project ->
            val clips = project.clips.toMutableList()
            clips[index] = clip.copy(trimEndMs = splitAt)
            clips.add(index + 1, right)
            project.copy(clips = clips).withRecomputedDuration()
        }
    }

    /** Adds a copy of the clip at [index] right after it. */
    fun duplicateClip(index: Int) {
        val clip = project.clips.getOrNull(index) ?: return
        val duplicate = clip.copy(id = nextId())
        commit { project ->
            val clips = project.clips.toMutableList()
            clips.add(index + 1, duplicate)
            project.copy(clips = clips).withRecomputedDuration()
        }
    }

    /** Removes the clip at [index]. The last clip is never removed. */
    fun deleteClip(index: Int) {
        if (project.clips.size <= 1) return
        commit { project ->
            val clips = project.clips.toMutableList()
            clips.removeAt(index)
            project.copy(clips = clips).withRecomputedDuration()
        }
        selectClip(index)
    }

    /**
     * Adds the next picked video that is not on the timeline yet.
     *
     * When every picked video is already used the last clip is duplicated, so
     * the "+" button always does something.
     */
    fun addNextSourceClip() {
        val used = project.clips.map { it.path }.toSet()
        val next = sources.firstOrNull { it.filePath !in used }
        if (next == null) {
            duplicateClip(project.clips.lastIndex)
            return
        }
        val clip = VideoClip(
            id = nextId(),
            path = next.filePath,
            sourceDurationMs = next.durationMs,
            trimStartMs = 0L,
            trimEndMs = next.durationMs,
        )
        commit { project -> project.copy(clips = project.clips + clip).withRecomputedDuration() }
    }

    /** Swaps two clips on the timeline. */
    fun swapClips(first: Int, second: Int) {
        val clips = project.clips
        if (first !in clips.indices || second !in clips.indices) return
        commit { project ->
            val list = project.clips.toMutableList()
            val swap = list[first]
            list[first] = list[second]
            list[second] = swap
            project.copy(clips = list).withRecomputedDuration()
        }
    }

    /** Plays the timeline backwards by reversing the order of the clips. */
    fun reverseTimeline() {
        commit { project -> project.copy(clips = project.clips.reversed()).withRecomputedDuration() }
    }

    /** Inserts a half second still of the frame at [positionMs] after the clip. */
    fun freezeAtPlayhead(index: Int, positionMs: Long) {
        val clip = project.clips.getOrNull(index) ?: return
        val start = positionMs.coerceIn(clip.trimStartMs, (clip.trimEndMs - FREEZE_MS).coerceAtLeast(clip.trimStartMs))
        val still = clip.copy(id = nextId(), trimStartMs = start, trimEndMs = start + FREEZE_MS)
        commit { project ->
            val clips = project.clips.toMutableList()
            clips.add(index + 1, still)
            project.copy(clips = clips).withRecomputedDuration()
        }
    }

    /** Copies the filter and adjustments of one clip to every other clip. */
    fun applyLookToAllClips(index: Int) {
        val source = project.clips.getOrNull(index) ?: return
        commit { project ->
            project.copy(
                clips = project.clips.map { clip ->
                    clip.copy(filter = source.filter, filterIntensity = source.filterIntensity, adjust = source.adjust)
                },
            )
        }
    }

    /* ------------------------------------------------------------ overlays -- */

    /** Adds a text layer and selects it. */
    fun addText() {
        val id = nextId()
        commit { project ->
            project.copy(
                overlays = project.overlays + Overlay.Text(
                    id = id,
                    text = "Add text",
                    position = Offset(0.5f, 0.3f),
                ),
            )
        }
        selectOverlay(id)
    }

    /** Adds a sticker layer and selects it. */
    fun addSticker(sticker: StickerItem) {
        val id = nextId()
        commit { project ->
            project.copy(
                overlays = project.overlays + Overlay.Sticker(
                    id = id,
                    emoji = sticker.emoji,
                    label = sticker.label,
                    position = Offset(0.5f, 0.5f),
                ),
            )
        }
        selectOverlay(id)
    }

    /** Adds a picture-in-picture layer from one of the timeline clips. */
    fun addPipLayer(clip: VideoClip) {
        val id = nextId()
        commit { project ->
            project.copy(
                overlays = project.overlays + Overlay.Pip(
                    id = id,
                    path = clip.path,
                    sourceDurationMs = clip.sourceDurationMs,
                    trimStartMs = clip.trimStartMs,
                    trimEndMs = clip.trimEndMs,
                ),
            )
        }
        selectOverlay(id)
    }

    /** Removes one overlay layer. */
    fun deleteOverlay(id: Long) {
        commit { project -> project.copy(overlays = project.overlays.filterNot { it.id == id }) }
        if (selectedOverlayId == id) selectOverlay(null)
    }

    /** Removes every freehand stroke. */
    fun clearDrawings() {
        commit { project -> project.copy(overlays = project.overlays.filterNot { it is Overlay.Drawing }) }
        if (project.overlays.none { it is Overlay.Drawing }) {
            selectedOverlayId?.let { id ->
                if (project.overlays.none { it.id == id }) selectOverlay(null)
            }
        }
    }

    /* --------------------------------------------------------------- audio -- */

    /** Adds a music or sound-effect track from the bundled catalogue. */
    fun addAudioTrack(item: CatalogueItem, kind: AudioKind) {
        val id = nextId()
        commit { project ->
            project.copy(
                audioTracks = project.audioTracks + AudioTrack(
                    id = id,
                    label = item.title,
                    kind = kind,
                    durationMs = item.durationMs,
                ),
            )
        }
    }

    /** Removes an audio track. */
    fun removeAudioTrack(id: Long) {
        commit { project -> project.copy(audioTracks = project.audioTracks.filterNot { it.id == id }) }
    }

    private companion object {
        val GESTURE_WINDOW = 600.milliseconds

        /** Ids for editor-generated items start well above the picked-media ids. */
        const val FIRST_GENERATED_ID = 1_000L

        /** Length of the still frame inserted by the freeze tool. */
        const val FREEZE_MS = 500L
    }
}

/** Replaces one clip on the timeline and refreshes the project duration. */
private fun EditProject.replaceClip(index: Int, transform: (VideoClip) -> VideoClip): EditProject = copy(
    clips = clips.mapIndexed { i, clip -> if (i == index) transform(clip) else clip },
).withRecomputedDuration()
