package com.movexa.app.feature.collage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movexa.app.core.designsystem.GradientButton
import com.movexa.app.core.designsystem.MovexaColors
import com.movexa.app.core.designsystem.MovexaSecondaryButton
import com.movexa.app.core.designsystem.MovexaShapes
import com.movexa.app.core.designsystem.MovexaType
import com.movexa.app.core.designsystem.ToolIconView
import com.movexa.app.core.editing.EditorTool
import com.movexa.app.core.media.EditorSessionStore
import com.movexa.app.core.media.PickedMedia
import com.movexa.app.core.media.decodeImageBitmap
import com.movexa.app.core.media.encodeImageBitmapToJpeg
import com.movexa.app.core.media.saveImageToGallery
import com.movexa.app.core.media.shareImageFile
import kotlinx.coroutines.launch
import kotlin.random.Random

/** The collage templates. Each one is a grid of slots. */
private enum class CollageLayout(val label: String, val slots: Int) {
    SPLIT("2 photos", 2),
    ONE_PLUS_TWO("1 + 2", 3),
    STACK("3 photos", 3),
    GRID_2X2("4 photos", 4),
    GRID_3X3("9 photos", 9),
}

/** Longest edge of the flattened collage, in pixels. */
private const val COLLAGE_MAX_PX = 1080

/**
 * The collage builder.
 *
 * Takes the photos picked in the gallery, drops them into a layout template and
 * flattens the result into a JPEG. The flattened bytes are handed to
 * [EditorSessionStore.exportedImageBytes] so the rest of the app can pick them
 * up, and the screen offers to save or share them right away.
 */
