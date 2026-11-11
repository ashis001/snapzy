package com.movexa.app.feature.picker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movexa.app.core.designsystem.GradientButton
import com.movexa.app.core.designsystem.TabStrip
import com.movexa.app.core.designsystem.MovexaColors
import com.movexa.app.core.designsystem.MovexaSecondaryButton
import com.movexa.app.core.designsystem.MovexaShapes
import com.movexa.app.core.designsystem.MovexaType
import com.movexa.app.core.media.DeviceMedia
import com.movexa.app.core.media.EditorSessionStore
import com.movexa.app.core.media.MediaType
import com.movexa.app.core.media.PickedMedia
import com.movexa.app.core.media.copyToCache
import com.movexa.app.core.media.encodeImageBitmapToJpeg
import com.movexa.app.core.media.loadImageBytes
import com.movexa.app.core.media.loadThumbnail
import com.movexa.app.core.media.queryDeviceMedia
import com.movexa.app.core.media.rememberImagePicker
import com.movexa.app.core.media.rememberMediaPermission
import com.movexa.app.core.media.rememberVideoPicker
import kotlinx.coroutines.launch

/** What the picker is being used for. Decides the selection limit and what can be picked. */
private enum class PickerKind(val maxSelection: Int, val selectableType: MediaType, val ctaLabel: String) {
    VIDEO(9, MediaType.VIDEO, "Next"),
    PHOTO(1, MediaType.IMAGE, "Next"),
    COLLAGE(9, MediaType.IMAGE, "Add"),
    ;

    companion object {
        fun from(mediaKind: String): PickerKind = when (mediaKind) {
            "video" -> VIDEO
            "collage" -> COLLAGE
            else -> PHOTO
        }
    }
}

/** The two gallery tabs. */
private enum class PickerTab(val label: String, val type: MediaType) {
    VIDEO("Videos", MediaType.VIDEO),
    PHOTO("Photos", MediaType.IMAGE),
}

/** Size used when a single picked photo is re-encoded for the photo editor. */
private const val PHOTO_IMPORT_WIDTH_PX = 2048
private const val PHOTO_IMPORT_HEIGHT_PX = 1536

/** Size used for collage photos — a collage holds up to nine of them at once. */
private const val COLLAGE_PHOTO_WIDTH_PX = 1280
private const val COLLAGE_PHOTO_HEIGHT_PX = 960

/**
 * The device gallery.
 *
 * Reads the real gallery through [queryDeviceMedia] (MediaStore on Android,
 * PhotoKit on iOS) and lets the user pick from it. "Browse device" falls back
 * to the OS document picker when the gallery is empty or the permission was
 * refused.
 */
