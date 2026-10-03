package com.berpod.app.ui.episode

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.data.local.EpisodeWithFeed
import com.berpod.app.ui.common.formatDuration
import com.berpod.app.ui.common.htmlText
import com.berpod.app.ui.common.formatPlaybackTime
import com.berpod.app.ui.dot.CoverImage
import com.berpod.app.ui.dot.DotIcon
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIconSpec
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.DotPrimaryButton
import com.berpod.app.ui.dot.DotProgress
import com.berpod.app.ui.dot.SectionSeparator
import com.berpod.app.ui.dot.formatDotDate
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/** Dot Matrix episode detail (canvas "E1 · Detail epizody"). */
@Composable
fun EpisodeDetailScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    onOpenPodcast: (feedId: Long) -> Unit
) {
    val viewModel: EpisodeDetailViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val c = DotTheme.colors

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onSnackbarMessageShown()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = c.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DotIconButton(DotIcons.Back, stringResource(R.string.action_back), onBack)
                Text(
                    stringResource(R.string.episode).upperCs(),
                    style = DotType.chip.copy(fontWeight = FontWeight.W700, letterSpacing = 2.5.sp),
                    color = c.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                // Balances the back button so the title stays centred.
                Spacer(Modifier.size(44.dp))
            }
            val item = uiState.item
            when {
                item != null -> EpisodeContent(item, uiState, viewModel, onOpenPodcast)
                // Plain text: EmptyState fills the height, which a scrolling column can't give it.
                uiState.isLoaded -> Text(
                    stringResource(R.string.episode_missing),
                    style = DotType.body,
                    color = c.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp)
                )
            }
        }
    }
}