@Composable
fun CollageScreen(onBack: () -> Unit, onExport: () -> Unit = {}) {
    val picked = remember { EditorSessionStore.pickedImages.take(9) }
    var photos by remember { mutableStateOf(picked) }
    var layout by remember { mutableStateOf(CollageLayout.GRID_2X2) }
    var selectedSlot by remember { mutableStateOf<Int?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var savedBytes by remember { mutableStateOf<ByteArray?>(null) }

    val graphicsLayer = rememberGraphicsLayer()
    val coroutineScope = rememberCoroutineScope()

    // Decoded once per picked photo; swapping only changes the order, so the
    // bitmaps are looked up by the photo they belong to.
    val decoded = remember(picked) {
        picked.map { item -> runCatching { decodeImageBitmap(item.bytes) }.getOrNull() }
    }

    fun bitmapAt(slotIndex: Int): ImageBitmap? {
        val item = photos.getOrNull(slotIndex) ?: return null
        val index = picked.indexOfFirst { it === item }
        return decoded.getOrNull(index)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MovexaColors.bgBase)
            .statusBarsPadding(),
    ) {
        CollageTopBar(
            onBack = onBack,
            photoCount = photos.size,
        )

        if (photos.isEmpty()) {
            EmptyCollageState(onBack = onBack)
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
                val capturePx = minOf(COLLAGE_MAX_PX, widthPx)
                val captureDp = with(density) { capturePx.toDp() }

                Box(
                    modifier = Modifier
                        .size(captureDp)
                        .clip(MovexaShapes.medium)
                        .background(MovexaColors.bgSurface)
                        .drawWithContent {
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(graphicsLayer)
                        },
                ) {
                    LayoutGrid(
                        layout = layout,
                        modifier = Modifier.fillMaxSize(),
                    ) { slotIndex, slotModifier ->
                        CollageSlot(
                            photo = bitmapAt(slotIndex),
                            slotIndex = slotIndex,
                            isSelected = selectedSlot == slotIndex,
                            onClick = {
                                if (selectedSlot == null) {
                                    selectedSlot = slotIndex
                                } else if (selectedSlot == slotIndex) {
                                    selectedSlot = null
                                } else {
                                    photos = photos.swap(selectedSlot!!, slotIndex)
                                    selectedSlot = null
                                }
                            },
                            modifier = slotModifier,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = if (selectedSlot == null) {
                    "Tap a photo, then tap another one to swap them."
                } else {
                    "Now tap the photo you want to swap it with."
                },
                style = MovexaType.label,
                color = if (selectedSlot == null) MovexaColors.textSecondary else MovexaColors.brandPrimary,
            )

            if (photos.size > layout.slots) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "This layout shows ${layout.slots} of your ${photos.size} photos.",
                    style = MovexaType.caption,
                    color = MovexaColors.accentPro,
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Layout",
                style = MovexaType.title,
                color = MovexaColors.textPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CollageLayout.entries.forEach { option ->
                    LayoutOption(
                        layout = option,
                        selected = option == layout,
                        onClick = {
                            layout = option
                            selectedSlot = null
                        },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        CollageBottomBar(
            isSaving = isSaving,
            hasSaved = savedBytes != null,
            statusMessage = statusMessage,
            statusIsError = statusIsError,
            onSave = {
                coroutineScope.launch {
                    isSaving = true
                    statusMessage = null
                    val bitmap = runCatching { graphicsLayer.toImageBitmap() }.getOrNull()
                    if (bitmap == null) {
                        isSaving = false
                        statusIsError = true
                        statusMessage = "Couldn't flatten the collage. Try again."
                    } else {
                        val bytes = encodeImageBitmapToJpeg(bitmap, 92)
                        EditorSessionStore.exportedImageBytes = bytes
                        savedBytes = bytes
                        isSaving = false
                        statusIsError = false
                        statusMessage = "Collage saved"
                        onExport()
                    }
                }
            },
            onSaveToDevice = {
                val bytes = savedBytes ?: return@CollageBottomBar
                saveImageToGallery(bytes, "snapzy_collage_${Random.nextInt(100000, 999999)}.jpg") { success ->
                    statusIsError = !success
                    statusMessage = if (success) "Saved to your gallery" else "Couldn't save to the gallery"
                }
            },
            onShare = {
                val bytes = savedBytes ?: return@CollageBottomBar
                shareImageFile(bytes, "snapzy_collage.jpg")
            },
        )
    }
}

/** Back chevron, title and the number of picked photos. */
@Composable
private fun CollageTopBar(onBack: () -> Unit, photoCount: Int) {
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
            text = "Collage",
            style = MovexaType.title,
            color = MovexaColors.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "$photoCount / 9 photos",
            style = MovexaType.label,
            color = MovexaColors.textSecondary,
        )
    }
}

/** Shown when the collage builder is opened without any photos. */
@Composable
private fun EmptyCollageState(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MovexaColors.bgSurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            ToolIconView(
                tool = EditorTool.COLLAGE,
                tint = MovexaColors.textSecondary,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No photos selected",
            style = MovexaType.title,
            color = MovexaColors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Pick up to 9 photos in the gallery and they will show up here.",
            style = MovexaType.body,
            color = MovexaColors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        GradientButton(text = "Back", onClick = onBack)
    }
}

/**
 * Renders a layout template.
 *
 * Every template is built from plain [Row]/[Column] containers with weights, so
 * no extra dependency is needed.
 */
@Composable
private fun LayoutGrid(
    layout: CollageLayout,
    modifier: Modifier = Modifier,
    slot: @Composable (Int, Modifier) -> Unit,
) {
    when (layout) {
        CollageLayout.SPLIT -> Row(modifier = modifier) {
            slot(0, Modifier.weight(1f).fillMaxHeight())
            slot(1, Modifier.weight(1f).fillMaxHeight())
        }

        CollageLayout.STACK -> Column(modifier = modifier) {
            slot(0, Modifier.weight(1f).fillMaxWidth())
            slot(1, Modifier.weight(1f).fillMaxWidth())
            slot(2, Modifier.weight(1f).fillMaxWidth())
        }

        CollageLayout.ONE_PLUS_TWO -> Column(modifier = modifier) {
            slot(0, Modifier.weight(2f).fillMaxWidth())
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                slot(1, Modifier.weight(1f).fillMaxHeight())
                slot(2, Modifier.weight(1f).fillMaxHeight())
            }
        }

        CollageLayout.GRID_2X2 -> Column(modifier = modifier) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                slot(0, Modifier.weight(1f).fillMaxHeight())
                slot(1, Modifier.weight(1f).fillMaxHeight())
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                slot(2, Modifier.weight(1f).fillMaxHeight())
                slot(3, Modifier.weight(1f).fillMaxHeight())
            }
        }

        CollageLayout.GRID_3X3 -> Column(modifier = modifier) {
            repeat(3) { row ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    repeat(3) { column ->
                        slot(row * 3 + column, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

/** One photo slot of the collage. */
@Composable
private fun CollageSlot(
    photo: ImageBitmap?,
    slotIndex: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(MovexaColors.bgBase)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, MovexaColors.brandPrimary)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
            )
        } else {
            Text(
                text = "${slotIndex + 1}",
                style = MovexaType.label,
                color = MovexaColors.textSecondary,
            )
        }
    }
}

/** A layout template in the picker row. */
@Composable
private fun LayoutOption(
    layout: CollageLayout,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(MovexaShapes.medium)
            .background(MovexaColors.bgSurface)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MovexaColors.brandPrimary, MovexaShapes.medium)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(MovexaShapes.small)
                .background(MovexaColors.bgSurfaceElevated),
        ) {
            LayoutGrid(
                layout = layout,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
            ) { slotIndex, slotModifier ->
                Box(
                    modifier = slotModifier
                        .padding(1.dp)
                        .background(MovexaColors.bgBase),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${slotIndex + 1}",
                        style = MovexaType.caption,
                        color = MovexaColors.textSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = layout.label,
            style = MovexaType.caption,
            color = if (selected) MovexaColors.brandPrimary else MovexaColors.textSecondary,
        )
    }
}

/** The bottom bar: flatten the collage, then save or share it. */
@Composable
private fun CollageBottomBar(
    isSaving: Boolean,
    hasSaved: Boolean,
    statusMessage: String?,
    statusIsError: Boolean,
    onSave: () -> Unit,
    onSaveToDevice: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MovexaColors.bgSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        statusMessage?.let { text ->
            Text(
                text = text,
                style = MovexaType.label,
                color = if (statusIsError) MovexaColors.accentDanger else MovexaColors.accentSuccess,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientButton(
                text = if (isSaving) "Flattening…" else "Save collage",
                modifier = Modifier.weight(1f),
                enabled = !isSaving,
                onClick = onSave,
            )
            if (hasSaved) {
                MovexaSecondaryButton(
                    text = "Share",
                    onClick = onShare,
                )
            }
        }
        if (hasSaved) {
            Spacer(Modifier.height(8.dp))
            MovexaSecondaryButton(
                text = "Save to device",
                modifier = Modifier.fillMaxWidth(),
                onClick = onSaveToDevice,
            )
        }
    }
}

/** Swaps two photos in the list. */
private fun List<PickedMedia.Image>.swap(first: Int, second: Int): List<PickedMedia.Image> {
    val copy = toMutableList()
    if (first !in copy.indices || second !in copy.indices) return copy
    val held = copy[first]
    copy[first] = copy[second]
    copy[second] = held
    return copy
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
