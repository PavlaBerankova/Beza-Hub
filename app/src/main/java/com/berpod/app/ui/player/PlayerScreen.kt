package com.berpod.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableFloatStateOf
import com.berpod.app.ui.theme.DotColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import com.berpod.app.ui.theme.LocalDotColors
import com.berpod.app.ui.podcastlist.rememberCoverTint
import com.berpod.app.ui.podcastlist.isLightTint
import com.berpod.app.ui.podcastlist.headerBlockColor
import com.berpod.app.ui.podcastlist.colorsOnBlock
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.animateColorAsState
import android.app.Activity
import com.berpod.app.R
import com.berpod.app.data.repository.SleepTimer
import com.berpod.app.ui.common.formatPlaybackTime
import com.berpod.app.ui.dot.CellRole
import com.berpod.app.ui.dot.DotIcon
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIconSpec
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.ringDots
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.dot.waveHeights
import com.berpod.app.ui.dot.waveSeekFraction
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType
import kotlin.math.abs

/**
 * Dot Matrix player: dot ring around the cover, seekable decorative wave, times and play/pause, on a solid
 * background in the cover's colour (like the podcast page's header) once the cover has loaded.
 */
@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onBack: () -> Unit, onOpenQueue: () -> Unit, onOpenPodcast: (Long) -> Unit, onOpenEpisode: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val base = DotTheme.colors
    val blockColor = rememberCoverTint(state.artworkUrl)?.let(::headerBlockColor)
    val block by animateColorAsState(blockColor ?: base.background, label = "playerBlock")
    val colors = if (blockColor == null) base else colorsOnBlock(base, block)

    // System bar icons follow the background: dark on a bright cover colour, light on a deep one.
    val view = LocalView.current
    val systemDark = isSystemInDarkTheme()
    val lightBlock = blockColor?.let(::isLightTint)
    DisposableEffect(lightBlock, systemDark) {
        val controller = (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = lightBlock ?: !systemDark
        onDispose { controller?.isAppearanceLightStatusBars = !systemDark }
    }

    // Only the top of the screen (bar, cover and its ring) sits on the colour, which fades out towards the
    // title; everything below keeps the normal colours on the normal background.
    PlayerContent(viewModel, onBack, onOpenQueue, onOpenPodcast, onOpenEpisode, block = blockColor?.let { block }, headerColors = colors.takeIf { blockColor != null })
}

