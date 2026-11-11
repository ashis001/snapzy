package com.movexa.app.feature.export

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movexa.app.core.designsystem.GradientButton
import com.movexa.app.core.designsystem.OptionChip
import com.movexa.app.core.designsystem.MovexaColors
import com.movexa.app.core.designsystem.MovexaSecondaryButton
import com.movexa.app.core.designsystem.MovexaShapes
import com.movexa.app.core.designsystem.MovexaType
import com.movexa.app.core.designsystem.ToolIconView
import com.movexa.app.core.editing.EditorTool
import com.movexa.app.core.editing.EditKind
import com.movexa.app.core.media.EditorSessionStore
import com.movexa.app.core.media.ExportSettings
import com.movexa.app.core.media.decodeImageBitmap
import com.movexa.app.core.media.renderVideoProject
import com.movexa.app.core.media.saveImageToGallery
import com.movexa.app.core.media.saveVideoToGallery
import com.movexa.app.core.media.shareImageFile
import com.movexa.app.core.media.shareVideoFile
import kotlinx.coroutines.launch
import kotlin.random.Random

/** The resolution presets offered on the export screen. */
private data class ResolutionOption(val label: String, val settings: ExportSettings)

private val resolutionOptions = listOf(
    ResolutionOption("720p", ExportSettings.P720),
    ResolutionOption("1080p", ExportSettings.P1080),
    ResolutionOption("4K", ExportSettings.P4K),
)

/**
 * The export screen.
 *
 * Shows what the editor produced, lets the user pick the output resolution and
 * saves or shares the result. When the editor handed its project over
 * ([EditorSessionStore.project]) the resolution change re-renders the project
 * locally, so the choice actually affects the file.
 */
@Composable
fun ExportScreen(onBack: () -> Unit) {
    val imageBytes = EditorSessionStore.exportedImageBytes
    val videoPath = EditorSessionStore.exportedVideoPath
    val project = EditorSessionStore.project

    var settings by remember { mutableStateOf(resolutionOptions[1]) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var isRendering by remember { mutableStateOf(false) }
    var renderProgress by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    val imageBitmap = remember(imageBytes) {
        imageBytes?.let { bytes -> runCatching { decodeImageBitmap(bytes) }.getOrNull() }
    }

    // The editor hands its project over; render it once so there is something
    // to preview, save and share. The player-only preview in the editor does
    // not produce a file — this is where the real render happens.
    //
    // Photo projects are already flattened to JPEG by the editor, so they are
    // not rendered again here.
    LaunchedEffect(project) {
        val target = project ?: return@LaunchedEffect
        // A photo project is already flattened to JPEG by the editor, and its
        // single "still clip" has no file behind it, so it is never rendered.
        if (target.kind != EditKind.VIDEO) return@LaunchedEffect
        if (target.clips.isEmpty()) return@LaunchedEffect
        if (EditorSessionStore.exportedVideoPath != null) return@LaunchedEffect
        isRendering = true
        renderProgress = 0f
        val rendered = renderVideoProject(target, settings.settings) { progress ->
            renderProgress = progress
        }
        isRendering = false
        if (rendered != null) {
            EditorSessionStore.exportedVideoPath = rendered
            statusIsError = false
            statusMessage = "Rendered at ${settings.label}"
        } else {
            statusIsError = true
            statusMessage = "Couldn't render this edit"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MovexaColors.bgBase)
            .statusBarsPadding(),
    ) {
        ExportTopBar(onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            ExportPreview(
                imageBitmap = imageBitmap,
                videoPath = videoPath,
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Resolution",
                style = MovexaType.title,
                color = MovexaColors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (project != null) {
                    "Changing the resolution re-renders your project."
                } else {
                    "Applied the next time this project is rendered."
                },
                style = MovexaType.label,
                color = MovexaColors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                resolutionOptions.forEach { option ->
                    OptionChip(
                        label = option.label,
                        selected = settings.settings == option.settings,
                        onClick = {
                            settings = option
                            statusMessage = null
                            if (project != null && project.kind == EditKind.VIDEO && project.clips.isNotEmpty()) {
                                coroutineScope.launch {
                                    isRendering = true
                                    renderProgress = 0f
                                    val rendered = renderVideoProject(project, option.settings) { progress ->
                                        renderProgress = progress
                                    }
                                    isRendering = false
                                    if (rendered != null) {
                                        EditorSessionStore.exportedVideoPath = rendered
                                        statusIsError = false
                                        statusMessage = "Re-rendered at ${option.label}"
                                    } else {
                                        statusIsError = true
                                        statusMessage = "Could not re-render at ${option.label}"
                                    }
                                }
                            }
                        },
                    )
                }
            }

            if (isRendering) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = MovexaColors.brandPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Rendering… ${progressPercent(renderProgress)}%",
                        style = MovexaType.label,
                        color = MovexaColors.textSecondary,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        ExportActionBar(
            hasContent = imageBytes != null || videoPath != null,
            statusMessage = statusMessage,
            statusIsError = statusIsError,
            onShare = {
                statusMessage = null
                imageBytes?.let { shareImageFile(it, exportFileName("jpg")) }
                videoPath?.let { shareVideoFile(it) }
            },
            onSave = {
                statusMessage = null
                imageBytes?.let { bytes ->
                    saveImageToGallery(bytes, exportFileName("jpg")) { success ->
                        statusIsError = !success
                        statusMessage = if (success) "Saved to your gallery" else "Couldn't save to the gallery"
                    }
                }
                videoPath?.let { path ->
                    saveVideoToGallery(path, exportFileName("mp4")) { success ->
                        statusIsError = !success
                        statusMessage = if (success) "Saved to your gallery" else "Couldn't save to the gallery"
                    }
                }
            },
            onBackToEditor = onBack,
        )
    }
}

/** Back chevron and the screen title. */
@Composable
private fun ExportTopBar(onBack: () -> Unit) {
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
            text = "Export",
            style = MovexaType.title,
            color = MovexaColors.textPrimary,
        )
    }
}