@Composable
fun MediaPickerScreen(
    mediaKind: String,
    onBack: () -> Unit,
    onMediaSelected: () -> Unit,
) {
    val kind = remember(mediaKind) { PickerKind.from(mediaKind) }
    var tabIndex by remember { mutableStateOf(if (kind == PickerKind.VIDEO) 0 else 1) }
    val activeTab = if (tabIndex == 0) PickerTab.VIDEO else PickerTab.PHOTO

    var videoItems by remember { mutableStateOf(emptyList<DeviceMedia>()) }
    var photoItems by remember { mutableStateOf(emptyList<DeviceMedia>()) }
    var videoLoaded by remember { mutableStateOf(false) }
    var photoLoaded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isPreparing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val selected = remember { mutableStateListOf<DeviceMedia>() }
    val thumbnails = remember { mutableStateMapOf<String, ImageBitmap>() }
    val attempted = remember { mutableStateListOf<String>() }
    val coroutineScope = rememberCoroutineScope()

    val (isGranted, requestPermission) = rememberMediaPermission()

    // Ask for gallery access the first time the screen appears.
    LaunchedEffect(Unit) {
        if (!isGranted) requestPermission()
    }

    // Load the gallery for the visible tab once access has been granted.
    LaunchedEffect(isGranted, tabIndex) {
        if (!isGranted) return@LaunchedEffect
        val type = if (tabIndex == 0) MediaType.VIDEO else MediaType.IMAGE
        val alreadyLoaded = if (type == MediaType.VIDEO) videoLoaded else photoLoaded
        if (alreadyLoaded) return@LaunchedEffect

        isLoading = true
        val items = queryDeviceMedia(type, 300)
        if (type == MediaType.VIDEO) {
            videoItems = items
            videoLoaded = true
        } else {
            photoItems = items
            photoLoaded = true
        }
        isLoading = false
    }

    // The OS picker fallback: it returns the original bytes / a local file.
    val launchImagePicker = rememberImagePicker { image ->
        if (image == null) return@rememberImagePicker
        EditorSessionStore.pickedImage = image
        EditorSessionStore.pickedImages = listOf(image)
        onMediaSelected()
    }
    val launchVideoPicker = rememberVideoPicker { video ->
        if (video == null) return@rememberVideoPicker
        EditorSessionStore.pickedVideo = video
        EditorSessionStore.pickedVideos = listOf(video)
        onMediaSelected()
    }

    val browseDevice = {
        if (kind == PickerKind.VIDEO) launchVideoPicker() else launchImagePicker()
    }

    val items = if (activeTab == PickerTab.VIDEO) videoItems else photoItems
    val canSelectHere = activeTab.type == kind.selectableType

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MovexaColors.bgBase)
            .statusBarsPadding(),
    ) {
        PickerTopBar(
            title = activeTab.label,
            onBack = onBack,
            onBrowseDevice = browseDevice,
        )

        // InShot puts a thin, right-aligned tab strip under its top bar.
        TabStrip(
            options = listOf(PickerTab.VIDEO.label, PickerTab.PHOTO.label),
            selectedIndex = tabIndex,
            onSelect = { index ->
                tabIndex = index
                message = null
            },
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading -> CenteredMessage(
                    title = "Loading your gallery…",
                    subtitle = "",
                    showSpinner = true,
                )

                !isGranted -> CenteredMessage(
                    title = "Allow access to your gallery",
                    subtitle = "Movexa needs media access to show the photos and videos on this device.",
                    actionLabel = "Grant access",
                    onAction = { requestPermission() },
                    secondaryActionLabel = "Browse device",
                    onSecondaryAction = browseDevice,
                )

                items.isEmpty() -> CenteredMessage(
                    title = if (activeTab == PickerTab.VIDEO) "No videos yet" else "No photos yet",
                    subtitle = "Nothing in this album yet — pick a file from your device instead.",
                    actionLabel = "Browse device",
                    onAction = browseDevice,
                )

                else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val cellPx = with(density) { (maxWidth / 4f).toPx() }.toInt().coerceAtLeast(48)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(items = items, key = { it.id }) { item ->
                            GalleryCell(
                                item = item,
                                thumbnails = thumbnails,
                                attempted = attempted,
                                cellPx = cellPx,
                                selectionNumber = selected.indexOfFirst { it.id == item.id } + 1,
                                isSelected = selected.any { it.id == item.id },
                                onClick = {
                                    if (!canSelectHere) {
                                        message = if (kind == PickerKind.VIDEO) {
                                            "Video projects use clips — pick from the Videos tab."
                                        } else {
                                            "This project uses photos — pick from the Photos tab."
                                        }
                                    } else {
                                        message = null
                                        toggleSelection(selected, item, kind)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        message?.let { text ->
            Text(
                text = text,
                style = MovexaType.label,
                color = MovexaColors.accentPro,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        PickerBottomBar(
            selectedCount = selected.size,
            maxSelection = kind.maxSelection,
            canSelectHere = canSelectHere,
            isPreparing = isPreparing,
            ctaLabel = kind.ctaLabel,
            onContinue = {
                coroutineScope.launch {
                    isPreparing = true
                    message = null
                    val ok = when (kind) {
                        PickerKind.VIDEO -> importVideos(selected, videoItems)
                        PickerKind.PHOTO -> importPhotos(
                            selected = selected,
                            allPhotos = photoItems,
                            widthPx = PHOTO_IMPORT_WIDTH_PX,
                            heightPx = PHOTO_IMPORT_HEIGHT_PX,
                            single = true,
                        )
                        PickerKind.COLLAGE -> importPhotos(
                            selected = selected,
                            allPhotos = photoItems,
                            widthPx = COLLAGE_PHOTO_WIDTH_PX,
                            heightPx = COLLAGE_PHOTO_HEIGHT_PX,
                            single = false,
                        )
                    }
                    isPreparing = false
                    if (ok) {
                        onMediaSelected()
                    } else {
                        message = "Couldn't import the selected media. Try again."
                    }
                }
            },
        )
    }
}

/** Adds or removes [item] from [selected], honouring the session's selection limit. */
private fun toggleSelection(
    selected: MutableList<DeviceMedia>,
    item: DeviceMedia,
    kind: PickerKind,
) {
    val index = selected.indexOfFirst { it.id == item.id }
    if (index >= 0) {
        selected.removeAt(index)
        return
    }
    if (selected.size >= kind.maxSelection) {
        return
    }
    selected.add(item)
}

/**
 * Copies every selected clip into the app cache and hands the paths to the
 * editor through [EditorSessionStore].
 */
private suspend fun importVideos(
    selected: List<DeviceMedia>,
    allVideos: List<DeviceMedia>,
): Boolean {
    val picked = mutableListOf<PickedMedia.Video>()
    for (item in selected) {
        val media = allVideos.firstOrNull { it.id == item.id } ?: item
        val path = copyToCache(media.uri, media.displayName) ?: continue
        picked += PickedMedia.Video(filePath = path, durationMs = media.durationMs)
    }
    if (picked.isEmpty()) return false
    EditorSessionStore.pickedVideos = picked
    EditorSessionStore.pickedVideo = picked.first()
    return true
}

/**
 * Decodes the selected photos and hands the bytes to the editor.
 *
 * A single photo also fills [EditorSessionStore.pickedImage] so the photo
 * editor can open it directly.
 */
private suspend fun importPhotos(
    selected: List<DeviceMedia>,
    allPhotos: List<DeviceMedia>,
    widthPx: Int,
    heightPx: Int,
    single: Boolean,
): Boolean {
    val picked = mutableListOf<PickedMedia.Image>()
    for (item in selected) {
        val media = allPhotos.firstOrNull { it.id == item.id } ?: item
        val bytes = loadPhotoBytes(media.uri, widthPx, heightPx) ?: continue
        picked += PickedMedia.Image(bytes)
    }
    if (picked.isEmpty()) return false
    EditorSessionStore.pickedImages = picked
    if (single) {
        EditorSessionStore.pickedImage = picked.first()
    }
    return true
}

/**
 * Loads the original bytes of a gallery photo.
 *
 * The original file is read rather than a thumbnail: a thumbnail is
 * centre-cropped to the cell size, which would silently crop the user's photo
 * before it ever reaches the editor.
 */
private suspend fun loadPhotoBytes(uri: String, widthPx: Int, heightPx: Int): ByteArray? {
    loadImageBytes(uri)?.let { return it }
    // Fall back to the thumbnail path if the original cannot be read.
    val bitmap = loadThumbnail(uri, widthPx, heightPx) ?: return null
    return encodeImageBitmapToJpeg(bitmap, 95)
}

/** Back arrow, screen title and the "Browse device" fallback. */
@Composable
private fun PickerTopBar(
    title: String,
    onBack: () -> Unit,
    onBrowseDevice: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            BackChevron(tint = MovexaColors.textPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            style = MovexaType.title,
            color = MovexaColors.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "Browse device",
            style = MovexaType.label,
            color = MovexaColors.brandPrimary,
            modifier = Modifier
                .clip(MovexaShapes.small)
                .clickable(onClick = onBrowseDevice)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

/** One gallery cell: thumbnail, duration badge and selection badge. */
@Composable
private fun GalleryCell(
    item: DeviceMedia,
    thumbnails: SnapshotStateMap<String, ImageBitmap>,
    attempted: MutableList<String>,
    cellPx: Int,
    selectionNumber: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bitmap = thumbnails[item.uri]

    LaunchedEffect(item.uri) {
        if (!attempted.add(item.uri)) return@LaunchedEffect
        val loaded = loadThumbnail(item.uri, cellPx, cellPx)
        if (loaded != null) thumbnails[item.uri] = loaded
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(MovexaColors.bgSurfaceElevated)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MovexaColors.brandPrimary)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MovexaColors.bgSurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (item.type == MediaType.VIDEO) "▶" else "✦",
                    style = MovexaType.label,
                    color = MovexaColors.textSecondary,
                )
            }
        }

        if (item.type == MediaType.VIDEO && item.durationMs > 0L) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(MovexaShapes.pill)
                    .background(MovexaColors.overlayScrim)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = formatDuration(item.durationMs),
                    style = MovexaType.caption,
                    color = MovexaColors.textPrimary,
                )
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MovexaColors.brandPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = selectionNumber.toString(),
                    style = MovexaType.caption,
                    color = MovexaColors.bgBase,
                )
            }
        }
    }
}

