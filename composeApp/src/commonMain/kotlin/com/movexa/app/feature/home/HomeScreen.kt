package com.movexa.app.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.movexa.app.core.comingsoon.GatedFeature
import com.movexa.app.core.designsystem.ICON_GRID
import com.movexa.app.core.designsystem.MovexaColors
import com.movexa.app.core.designsystem.MovexaShapes
import com.movexa.app.core.designsystem.MovexaType
import com.movexa.app.core.designsystem.ToolIconView
import com.movexa.app.core.editing.EditProject
import com.movexa.app.core.editing.EditorTool
import com.movexa.app.core.storage.DraftSummary
import com.movexa.app.core.storage.createDraftStore
import com.movexa.app.core.storage.currentTimeMillis
import com.movexa.app.core.media.loadThumbnail
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * The springboard.
 *
 * The layout follows the reference app: a colourful wallpaper, the wordmark
 * with the Pro crown and the settings gear in the header, the recent drafts in
 * the open space, `CREATE NEW` with the three create buttons on a frosted
 * card, and a `WHAT'S NEW` row of the server-backed features.
 *
 * There is no bottom navigation — the crown and the gear are the only other
 * places to go, and both of them open the shared Coming Soon sheet because
 * they are backed by a server.
 */
@Composable
fun HomeScreen(
    onCreateVideo: () -> Unit,
    onCreatePhoto: () -> Unit,
    onCreateCollage: () -> Unit,
    onGatedFeature: (GatedFeature) -> Unit,
    onOpenDraft: (EditProject) -> Unit = {},
) {
    val draftStore = remember { createDraftStore() }
    val scope = rememberCoroutineScope()
    var drafts by remember { mutableStateOf<List<DraftSummary>>(emptyList()) }

    // The list is reloaded every time the screen comes back into view, so a
    // draft saved in the editor appears as soon as the user returns.
    LifecycleResumeEffect(draftStore) {
        scope.launch { drafts = draftStore.list() }
        onPauseOrDispose { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = MovexaColors.homeGradient,
                    start = Offset(0f, 0f),
                    end = Offset(1400f, 2200f),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            HomeHeader(
                onOpenPro = { onGatedFeature(GatedFeature.GO_PRO) },
                onOpenSettings = { onGatedFeature(GatedFeature.SETTINGS) },
            )

            Spacer(Modifier.weight(1f))

            if (drafts.isNotEmpty()) {
                SectionLabel(text = "RECENT")
                Spacer(Modifier.height(10.dp))
                DraftRow(
                    drafts = drafts,
                    onOpen = { id ->
                        scope.launch { draftStore.load(id)?.let(onOpenDraft) }
                    },
                )
                Spacer(Modifier.height(28.dp))
            }

            SectionLabel(text = "CREATE NEW")
            Spacer(Modifier.height(12.dp))
            CreateCard(
                onCreateVideo = onCreateVideo,
                onCreatePhoto = onCreatePhoto,
                onCreateCollage = onCreateCollage,
            )

            Spacer(Modifier.height(22.dp))
            SectionLabelRow(
                title = "WHAT'S NEW",
                action = "ALL ›",
                onAction = { onGatedFeature(GatedFeature.DISCOVER) },
            )
            Spacer(Modifier.height(12.dp))
            FeatureRow(onGatedFeature = onGatedFeature)

            Spacer(Modifier.height(22.dp))
        }
    }
}

/* ---------------------------------------------------------------- header -- */

/** The wordmark, the Pro crown and the settings gear. */
@Composable
private fun HomeHeader(
    onOpenPro: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Movexa",
            style = MovexaType.display,
            color = Color.White,
        )
        Spacer(Modifier.weight(1f))
        HeaderIconButton(
            icon = HeaderIcon.CROWN,
            contentDescription = "Movexa Pro",
            onClick = onOpenPro,
        )
        Spacer(Modifier.width(8.dp))
        HeaderIconButton(
            icon = HeaderIcon.SETTINGS,
            contentDescription = "Settings",
            onClick = onOpenSettings,
        )
    }
}

private enum class HeaderIcon { CROWN, SETTINGS }

/** A round, frosted button holding one of the header glyphs. */
@Composable
private fun HeaderIconButton(
    icon: HeaderIcon,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MovexaColors.homeCard)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp)) {
            when (icon) {
                HeaderIcon.CROWN -> drawCrownIcon(Color.White)
                HeaderIcon.SETTINGS -> drawGearIcon(Color.White)
            }
        }
    }
}

/** A three-pointed crown, the Pro badge. */
private fun DrawScope.drawCrownIcon(tint: Color) {
    val factor = min(size.width, size.height) / ICON_GRID
    scale(factor, factor, pivot = Offset.Zero) {
        val path = Path().apply {
            moveTo(4f, 18f)
            lineTo(4f, 8f)
            lineTo(9f, 12f)
            lineTo(12f, 5f)
            lineTo(15f, 12f)
            lineTo(20f, 8f)
            lineTo(20f, 18f)
            close()
        }
        drawPath(path = path, color = tint)
        drawLine(
            color = tint,
            start = Offset(3f, 20.5f),
            end = Offset(21f, 20.5f),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
        )
    }
}