/**
 * The result preview: the flattened image, a summary card for a rendered video,
 * or an empty state when nothing has been exported yet.
 */
@Composable
private fun ExportPreview(
    imageBitmap: ImageBitmap?,
    videoPath: String?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MovexaShapes.medium)
            .background(MovexaColors.bgSurface)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            imageBitmap != null -> Image(
                bitmap = imageBitmap,
                contentDescription = "Exported image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            )

            videoPath != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 28.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MovexaColors.brandPrimary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    ToolIconView(
                        tool = EditorTool.ANIMATION,
                        tint = MovexaColors.brandPrimary,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Video ready",
                    style = MovexaType.title,
                    color = MovexaColors.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = fileNameOf(videoPath),
                    style = MovexaType.label,
                    color = MovexaColors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 28.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MovexaColors.bgSurfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    ToolIconView(
                        tool = EditorTool.EXPORT,
                        tint = MovexaColors.textSecondary,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Nothing to export yet",
                    style = MovexaType.title,
                    color = MovexaColors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Go back to the editor and export your edit first.",
                    style = MovexaType.body,
                    color = MovexaColors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Share / save actions and the way back to the editor. */
@Composable
private fun ExportActionBar(
    hasContent: Boolean,
    statusMessage: String?,
    statusIsError: Boolean,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onBackToEditor: () -> Unit,
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
        ) {
            MovexaSecondaryButton(
                text = "Share",
                modifier = Modifier.weight(1f),
                onClick = onShare,
            )
            GradientButton(
                text = "Save to device",
                modifier = Modifier.weight(1f),
                enabled = hasContent,
                onClick = onSave,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "‹ Back to editor",
            style = MovexaType.label,
            color = MovexaColors.brandPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBackToEditor)
                .padding(vertical = 8.dp),
        )
    }
}

/** A unique-enough file name for the gallery. */
private fun exportFileName(extension: String): String =
    "snapzy_${Random.nextInt(100000, 999999)}.$extension"

/**
 * Normalises a render progress value to a percentage.
 *
 * The render pipeline reports 0..100, but a 0..1 fraction is accepted too so
 * the label never shows "0%" for a finished render.
 */
private fun progressPercent(raw: Float): Int {
    val percent = if (raw <= 1f) raw * 100f else raw
    return percent.toInt().coerceIn(0, 100)
}

/** The file name of a path, without the directories. */
private fun fileNameOf(path: String): String =
    path.substringAfterLast('/').substringAfterLast('\\').ifEmpty { path }

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