/** Selection summary and the primary action. */
@Composable
private fun PickerBottomBar(
    selectedCount: Int,
    maxSelection: Int,
    canSelectHere: Boolean,
    isPreparing: Boolean,
    ctaLabel: String,
    onContinue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MovexaColors.bgSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    selectedCount > 0 -> "$selectedCount selected"
                    !canSelectHere -> "Nothing to select here"
                    else -> "Select media"
                },
                style = MovexaType.body,
                color = MovexaColors.textPrimary,
            )
            Text(
                text = when {
                    selectedCount == 0 && !canSelectHere -> "Switch tabs to pick the right kind of media"
                    else -> "Up to $maxSelection"
                },
                style = MovexaType.caption,
                color = MovexaColors.textSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        GradientButton(
            text = if (isPreparing) "Preparing…" else ctaLabel,
            enabled = selectedCount > 0 && !isPreparing,
            onClick = onContinue,
        )
    }
}

/** Empty / permission / loading state. */
@Composable
private fun CenteredMessage(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    showSpinner: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showSpinner) {
            CircularProgressIndicator(color = MovexaColors.brandPrimary)
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = title,
            style = MovexaType.title,
            color = MovexaColors.textPrimary,
            textAlign = TextAlign.Center,
        )
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MovexaType.body,
                color = MovexaColors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            GradientButton(text = actionLabel, onClick = onAction)
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Spacer(Modifier.height(10.dp))
            MovexaSecondaryButton(text = secondaryActionLabel, onClick = onSecondaryAction)
        }
    }
}

/** "m:ss" duration badge text. */
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/** The back chevron, drawn with Compose primitives. */
@Composable
private fun BackChevron(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.64f, size.height * 0.18f)
            lineTo(size.width * 0.28f, size.height * 0.5f)
            lineTo(size.width * 0.64f, size.height * 0.82f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(
                width = 2f,
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}
