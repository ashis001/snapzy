package com.snapzy.app.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.snapzy.app.core.comingsoon.GatedFeature
import com.snapzy.app.core.designsystem.CircleIconButton
import com.snapzy.app.core.designsystem.ComingSoonTile
import com.snapzy.app.core.designsystem.GradientFab
import com.snapzy.app.core.designsystem.SnapzyColors
import com.snapzy.app.core.designsystem.SnapzyPrimaryButton
import com.snapzy.app.core.designsystem.SnapzySecondaryButton
import com.snapzy.app.core.designsystem.SnapzyShapes
import com.snapzy.app.core.designsystem.SnapzyType
import com.snapzy.app.core.designsystem.StatusChip
import com.snapzy.app.core.designsystem.ToolIconView
import com.snapzy.app.core.editing.EditProject
import com.snapzy.app.core.editing.EditorTool
import com.snapzy.app.core.media.loadThumbnail
import com.snapzy.app.core.storage.DraftSummary
import com.snapzy.app.core.storage.createDraftStore
import com.snapzy.app.core.storage.currentTimeMillis
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Snapzy springboard.
 *
 * Mirrors InShot's home layout: a wordmark header, a "Projects" area, a grid of
 * large create tiles and a rounded bottom bar. Everything that needs a backend
 * (templates, AI tools, the store, Discover, Account) opens the shared Coming
 * Soon sheet through [onGatedFeature].
 *
 * The "Projects" area lists the saved drafts; tapping one hands the edit back
 * through [onOpenDraft] so it can be reopened in the editor.
 */
@Composable
fun HomeScreen(
    onCreateVideo: () -> Unit,
    onCreatePhoto: () -> Unit,
    onCreateCollage: () -> Unit,
    onGatedFeature: (GatedFeature) -> Unit,
    onOpenDraft: (EditProject) -> Unit = {},
) {
    var showCreateSheet by remember { mutableStateOf(false) }

    val draftStore = remember { createDraftStore() }
    val scope = rememberCoroutineScope()
    var drafts by remember { mutableStateOf<List<DraftSummary>>(emptyList()) }

    // The list is reloaded every time the screen comes back into view, so a
    // draft saved in the editor appears as soon as the user returns.
    LifecycleResumeEffect(draftStore) {
        scope.launch { drafts = draftStore.list() }
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SnapzyColors.bgBase)
            .statusBarsPadding(),
    ) {
        HomeHeader(
            onOpenPro = { onGatedFeature(GatedFeature.GO_PRO) },
            onOpenSettings = { onGatedFeature(GatedFeature.ACCOUNT) },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            SectionHeader(title = "Projects", subtitle = "Pick up where you left off")
            if (drafts.isEmpty()) {
                ProjectsSection(
                    onCreateVideo = onCreateVideo,
                    onCreatePhoto = onCreatePhoto,
                )
            } else {
                DraftRow(
                    drafts = drafts,
                    onOpen = { id ->
                        scope.launch {
                            val project = draftStore.load(id)
                            if (project != null) onOpenDraft(project)
                        }
                    },
                )
            }

            Spacer(Modifier.height(22.dp))

            SectionHeader(title = "Create new", subtitle = "Start something new")
            CreateCard(
                onCreateVideo = onCreateVideo,
                onCreatePhoto = onCreatePhoto,
                onCreateCollage = onCreateCollage,
            )

            Spacer(Modifier.height(14.dp))

            SecondaryActions(
                onTemplates = { onGatedFeature(GatedFeature.TEMPLATES) },
                onAiTools = { onGatedFeature(GatedFeature.AI_ENHANCE) },
                onStore = { onGatedFeature(GatedFeature.MARKETPLACE) },
            )

            Spacer(Modifier.height(22.dp))

            SectionHeader(title = "On the roadmap", subtitle = "Coming to Snapzy")
            ComingSoonTile(
                title = "AI Cut",
                subtitle = "Remove silences and filler words",
                modifier = Modifier.padding(bottom = 8.dp),
                onClick = { onGatedFeature(GatedFeature.AI_CUT) },
            )
            ComingSoonTile(
                title = "Auto Captions",
                subtitle = "Speech to text, timed for you",
                modifier = Modifier.padding(bottom = 8.dp),
                onClick = { onGatedFeature(GatedFeature.AUTO_CAPTIONS) },
            )
            ComingSoonTile(
                title = "AI Voiceover",
                subtitle = "Turn text into narration",
                onClick = { onGatedFeature(GatedFeature.VOICEOVER) },
            )

            Spacer(Modifier.height(24.dp))
        }

        HomeBottomBar(
            onDiscover = { onGatedFeature(GatedFeature.DISCOVER) },
            onSettings = { onGatedFeature(GatedFeature.ACCOUNT) },
            onCreate = { showCreateSheet = true },
        )
    }

    if (showCreateSheet) {
        CreateSheet(
            onDismiss = { showCreateSheet = false },
            onNewVideo = {
                showCreateSheet = false
                onCreateVideo()
            },
            onNewPhoto = {
                showCreateSheet = false
                onCreatePhoto()
            },
            onCollage = {
                showCreateSheet = false
                onCreateCollage()
            },
            onTemplates = {
                showCreateSheet = false
                onGatedFeature(GatedFeature.TEMPLATES)
            },
        )
    }
}

