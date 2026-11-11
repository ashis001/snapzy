package com.snapzy.app.core.storage

import com.snapzy.app.core.editing.EditKind
import com.snapzy.app.core.editing.EditProject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * The shared, file backed draft store.
 *
 * One JSON file per draft plus a small index that holds the metadata the
 * home screen needs, so listing drafts never has to read (or decode) the
 * media a draft points at.
 *
 * Nothing here throws on bad data: a draft whose file cannot be parsed is
 * skipped, and a missing or corrupt index is rebuilt from the files on disk.
 */
internal class FileDraftStore(private val files: DraftFiles) : DraftStore {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun list(): List<DraftSummary> {
        if (!hasStorage()) return emptyList()

        val onDisk = files.listJsonFiles()
            .filter { it != INDEX_FILE_NAME && it.endsWith(DRAFT_EXTENSION) }
            .map { it.removeSuffix(DRAFT_EXTENSION) }
            .toSet()

        val indexed = readIndex().associateBy { it.id }
        val summaries = mutableListOf<DraftSummary>()
        for (id in onDisk) {
            val entry = indexed[id]
            if (entry != null) {
                summaries += entry.toSummary()
            } else {
                // Written by a build that stopped before the index was updated.
                val snapshot = readSnapshot(id) ?: continue
                summaries += snapshot.toSummary()
            }
        }

        val ordered = summaries.sortedByDescending { it.updatedAt }
        if (ordered.size != indexed.size) writeIndex(ordered)
        return ordered
    }

    override suspend fun save(project: EditProject, title: String, id: String?): String {
        val draftId = id?.takeIf { isSafeDraftId(it) } ?: newDraftId()
        if (!hasStorage()) return draftId

        files.ensureDirectory()
        val now = currentTimeMillis()
        val existing = readSnapshot(draftId)
        val snapshot = project.toSnapshot(
            id = draftId,
            title = title,
            createdAt = existing?.createdAt ?: now,
        ).copy(updatedAt = now)

        val text = json.encodeToString(DraftSnapshot.serializer(), snapshot)
        files.writeTextAtomically(snapshotPath(draftId), text)

        val summaries = (list() + snapshot.toSummary())
            .associateBy { it.id }
            .values
            .sortedByDescending { it.updatedAt }
        writeIndex(summaries)

        return draftId
    }

    override suspend fun load(id: String): EditProject? = readSnapshot(id)?.toProject()

    override suspend fun delete(id: String) {
        files.deleteFile(snapshotPath(id))
        writeIndex(list().filter { it.id != id })
    }

    override suspend fun thumbnail(id: String): String? =
        readSnapshot(id)?.thumbnailPath?.takeIf { it.isNotBlank() }

    /* ------------------------------------------------------------- reading -- */

    private suspend fun readSnapshot(id: String): DraftSnapshot? {
        if (!isSafeDraftId(id)) return null
        val text = files.readText(snapshotPath(id)) ?: return null
        return runCatching { json.decodeFromString(DraftSnapshot.serializer(), text) }.getOrNull()
    }

    private suspend fun readIndex(): List<DraftIndexEntry> {
        val text = files.readText(indexPath()) ?: return emptyList()
        val index = runCatching { json.decodeFromString(DraftIndex.serializer(), text) }.getOrNull()
        return index?.entries.orEmpty()
    }

    /* ------------------------------------------------------------- writing -- */

    private suspend fun writeIndex(summaries: List<DraftSummary>) {
        val index = DraftIndex(entries = summaries.map { it.toIndexEntry() })
        val text = json.encodeToString(DraftIndex.serializer(), index)
        files.writeTextAtomically(indexPath(), text)
    }

    /* --------------------------------------------------------------- paths -- */

    /**
     * Whether there is anywhere to keep drafts.
     *
     * The platform can be out of reach — Android has no context yet, say —
     * and then the store is simply empty rather than half working.
     */
    private fun hasStorage(): Boolean = files.draftsDirectory().isNotEmpty()

    private fun snapshotPath(id: String): String = files.draftsDirectory() + id + DRAFT_EXTENSION

    private fun indexPath(): String = files.draftsDirectory() + INDEX_FILE_NAME

    private companion object {
        const val DRAFT_EXTENSION = ".json"

        /** The index lives next to the drafts; it is not one of them. */
        const val INDEX_FILE_NAME = "drafts.json"
    }
}

/** The metadata of every draft, kept aside so listing stays cheap. */
@Serializable
internal data class DraftIndex(
    val entries: List<DraftIndexEntry> = emptyList(),
)

/** One draft's metadata. */
@Serializable
internal data class DraftIndexEntry(
    val id: String,
    val title: String = "",
    val updatedAt: Long = 0L,
    val durationMs: Long = 0L,
    /** [EditKind] by name. */
    val kind: String = "VIDEO",
    val thumbnailPath: String? = null,
)

/** The metadata of one draft, as the home screen sees it. */
private fun DraftIndexEntry.toSummary(): DraftSummary = DraftSummary(
    id = id,
    title = title,
    updatedAt = updatedAt,
    durationMs = durationMs,
    kind = enumByName<EditKind>(kind),
    thumbnailPath = thumbnailPath,
)

/** The metadata of one draft, as the index stores it. */
private fun DraftSummary.toIndexEntry(): DraftIndexEntry = DraftIndexEntry(
    id = id,
    title = title,
    updatedAt = updatedAt,
    durationMs = durationMs,
    kind = kind.name,
    thumbnailPath = thumbnailPath,
)

/** The metadata of one draft, read back from the draft file itself. */
private fun DraftSnapshot.toSummary(): DraftSummary = DraftSummary(
    id = id,
    title = title,
    updatedAt = updatedAt,
    durationMs = durationMs,
    kind = enumByName<EditKind>(kind),
    thumbnailPath = thumbnailPath,
)

/**
 * A fresh draft id.
 *
 * Ids end up in file names, so they are kept to hex characters.
 */
private fun newDraftId(): String {
    val random = Random.nextBytes(6)
        .joinToString("") { it.toUByte().toInt().toString(16).padStart(2, '0') }
    return currentTimeMillis().toString(16) + random
}

/** Whether [id] is safe to use as a file name. */
private fun isSafeDraftId(id: String): Boolean =
    id.isNotEmpty() && id.length <= 64 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
