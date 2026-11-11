package com.snapzy.app.core.media

import com.snapzy.app.core.editing.EditProject

/**
 * Hands picked media and the finished edit between screens.
 *
 * The nav graph carries only route strings, so the payload has to live
 * somewhere shared. A single active edit session at a time matches the app's
 * one-editor-at-a-time flow.
 */
object EditorSessionStore {
    /** Single image picked for the photo editor. */
    var pickedImage: PickedMedia.Image? = null

    /** Single video picked for the video editor. */
    var pickedVideo: PickedMedia.Video? = null

    /** Every video picked in a multi-select session. */
    var pickedVideos: List<PickedMedia.Video> = emptyList()

    /** Every image picked in a multi-select session (collage / photo). */
    var pickedImages: List<PickedMedia.Image> = emptyList()

    /**
     * The edit being worked on, handed to the export screen so it can render
     * the video with the chosen resolution.
     */
    var project: EditProject? = null

    var exportedImageBytes: ByteArray? = null
    var exportedVideoPath: String? = null

    /**
     * A saved draft that was reopened from the home screen.
     *
     * The editors build their project from this instead of from the picked
     * media, so an edit survives leaving the app.
     */
    var draftProject: EditProject? = null

    /** The id of the draft this editing session writes to. */
    var draftId: String? = null

    fun clear() {
        pickedImage = null
        pickedVideo = null
        pickedVideos = emptyList()
        pickedImages = emptyList()
        project = null
        exportedImageBytes = null
        exportedVideoPath = null
    }
}