/** Wordmark, PRO tag and the settings affordance. */
@Composable
private fun HomeHeader(
    onOpenPro: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Snapzy",
            style = SnapzyType.display.copy(fontWeight = FontWeight.Bold),
            color = SnapzyColors.brandPrimary,
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.clickable(onClick = onOpenPro)) {
            StatusChip(text = "PRO")
        }
        Spacer(Modifier.weight(1f))
        CircleIconButton(
            contentDescription = "Settings",
            onClick = onOpenSettings,
            modifier = Modifier.size(38.dp),
        ) {
            NavIconCanvas(icon = NavIcon.SETTINGS, tint = SnapzyColors.textSecondary, size = 22.dp)
        }
    }
}

/** Small caps section title with a supporting line. */
@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(
            text = title,
            style = SnapzyType.title,
            color = SnapzyColors.textPrimary,
        )
        Text(
            text = subtitle,
            style = SnapzyType.label,
            color = SnapzyColors.textSecondary,
        )
    }
}

/**
 * The recent-projects area, shown when nothing has been saved yet.
 *
 * Instead of fake cards this renders a designed empty state that points at
 * the create actions.
 */
@Composable
private fun ProjectsSection(
    onCreateVideo: () -> Unit,
    onCreatePhoto: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SnapzyShapes.medium)
            .background(SnapzyColors.bgSurface)
            .dashedBorder(SnapzyColors.divider)
            .padding(horizontal = 20.dp, vertical = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SnapzyColors.bgSurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                ToolIconView(
                    tool = EditorTool.DRAFT,
                    tint = SnapzyColors.brandPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "No projects yet",
                style = SnapzyType.title,
                color = SnapzyColors.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tap + Create to start your first video or photo edit.",
                style = SnapzyType.body,
                color = SnapzyColors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SnapzyPrimaryButton(text = "New video", onClick = onCreateVideo)
                SnapzySecondaryButton(text = "New photo", onClick = onCreatePhoto)
            }
        }
    }
}

/**
 * The strip of saved drafts.
 *
 * Each card shows the draft's poster frame, its title, how long ago it was
 * touched and how long the edit is. Tapping a card opens it.
 */
@Composable
private fun DraftRow(
    drafts: List<DraftSummary>,
    onOpen: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        drafts.forEach { draft ->
            DraftCard(draft = draft, onClick = { onOpen(draft.id) })
        }
    }
}

