package com.snapzy.app.feature.editor.photo

import com.snapzy.app.core.editing.AdjustSpec
import com.snapzy.app.core.editing.EditProject
import com.snapzy.app.core.editing.FilterPreset
import com.snapzy.app.core.editing.VideoClip

/**
 * The photo editor keeps the still's look inside [EditProject.clips].
 *
 * [EditProject] has no photo-specific slot for a filter or an adjustment, but
 * a photo project always carries exactly one "still clip" whose `filter` and
 * `adjust` describe the picture. Keeping the look inside the project is what
 * makes undo / redo work for the look tools (Filter, Adjust, Effects).
 *
 * Everything in this file is the single place that knows about the encoding,
 * so swapping it for a dedicated `EditProject` field later is a one-file
 * change.
 */
const val PHOTO_STILL_CLIP_ID = 0L

/** Builds the placeholder clip that carries a photo's filter and adjustments. */
fun photoStillClip(
    filter: FilterPreset = FilterPreset.ORIGINAL,
    adjust: AdjustSpec = AdjustSpec(),
): VideoClip = VideoClip(
    id = PHOTO_STILL_CLIP_ID,
    path = "",
    sourceDurationMs = 0L,
    trimStartMs = 0L,
    trimEndMs = 0L,
    filter = filter,
    adjust = adjust,
)

/** The filter applied to the photo. */
val EditProject.photoFilter: FilterPreset
    get() = clips.firstOrNull { it.id == PHOTO_STILL_CLIP_ID }?.filter ?: FilterPreset.ORIGINAL

/** The adjustments applied to the photo. */
val EditProject.photoAdjust: AdjustSpec
    get() = clips.firstOrNull { it.id == PHOTO_STILL_CLIP_ID }?.adjust ?: AdjustSpec()

/** Replaces the photo's look, leaving every other part of the project alone. */
fun EditProject.withPhotoLook(filter: FilterPreset, adjust: AdjustSpec): EditProject {
    val still = clips.firstOrNull { it.id == PHOTO_STILL_CLIP_ID }
    val updated = (still ?: photoStillClip()).copy(filter = filter, adjust = adjust)
    val clips = if (still == null) {
        clips + updated
    } else {
        clips.map { if (it.id == PHOTO_STILL_CLIP_ID) updated else it }
    }
    return copy(clips = clips)
}

/**
 * The next free overlay id.
 *
 * Derived from the project rather than kept in a counter so undo / redo can
 * never hand the same id to two layers.
 */
val EditProject.nextOverlayId: Long
    get() = (overlays.maxOfOrNull { it.id } ?: 0L) + 1L