@Composable
private fun PlayerContent(viewModel: PlayerViewModel, onBack: () -> Unit, onOpenQueue: () -> Unit, onOpenPodcast: (Long) -> Unit, onOpenEpisode: (Long) -> Unit, block: Color?, headerColors: DotColors?) {
    val state by viewModel.uiState.collectAsState()
    val c = DotTheme.colors
    val t = headerColors ?: c
    // Where the podcast's name ends: the colour stops just below it, so the title and name sit on the colour
    // and the wave starts on the normal background, whatever the title's length.
    var colourEndPx by remember { mutableFloatStateOf(0f) }
    // Spare height on a tall screen is put between the coloured top and the wave, which moves the rest down.
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    var gapPx by remember { mutableFloatStateOf(0f) }
    var screenHeightPx by remember { mutableFloatStateOf(0f) }
    var draggingFraction by remember { mutableStateOf<Float?>(null) }
    val known = state.durationMs > 0L
    val duration = state.durationMs.coerceAtLeast(1L)
    // Gesture handlers are installed once; they read the latest duration through this.
    val currentDuration by rememberUpdatedState(state.durationMs)
    val fraction = draggingFraction ?: if (known) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val heights = remember(state.currentEpisodeId) { waveHeights(state.currentEpisodeId ?: 0L) }
    val ringStep = (fraction * 56).toInt()
    val ring = remember(ringStep) { ringDots(56, center = 140f, radius = 132f, fraction = ringStep / 56f + 0.0001f) }

    val navBarPx = WindowInsets.navigationBars.getBottom(density)
    Column(
        Modifier
            .fillMaxSize()
            .onSizeChanged { screenHeightPx = it.height.toFloat() }
            .background(c.background)
            .drawBehind {
                if (block != null && colourEndPx > 0f) {
                    // A sharp edge a little below the podcast's name, like the podcast page's header.
                    drawRect(color = block, size = androidx.compose.ui.geometry.Size(size.width, colourEndPx + 22.dp.toPx()))
                }
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            // Scrolls on small screens or with large text so the play button stays reachable.
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CompositionLocalProvider(LocalDotColors provides t) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DotIconButton(DotIcons.CloseDown, stringResource(R.string.action_close_player), onBack)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                // The label follows the playback: "Přehrává se" while it plays, "Pozastaveno" while it is paused.
                DotIcon(DotIcons.WaveGlyph, 14.dp, monochrome = if (state.isPlaying) null else t.textMuted)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (state.isPlaying) R.string.now_playing else R.string.now_paused).upperCs(), style = DotType.chip.copy(fontWeight = FontWeight.W700, letterSpacing = 2.5.sp), color = t.text)
            }
            Spacer(Modifier.size(44.dp))
        }
        Box(Modifier.padding(top = 18.dp).size(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val scale = size.width / 280f
                ring.forEach { dot ->
                    drawCircle(
                        color = when (dot.role) { CellRole.Played -> t.accent; CellRole.Current -> t.text; CellRole.Rest -> t.ringIdle },
                        radius = 3f * scale,
                        center = Offset(dot.x * scale, dot.y * scale)
                    )
                }
            }
            Box(
                modifier = Modifier.size(210.dp).clip(CircleShape).background(t.divider).border(1.dp, t.divider, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                DotIcon(DotIcons.Podcasts, 56.dp, monochrome = t.textMuted)
                if (state.artworkUrl != null) {
                    AsyncImage(
                        model = state.artworkUrl, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
        }
        }
        Text(state.title, style = DotType.title.copy(fontSize = 20.sp), color = t.text, maxLines = 3, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 18.dp))
        Text(state.podcastTitle.upperCs(), style = DotType.chip.copy(letterSpacing = 2.sp), color = t.textMuted, modifier = Modifier.padding(top = 6.dp).onGloballyPositioned { colourEndPx = it.positionInRoot().y + it.size.height })
        Spacer(Modifier.height(with(density) { gapPx.toDp() }))
        val seekLabel = stringResource(R.string.action_seek)
        Canvas(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .aspectRatio(36f / 9f)
                .semantics {
                    contentDescription = seekLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { target ->
                        if (currentDuration <= 0L) return@setProgress false
                        viewModel.seekTo((target.coerceIn(0f, 1f) * currentDuration).toLong())
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (currentDuration > 0L) viewModel.seekTo((waveSeekFraction(it.x, size.width.toFloat()) * currentDuration).toLong())
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            draggingFraction?.let { if (currentDuration > 0L) viewModel.seekTo((it * currentDuration).toLong()) }
                            draggingFraction = null
                        },
                        onDragCancel = { draggingFraction = null }
                    ) { change, _ -> draggingFraction = waveSeekFraction(change.position.x, size.width.toFloat()) }
                }
        ) {
            val gap = 2.dp.toPx()
            val cell = (size.width - gap * 35) / 36
            val playedColumns = (fraction * 36).toInt()
            for (col in 0 until 36) for (row in 0 until 9) {
                val lit = row >= 9 - heights[col]
                drawRoundRect(
                    color = if (!lit) c.waveIdle else if (col < playedColumns) c.accent else c.waveMuted,
                    topLeft = Offset(col * (cell + gap), row * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(formatPlaybackTime((fraction * duration).toLong()), style = DotType.rowTitle.copy(letterSpacing = 1.sp), color = c.text)
            Spacer(Modifier.weight(1f))
                        if (known) Text("−" + formatPlaybackTime(duration - (fraction * duration).toLong()), style = DotType.rowTitle.copy(letterSpacing = 1.sp), color = c.textMuted)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkipButton(DotIcons.Back15, "15", stringResource(R.string.action_back_15), viewModel::skipBack)
            TransportButton(DotIcons.Previous, stringResource(R.string.action_previous), enabled = true, onClick = viewModel::playPrevious)
            DotIconButton(
                if (state.isPlaying) DotIcons.PauseLarge else DotIcons.PlayLarge,
                stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                viewModel::togglePlayPause,
                size = 78.dp, radius = 24.dp, iconSize = 36.dp, accentBorder = true, filled = c.surface
            )
            TransportButton(DotIcons.Next, stringResource(R.string.action_next), enabled = state.hasNext, onClick = viewModel::playNext)
            SkipButton(DotIcons.Forward30, "30", stringResource(R.string.action_forward_30), viewModel::skipForward)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp).onGloballyPositioned { coords ->
                // Not while the screen is scrolled: positions are then shifted and would change the gap.
                if (scroll.value == 0 && screenHeightPx > 0f) {
                    val optionsBottom = coords.positionInRoot().y + coords.size.height
                    val free = screenHeightPx - navBarPx - with(density) { 24.dp.toPx() } - (optionsBottom - gapPx)
                    gapPx = (free * 0.7f).coerceAtLeast(0f)
                }
            },
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OptionButton(
                icon = DotIcons.Speed,
                label = formatSpeed(state.speed),
                description = stringResource(R.string.speed) + " " + formatSpeed(state.speed),
                active = abs(state.speed - 1f) > 0.01f,
                onClick = viewModel::cycleSpeed,
                modifier = Modifier.weight(1f)
            )
            Box(Modifier.weight(1f)) {
                var menuOpen by remember { mutableStateOf(false) }
                OptionButton(
                    icon = DotIcons.Sleep,
                    label = when (val t = state.sleepTimer) {
                        null -> null
                        SleepTimer.EndOfEpisode -> stringResource(R.string.sleep_end_short)
                        is SleepTimer.At -> formatPlaybackTime(state.sleepRemainingMs ?: 0L)
                    },
                    // Stable for screen readers; the visible countdown changes every second.
                    description = when (state.sleepTimer) {
                        null -> stringResource(R.string.sleep)
                        SleepTimer.EndOfEpisode -> stringResource(R.string.sleep) + ", " + stringResource(R.string.sleep_end_of_episode)
                        is SleepTimer.At -> stringResource(R.string.sleep_running)
                    },
                    active = state.sleepTimer != null,
                    onClick = { menuOpen = true },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    listOf(15, 30, 45, 60).forEach { min ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.sleep_minutes, min)) }, onClick = { menuOpen = false; viewModel.setSleepTimer(min) })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.sleep_end_of_episode)) }, onClick = { menuOpen = false; viewModel.setSleepAtEndOfEpisode() })
                    if (state.sleepTimer != null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.sleep_cancel)) }, onClick = { menuOpen = false; viewModel.cancelSleepTimer() })
                    }
                }
            }
            OptionButton(DotIcons.QueueList, null, active = false, onClick = onOpenQueue, modifier = Modifier.weight(1f), description = stringResource(R.string.queue))
            // Where the episode and its podcast can be read about.
            Box(Modifier.weight(1f)) {
                var detailOpen by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                OptionButton(
                    DotIcons.Info,
                    null,
                    active = false,
                    onClick = { detailOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    description = stringResource(R.string.player_detail)
                )
                DropdownMenu(expanded = detailOpen, onDismissRequest = { detailOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_open_podcast)) },
                        onClick = {
                            detailOpen = false
                            scope.launch { viewModel.currentFeedId()?.let(onOpenPodcast) }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_open_episode)) },
                        onClick = {
                            detailOpen = false
                            state.currentEpisodeId?.let(onOpenEpisode)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SkipButton(icon: DotIconSpec, seconds: String, description: String, onClick: () -> Unit) {
    DotIconButton(icon, description, onClick, size = 52.dp, radius = 16.dp) {
        Box(contentAlignment = Alignment.Center) {
            DotIcon(icon, 30.dp)
            // Already in the button's description; keep TalkBack from reading it twice.
            Text(seconds, style = DotType.chip.copy(fontSize = 9.sp, fontWeight = FontWeight.W700), color = DotTheme.colors.text, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
private fun TransportButton(icon: DotIconSpec, description: String, enabled: Boolean, onClick: () -> Unit) {
    val c = DotTheme.colors
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        DotIcon(icon, 28.dp, monochrome = if (enabled) null else c.divider)
    }
}

@Composable
private fun OptionButton(icon: DotIconSpec, label: String?, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, description: String = label.orEmpty()) {
    val c = DotTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .border(1.dp, if (active) c.accent else c.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description; role = Role.Button; onClick { onClick(); true } },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DotIcon(icon, 20.dp)
        // Icon-only unless there is a value to show (the speed, a running sleep timer).
        if (label != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                label.upperCs(),
                style = DotType.segment.copy(fontWeight = if (active) FontWeight.W700 else FontWeight.W600, letterSpacing = 1.sp),
                color = if (active) c.accent else c.textStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatSpeed(speed: Float): String = String.format(java.util.Locale.forLanguageTag("cs"), "%.1f×", speed)