/** One saved draft: poster frame, title, age and length. */
@Composable
private fun DraftCard(
    draft: DraftSummary,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(DRAFT_CARD_WIDTH)
            .clip(SnapzyShapes.medium)
            .background(SnapzyColors.bgSurface)
            .clickable(onClick = onClick)
            .padding(DRAFT_CARD_PADDING),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(DRAFT_THUMBNAIL_RATIO)
                .clip(SnapzyShapes.small)
                .background(SnapzyColors.bgSurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            DraftThumbnail(path = draft.thumbnailPath)
            Text(
                text = formatDuration(draft.durationMs),
                style = SnapzyType.caption,
                color = SnapzyColors.textPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(SnapzyShapes.pill)
                    .background(SnapzyColors.bgBase.copy(alpha = 0.72f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = draft.title,
            style = SnapzyType.body,
            color = SnapzyColors.textPrimary,
            maxLines = 1,
        )
        Text(
            text = relativeTime(draft.updatedAt),
            style = SnapzyType.caption,
            color = SnapzyColors.textSecondary,
            maxLines = 1,
        )
    }
}

/**
 * The draft's poster frame.
 *
 * The frame is the media the draft points at, so it is loaded through the
 * shared thumbnail loader. A photo draft keeps its picture in the draft
 * itself and has no file behind it, so it falls back to the draft glyph.
 */
@Composable
private fun DraftThumbnail(path: String?) {
    var thumbnail by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    val density = LocalDensity.current
    val widthPx = remember(density) {
        with(density) { (DRAFT_CARD_WIDTH - DRAFT_CARD_PADDING * 2).toPx().toInt() }
    }

    LaunchedEffect(path) {
        thumbnail = if (path.isNullOrBlank()) {
            null
        } else {
            val heightPx = (widthPx / DRAFT_THUMBNAIL_RATIO).toInt()
            loadThumbnail("file://$path", widthPx, heightPx)
        }
    }

    val image = thumbnail
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        ToolIconView(
            tool = EditorTool.DRAFT,
            tint = SnapzyColors.brandPrimary,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** How long ago a draft was touched, in words. */
private fun relativeTime(timestampMs: Long): String {
    val elapsed = (currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    val hours = elapsed / 3_600_000L
    val days = elapsed / 86_400_000L
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "$minutes min ago"
        hours < 24L -> "$hours hour${plural(hours)} ago"
        days < 7L -> "$days day${plural(days)} ago"
        else -> "Earlier"
    }
}

/** The length of a draft, as `m:ss`. */
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/** The plural "s" for a count, when it is not one. */
private fun plural(count: Long): String = if (count == 1L) "" else "s"

/** The width of a draft card. */
private val DRAFT_CARD_WIDTH = 168.dp

/** The padding inside a draft card. */
private val DRAFT_CARD_PADDING = 8.dp

/** The poster frame is drawn 16:9, like the canvas it comes from. */
private const val DRAFT_THUMBNAIL_RATIO = 16f / 9f

/**
 * The primary create actions, laid out the way InShot does it: one elevated
 * card holding three large circular buttons (Video, Photo, Collage).
 */
@Composable
private fun CreateCard(
    onCreateVideo: () -> Unit,
    onCreatePhoto: () -> Unit,
    onCreateCollage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SnapzyShapes.large)
            .background(SnapzyColors.bgSurface)
            .padding(vertical = 22.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        CreateCircleButton(
            label = "Video",
            tool = EditorTool.ANIMATION,
            colors = listOf(SnapzyColors.brandRamp[1], SnapzyColors.brandSecondary),
            onClick = onCreateVideo,
        )
        CreateCircleButton(
            label = "Photo",
            tool = EditorTool.BACKGROUND,
            colors = listOf(SnapzyColors.brandSecondary, SnapzyColors.brandRamp[3]),
            onClick = onCreatePhoto,
        )
        CreateCircleButton(
            label = "Collage",
            tool = EditorTool.COLLAGE,
            colors = listOf(SnapzyColors.brandRamp[3], SnapzyColors.brandSecondaryDeep),
            onClick = onCreateCollage,
        )
    }
}

/** One large circular create button with its label underneath. */
@Composable
private fun CreateCircleButton(
    label: String,
    tool: EditorTool,
    colors: List<Color>,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(SnapzyShapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center,
        ) {
            ToolIconView(
                tool = tool,
                tint = SnapzyColors.bgBase,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            style = SnapzyType.body,
            color = SnapzyColors.textPrimary,
        )
    }
}

/** Secondary, server-backed entry points shown as a compact row of pills. */
@Composable
private fun SecondaryActions(
    onTemplates: () -> Unit,
    onAiTools: () -> Unit,
    onStore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SecondaryPill(label = "Templates", tool = EditorTool.LAYOUT, onClick = onTemplates)
        SecondaryPill(label = "AI Tools", tool = EditorTool.ENHANCE, onClick = onAiTools)
        SecondaryPill(label = "Store", tool = EditorTool.STICKER, onClick = onStore)
    }
}

@Composable
private fun SecondaryPill(
    label: String,
    tool: EditorTool,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(SnapzyShapes.pill)
            .background(SnapzyColors.bgSurface)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToolIconView(
            tool = tool,
            tint = SnapzyColors.brandSecondary,
            modifier = Modifier.size(18.dp),
        )
        Text(text = label, style = SnapzyType.label, color = SnapzyColors.textPrimary)
    }
}

/** Rounded bottom bar: Home, Discover, Settings and the gradient create button. */
@Composable
private fun HomeBottomBar(
    onDiscover: () -> Unit,
    onSettings: () -> Unit,
    onCreate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(SnapzyShapes.large)
            .background(SnapzyColors.bgSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavItem(
            icon = NavIcon.HOME,
            label = "Home",
            selected = true,
            onClick = {},
            modifier = Modifier.weight(1f),
        )
        NavItem(
            icon = NavIcon.DISCOVER,
            label = "Discover",
            selected = false,
            onClick = onDiscover,
            modifier = Modifier.weight(1f),
        )
        NavItem(
            icon = NavIcon.SETTINGS,
            label = "Settings",
            selected = false,
            onClick = onSettings,
            modifier = Modifier.weight(1f),
        )
        GradientFab(size = 48.dp, onClick = onCreate) {
            Text("+", style = SnapzyType.title, color = SnapzyColors.bgBase)
        }
    }
}

@Composable
private fun NavItem(
    icon: NavIcon,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) SnapzyColors.brandPrimary else SnapzyColors.textSecondary
    Column(
        modifier = modifier
            .clip(SnapzyShapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NavIconCanvas(icon = icon, tint = tint, size = 22.dp)
        Spacer(Modifier.height(3.dp))
        Text(text = label, style = SnapzyType.caption, color = tint, maxLines = 1)
    }
}

/** The bottom-bar glyphs, drawn with Compose primitives so no icon font is needed. */
private enum class NavIcon { HOME, DISCOVER, SETTINGS }

@Composable
private fun NavIconCanvas(
    icon: NavIcon,
    tint: Color,
    size: Dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        drawNavIcon(icon = icon, tint = tint)
    }
}