@Composable
private fun EpisodeContent(
    item: EpisodeWithFeed,
    uiState: EpisodeDetailUiState,
    viewModel: EpisodeDetailViewModel,
    onOpenPodcast: (Long) -> Unit
) {
    val c = DotTheme.colors
    val episode = item.episode
    val context = LocalContext.current

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CoverImage(episode.imageUrl ?: item.podcastImageUrl, 104.dp, 18.dp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                item.podcastTitle.upperCs(),
                style = DotType.chip.copy(fontWeight = FontWeight.W700, letterSpacing = 1.5.sp),
                color = c.accent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(formatDotDate(episode.pubDateMillis), formatDuration(episode.durationSeconds)?.upperCs()).joinToString("\n"),
                style = DotType.label.copy(lineHeight = 17.sp),
                color = c.textMuted
            )
        }
    }

    Text(
        episode.title,
        style = DotType.title.copy(fontSize = 24.sp, lineHeight = 31.sp),
        color = c.text,
        modifier = Modifier.semantics { heading() }
    )

    val fraction = if (uiState.durationMs > 0) (uiState.positionMs.toFloat() / uiState.durationMs).coerceIn(0f, 1f) else null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DotProgress(fraction ?: 0f, cells = 40)
        Row {
            Text(formatPlaybackTime(uiState.positionMs), style = DotType.chip.copy(fontSize = 13.sp, letterSpacing = 1.sp), color = c.text)
            Spacer(Modifier.weight(1f))
            val remaining = formatDuration(((uiState.durationMs - uiState.positionMs) / 1000).toInt())
            if (remaining != null) {
                val label = if (uiState.positionMs > 0) stringResource(R.string.remaining, remaining) else remaining
                Text(label.upperCs(), style = DotType.chip.copy(fontSize = 13.sp, letterSpacing = 1.sp), color = c.textMuted)
            }
        }
    }

    val action = primaryAction(episode, uiState.isCurrent, uiState.isPlaying)
    DotPrimaryButton(
        label = when (action) {
            PrimaryAction.Play -> stringResource(R.string.action_play)
            PrimaryAction.Resume -> stringResource(R.string.resume_from, formatPlaybackTime(uiState.positionMs))
            PrimaryAction.Pause -> stringResource(R.string.action_pause)
            PrimaryAction.PlayAgain -> stringResource(R.string.action_play_again)
        },
        icon = if (action == PrimaryAction.Pause) DotIcons.Pause else DotIcons.PlaySolid,
        onClick = viewModel::onPrimary
    )

    val shareTitle = stringResource(R.string.action_share)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EpisodeActionButton(DotIcons.Download, stringResource(R.string.action_download), active = false, onClick = {}, modifier = Modifier.weight(1f))
        EpisodeActionButton(
            icon = if (uiState.isQueued) DotIcons.Queued else DotIcons.AddToQueue,
            label = stringResource(if (uiState.isQueued) R.string.in_queue else R.string.to_queue),
            active = uiState.isQueued,
            onClick = viewModel::onToggleQueue,
            modifier = Modifier.weight(1f),
            description = stringResource(if (uiState.isQueued) R.string.action_remove_from_queue else R.string.action_add_to_queue)
        )
        EpisodeActionButton(
            icon = DotIcons.Share,
            label = shareTitle,
            active = false,
            onClick = {
                val text = "${episode.title} — ${item.podcastTitle}\n${episode.audioUrl}"
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, shareTitle))
            },
            modifier = Modifier.weight(1f)
        )
    }

    // With chapters found, glued chapter times get their line breaks back in the text too.
    val hasChapters = uiState.chapters.isNotEmpty()
    val description = remember(episode.description, hasChapters) {
        htmlText(episode.description?.let { if (hasChapters) unglueChapterTimes(it) else it }, c.accent)
    }
    if (description != null) {
        Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionSeparator(stringResource(R.string.section_about_episode))
            Text(description, style = DotType.reading, color = c.text)
        }
    }

    if (uiState.chapters.isNotEmpty()) {
        val current = if (uiState.isCurrent) currentChapterIndex(uiState.chapters, uiState.positionMs) else -1
        Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionSeparator(stringResource(R.string.section_chapters), "%02d".format(uiState.chapters.size))
            Column {
                uiState.chapters.forEachIndexed { i, chapter ->
                    ChapterRow(
                        chapter = chapter,
                        state = when {
                            i == current -> ChapterState.Current
                            i < current -> ChapterState.Past
                            else -> ChapterState.Upcoming
                        },
                        showDivider = i < uiState.chapters.lastIndex && i != current && i + 1 != current,
                        onClick = { viewModel.onChapter(chapter) }
                    )
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, c.divider, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) { onOpenPodcast(episode.feedId) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CoverImage(item.podcastImageUrl, 48.dp, 10.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.all_episodes).upperCs(), style = DotType.label, color = c.textMuted)
            Text(item.podcastTitle, style = DotType.rowTitle.copy(fontWeight = FontWeight.W700), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DotIcon(DotIcons.Forward, 20.dp)
    }
}

@Composable
private fun EpisodeActionButton(
    icon: DotIconSpec,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = label
) {
    val c = DotTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .border(if (active) 1.5.dp else 1.dp, if (active) c.accent else c.outline, shape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DotIcon(icon, 20.dp)
        Spacer(Modifier.width(6.dp))
        Text(label.upperCs(), style = DotType.label.copy(fontWeight = FontWeight.W700, letterSpacing = 1.sp), color = c.text, maxLines = 1)
    }
}

private enum class ChapterState { Past, Current, Upcoming }

@Composable
private fun ChapterRow(chapter: Chapter, state: ChapterState, showDivider: Boolean, onClick: () -> Unit) {
    val c = DotTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (state == ChapterState.Current) c.surface else c.background)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                formatPlaybackTime(chapter.startMs),
                style = DotType.chip.copy(fontSize = 13.sp, fontWeight = FontWeight.W700, letterSpacing = 1.sp),
                color = if (state == ChapterState.Past) c.textMuted else c.accent,
                modifier = Modifier.width(64.dp)
            )
            Text(
                chapter.title,
                style = DotType.rowTitle.copy(fontWeight = if (state == ChapterState.Current) FontWeight.W700 else FontWeight.W500),
                color = if (state == ChapterState.Past) c.textMuted else c.text,
                modifier = Modifier.weight(1f)
            )
            if (state == ChapterState.Current) Box { DotIcon(DotIcons.Equalizer, 22.dp) }
        }
        if (showDivider) HorizontalDivider(color = c.divider)
    }
}