/** The settings gear. */
private fun DrawScope.drawGearIcon(tint: Color) {
    val factor = min(size.width, size.height) / ICON_GRID
    scale(factor, factor, pivot = Offset.Zero) {
        drawCircle(
            color = tint,
            radius = 7.5f,
            center = Offset(12f, 12f),
            style = Stroke(width = 2f),
        )
        drawCircle(color = tint, radius = 2.5f, center = Offset(12f, 12f))
        for (index in 0 until 8) {
            val angle = index * (kotlin.math.PI / 4.0)
            val dx = (kotlin.math.cos(angle) * 9.5).toFloat()
            val dy = (kotlin.math.sin(angle) * 9.5).toFloat()
            drawLine(
                color = tint,
                start = Offset(12f + dx * 0.72f, 12f + dy * 0.72f),
                end = Offset(12f + dx, 12f + dy),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/* --------------------------------------------------------------- labels -- */

/** An uppercase section label. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MovexaType.title.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
        ),
        color = Color.White,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

/** A section label with a link on the right, the way `WHAT'S NEW` reads. */
@Composable
private fun SectionLabelRow(
    title: String,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MovexaType.title.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
            ),
            color = Color.White,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = action,
            style = MovexaType.label.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier
                .clip(MovexaShapes.pill)
                .clickable(onClick = onAction)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/* ---------------------------------------------------------------- create -- */

/** The frosted card the three create buttons sit on. */
@Composable
private fun CreateCard(
    onCreateVideo: () -> Unit,
    onCreatePhoto: () -> Unit,
    onCreateCollage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MovexaColors.homeCard)
            .border(
                border = BorderStroke(1.dp, MovexaColors.homeCardBorder),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(vertical = 22.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        CreateCircleButton(
            label = "Video",
            tool = EditorTool.ANIMATION,
            colors = listOf(Color(0xFFFF7AB6), Color(0xFFFF4F6D)),
            onClick = onCreateVideo,
        )
        CreateCircleButton(
            label = "Photo",
            tool = EditorTool.BACKGROUND,
            colors = listOf(Color(0xFFFF9E6D), Color(0xFFFF5F6D)),
            onClick = onCreatePhoto,
        )
        CreateCircleButton(
            label = "Collage",
            tool = EditorTool.COLLAGE,
            colors = listOf(Color(0xFFFFC46B), Color(0xFFFF8A4C)),
            onClick = onCreateCollage,
        )
    }
}

/** One of the three round create buttons. */
@Composable
private fun CreateCircleButton(
    label: String,
    tool: EditorTool,
    colors: List<Color>,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(MovexaShapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center,
        ) {
            ToolIconView(
                tool = tool,
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            style = MovexaType.body,
            color = Color.White,
        )
    }
}

/* ----------------------------------------------------------------- news -- */

/** The `WHAT'S NEW` row: the server-backed features, each one gated. */
@Composable
private fun FeatureRow(onGatedFeature: (GatedFeature) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FeatureCard(
            title = "Templates",
            subtitle = "Trending edits",
            tool = EditorTool.LAYOUT,
            colors = listOf(Color(0xFF5FD8FF), Color(0xFF3F7BFF)),
            onClick = { onGatedFeature(GatedFeature.TEMPLATES) },
        )
        FeatureCard(
            title = "AI Tools",
            subtitle = "Cut, captions, more",
            tool = EditorTool.ENHANCE,
            colors = listOf(Color(0xFFB98CFF), Color(0xFF6E6AF0)),
            onClick = { onGatedFeature(GatedFeature.AI_ENHANCE) },
        )
        FeatureCard(
            title = "Store",
            subtitle = "Music and stickers",
            tool = EditorTool.STICKER,
            colors = listOf(Color(0xFFFF8FB8), Color(0xFFFF5F8F)),
            onClick = { onGatedFeature(GatedFeature.MARKETPLACE) },
        )
    }
}

/** One card in the `WHAT'S NEW` row. */
@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    tool: EditorTool,
    colors: List<Color>,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(MovexaShapes.medium)
            .background(MovexaColors.homeCard)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.95f)
                .background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center,
        ) {
            ToolIconView(
                tool = tool,
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
            Text(
                text = "SOON",
                style = MovexaType.caption.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(MovexaShapes.pill)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MovexaType.body.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Text(
            text = subtitle,
            style = MovexaType.caption,
            color = Color.White.copy(alpha = 0.78f),
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Spacer(Modifier.height(10.dp))
    }
}

/* --------------------------------------------------------------- drafts -- */

/** The row of saved drafts. */
@Composable
private fun DraftRow(
    drafts: List<DraftSummary>,
    onOpen: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
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
            .width(120.dp)
            .clip(MovexaShapes.medium)
            .background(MovexaColors.homeCard)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(MovexaShapes.small)
                .background(Color.Black.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center,
        ) {
            DraftThumbnail(path = draft.thumbnailPath)
            Text(
                text = formatDuration(draft.durationMs),
                style = MovexaType.caption,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(MovexaShapes.pill)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = draft.title,
            style = MovexaType.caption.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text(
            text = relativeTime(draft.updatedAt),
            style = MovexaType.caption,
            color = Color.White.copy(alpha = 0.72f),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** The poster frame of a draft. */
@Composable
private fun DraftThumbnail(path: String?) {
    val source = path?.let { "file://$it" }
    if (source == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "▤",
                style = MovexaType.title,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
        return
    }

    var bitmap by remember(source) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(source) {
        bitmap = source?.let { loadThumbnail(it, 320, 200) }
    }
    bitmap?.let { loaded ->
        Image(
            bitmap = loaded,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f),
            contentScale = ContentScale.Crop,
        )
    }
}

/** "just now", "3 min ago", "yesterday". */
private fun relativeTime(timestampMs: Long): String {
    val now = currentTimeMillis()
    val elapsed = (now - timestampMs).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    val hours = elapsed / 3_600_000L
    val days = elapsed / 86_400_000L
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "$minutes min ago"
        hours < 24L -> "$hours hour${plural(hours)} ago"
        days == 1L -> "yesterday"
        else -> "$days days ago"
    }
}

/** "0:06" or "1:02:03". */
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

private fun plural(count: Long): String = if (count == 1L) "" else "s"