private fun DrawScope.drawNavIcon(icon: NavIcon, tint: Color) {
    val stroke = 1.8f
    when (icon) {
        NavIcon.HOME -> {
            val roof = Path().apply {
                moveTo(3.5f, 11f)
                lineTo(12f, 4f)
                lineTo(20.5f, 11f)
            }
            drawPath(
                path = roof,
                color = tint,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(6f, 11f),
                size = Size(12f, 8.5f),
                cornerRadius = CornerRadius(2f, 2f),
                style = Stroke(width = stroke),
            )
        }
        NavIcon.DISCOVER -> {
            drawCircle(
                color = tint,
                radius = 8.5f,
                center = Offset(12f, 12f),
                style = Stroke(width = stroke),
            )
            val needle = Path().apply {
                moveTo(15.5f, 8.5f)
                lineTo(13.2f, 13.2f)
                lineTo(8.5f, 15.5f)
                lineTo(10.8f, 10.8f)
                close()
            }
            drawPath(path = needle, color = tint)
        }
        NavIcon.SETTINGS -> {
            drawCircle(
                color = tint,
                radius = 6.5f,
                center = Offset(12f, 12f),
                style = Stroke(width = stroke),
            )
            drawCircle(
                color = tint,
                radius = 2.6f,
                center = Offset(12f, 12f),
                style = Stroke(width = stroke),
            )
            repeat(8) { index ->
                val angle = index * PI / 4.0
                val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                val center = Offset(12f, 12f)
                drawLine(
                    color = tint,
                    start = Offset(center.x + dir.x * 7f, center.y + dir.y * 7f),
                    end = Offset(center.x + dir.x * 10f, center.y + dir.y * 10f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** Dashed rounded border, used by the empty-projects card. */
private fun Modifier.dashedBorder(color: Color): Modifier = drawBehind {
    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f))
    drawRoundRect(
        color = color,
        topLeft = Offset(1f, 1f),
        size = Size(size.width - 2f, size.height - 2f),
        cornerRadius = CornerRadius(14f, 14f),
        style = Stroke(width = 2f, pathEffect = pathEffect),
    )
}

/** The "Create" sheet opened from the gradient button in the bottom bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSheet(
    onDismiss: () -> Unit,
    onNewVideo: () -> Unit,
    onNewPhoto: () -> Unit,
    onCollage: () -> Unit,
    onTemplates: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SnapzyColors.bgSurfaceElevated,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Create new",
                style = SnapzyType.title,
                color = SnapzyColors.textPrimary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            CreateOptionRow(tool = EditorTool.ANIMATION, label = "New Video", onClick = onNewVideo)
            CreateOptionRow(tool = EditorTool.BACKGROUND, label = "New Photo", onClick = onNewPhoto)
            CreateOptionRow(tool = EditorTool.COLLAGE, label = "Collage", onClick = onCollage)
            CreateOptionRow(
                tool = EditorTool.LAYOUT,
                label = "Templates",
                gated = true,
                onClick = onTemplates,
            )
        }
    }
}

@Composable
private fun CreateOptionRow(
    tool: EditorTool,
    label: String,
    gated: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SnapzyShapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolIconView(
            tool = tool,
            tint = SnapzyColors.brandPrimary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(text = label, style = SnapzyType.body, color = SnapzyColors.textPrimary)
        Spacer(Modifier.weight(1f))
        if (gated) {
            StatusChip(text = "SOON", background = SnapzyColors.bgBase.copy(alpha = 0.7f))
        }
    }
}
