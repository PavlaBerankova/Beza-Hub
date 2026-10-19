package com.berpod.app.ui.youtube.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.data.local.YoutubeVideoEntity
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.heightIn
import com.berpod.app.data.local.VideoWithChannel
import com.berpod.app.ui.common.formatDuration
import com.berpod.app.ui.common.formatPlaybackTime
import com.berpod.app.ui.common.htmlText
import com.berpod.app.ui.dot.DotIcon
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIconSpec
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.DotProgress
import com.berpod.app.ui.dot.SectionSeparator
import com.berpod.app.ui.dot.formatDotDate
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType
import com.berpod.app.ui.youtube.channels.ChannelAvatar
import com.berpod.app.ui.youtube.newvideos.CompactVideoRow

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Dot Matrix video detail (canvas "Y4 · Video"): embedded player with custom chrome, then details. */
@Composable
fun VideoDetailScreen(viewModelFactory: ViewModelProvider.Factory, onBack: () -> Unit, onVideoClick: (Long) -> Unit) {
    val viewModel: VideoDetailViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val c = DotTheme.colors
    // Shared with the app's frame, which hides the mini player and the tabs while this is on.
    val fullscreenState = LocalVideoFullscreen.current
    var isFullscreen by fullscreenState
    // Leaving the page leaves fullscreen.
    DisposableEffect(Unit) { onDispose { fullscreenState.value = false } }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onSnackbarMessageShown()
        }
    }

    BackHandler(enabled = isFullscreen) {
        viewModel.onPlayerWillBeRecreated()
        isFullscreen = false
    }

    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    DisposableEffect(isFullscreen, activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = if (isFullscreen) Color.Black else c.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
                .then(if (isFullscreen) Modifier else Modifier.statusBarsPadding())
                .then(if (isFullscreen) Modifier else Modifier.verticalScroll(rememberScrollState()))
        ) {
            if (!isFullscreen) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    DotIconButton(DotIcons.Back, stringResource(R.string.action_back), onBack)
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        DotIcon(DotIcons.Watching, 16.dp, monochrome = c.accent)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.video_label).upperCs(),
                            style = DotType.chip.copy(fontWeight = FontWeight.W700, letterSpacing = 2.5.sp),
                            color = c.text,
                            maxLines = 1
                        )
                    }
                    // Rendered per the design; no menu is defined for it yet.
                    DotIconButton(DotIcons.More, stringResource(R.string.action_more), onClick = {})
                }
            }
            val item = uiState.item
            when {
                item != null -> VideoContent(
                    item = item,
                    uiState = uiState,
                    viewModel = viewModel,
                    onVideoClick = onVideoClick,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = { isFullscreen = !isFullscreen }
                )
                uiState.isLoaded -> Text(
                    stringResource(R.string.video_missing),
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
private fun VideoContent(
    item: VideoWithChannel,
    uiState: VideoDetailUiState,
    viewModel: VideoDetailViewModel,
    onVideoClick: (Long) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit
) {
    val c = DotTheme.colors
    val video = item.video
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.choose_browser)

    if (IN_APP_VIDEO_PLAYBACK) {
    if (isFullscreen) {
        // Fullscreen: a black screen, the video as large as it goes at 16:9 (bars at the sides), YouTube's own controls
        // and nothing drawn over it. The player is created again with those controls, from where it is now.
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            key(true) {
                EmbeddedYoutubePlayer(viewModel.playerListener, nativeControls = true, modifier = Modifier.fillMaxHeight().aspectRatio(16f / 9f))
            }
        }
        return
    }

    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        key(false) {
            EmbeddedYoutubePlayer(viewModel.playerListener, nativeControls = false, modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(role = Role.Button, onClickLabel = stringResource(if (uiState.isPlaying) R.string.action_pause else R.string.action_play)) {
                    viewModel.onTogglePlayPause()
                }
        ) {
            Box(
                modifier = Modifier.align(Alignment.Center).size(56.dp).clip(RoundedCornerShape(28.dp)).background(c.accent.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                DotIcon(if (uiState.isPlaying) DotIcons.Pause else DotIcons.PlaySolid, 22.dp, monochrome = c.onAccent)
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val fraction = if (uiState.durationMs > 0) (uiState.positionMs.toFloat() / uiState.durationMs).coerceIn(0f, 1f) else 0f
                Box(
                    modifier = Modifier.fillMaxWidth().pointerInput(uiState.durationMs) {
                        if (uiState.durationMs <= 0) return@pointerInput
                        detectTapGestures { offset ->
                            val seekFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            viewModel.onSeek((seekFraction * uiState.durationMs).toLong())
                        }
                    }
                ) {
                    DotProgress(fraction, cells = 32)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatPlaybackTime(uiState.positionMs), style = DotType.chip.copy(fontSize = 11.sp, letterSpacing = 1.sp), color = Color.White)
                    Spacer(Modifier.weight(1f))
                    Text(formatPlaybackTime(uiState.durationMs), style = DotType.chip.copy(fontSize = 11.sp, letterSpacing = 1.sp), color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(if (isFullscreen) R.string.action_fullscreen_exit else R.string.action_fullscreen),
                                onClick = { viewModel.onPlayerWillBeRecreated(); onToggleFullscreen() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        DotIcon(if (isFullscreen) DotIcons.FullscreenExit else DotIcons.Fullscreen, 18.dp, monochrome = Color.White)
                    }
                }
            }
        }
    }

    } else {
        VideoPreview(video, onOpen = { openInYoutube(context, video.videoUrl) })
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChannelAvatar(item.channelThumbnailUrl, item.channelTitle, 48.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.channelTitle.upperCs(),
                    style = DotType.chip.copy(fontWeight = FontWeight.W700, letterSpacing = 1.5.sp),
                    color = c.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                formatDotDate(video.pubDateMillis)?.let { Text(it, style = DotType.label, color = c.textMuted) }
            }
        }

        Text(
            video.title,
            style = DotType.title.copy(fontSize = 22.sp, lineHeight = 29.sp),
            color = c.text,
            modifier = Modifier.semantics { heading() }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoActionButton(
                icon = if (uiState.isQueued) DotIcons.Queued else DotIcons.Queue,
                label = stringResource(if (uiState.isQueued) R.string.in_queue else R.string.to_queue),
                active = uiState.isQueued,
                onClick = viewModel::onToggleQueue,
                modifier = Modifier.weight(1f),
                description = stringResource(if (uiState.isQueued) R.string.action_remove_from_queue else R.string.action_add_to_queue)
            )
            VideoActionButton(
                icon = DotIcons.YouTube,
                label = stringResource(R.string.in_youtube_button),
                active = false,
                onClick = { openInYoutube(context, video.videoUrl) },
                modifier = Modifier.weight(1f),
                description = stringResource(R.string.action_open_in_youtube)
            )
            VideoActionButton(
                icon = DotIcons.OpenExternal,
                label = stringResource(R.string.in_browser_button),
                active = false,
                onClick = { openInBrowser(context, video.videoUrl, chooserTitle) },
                modifier = Modifier.weight(1f),
                description = stringResource(R.string.action_open_in_browser)
            )
        }

        WatchedButton(isWatched = uiState.isWatched, onClick = viewModel::onToggleWatched)

        val description = remember(video.description) { htmlText(video.description, c.accent) }
        if (description != null) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionSeparator(stringResource(R.string.section_about_video))
                Text(description, style = DotType.reading, color = c.text)
            }
        }

        if (uiState.nextInQueue.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionSeparator(stringResource(R.string.section_next_in_queue), uiState.nextInQueue.size.toString())
                Column {
                    uiState.nextInQueue.forEachIndexed { index, next ->
                        CompactVideoRow(
                            video = next.video,
                            channelTitle = next.channelTitle,
                            whenLabel = null,
                            onClick = { onVideoClick(next.video.id) },
                            showDivider = index < uiState.nextInQueue.lastIndex
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoActionButton(
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

/** The thumbnail of the video with a play button that opens it in the YouTube app. */
@Composable
private fun VideoPreview(video: YoutubeVideoEntity, onOpen: () -> Unit) {
    val c = DotTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(c.divider)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.action_open_in_youtube), onClick = onOpen),
        contentAlignment = Alignment.Center
    ) {
        DotIcon(DotIcons.YouTube, 40.dp, monochrome = c.textMuted)
        if (video.thumbnailUrl != null) {
            AsyncImage(model = video.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(28.dp)).background(c.accent.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            DotIcon(DotIcons.PlaySolid, 22.dp, monochrome = c.onAccent)
        }
    }
}

/**
 * "Označit jako zhlédnuté" while the video is not watched (an outlined button that asks to be pressed), and a filled
 * accent "Zhlédnuto" with a check once it is, with a hint that pressing it again takes the mark back.
 */
@Composable
private fun WatchedButton(isWatched: Boolean, onClick: () -> Unit) {
    val c = DotTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val stateText = stringResource(if (isWatched) R.string.status_watched else R.string.status_not_watched)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(shape)
            .then(if (isWatched) Modifier.background(c.accent) else Modifier.border(1.5.dp, c.outline, shape))
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(if (isWatched) R.string.action_mark_unwatched else R.string.action_mark_watched),
                onClick = onClick
            )
            .semantics { stateDescription = stateText }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isWatched) {
            DotIcon(DotIcons.Check, 22.dp, monochrome = c.onAccent)
            Text(
                stringResource(R.string.status_watched).upperCs(),
                style = DotType.label.copy(fontSize = 13.sp, fontWeight = FontWeight.W800, letterSpacing = 1.5.sp),
                color = c.onAccent,
                modifier = Modifier.weight(1f)
            )
            Text(stringResource(R.string.watched_undo_hint), style = DotType.label.copy(fontSize = 11.sp), color = c.onAccent.copy(alpha = 0.85f), maxLines = 1)
        } else {
            DotIcon(DotIcons.Watching, 22.dp, monochrome = c.textMuted)
            Text(
                stringResource(R.string.action_mark_watched).upperCs(),
                style = DotType.label.copy(fontSize = 13.sp, fontWeight = FontWeight.W700, letterSpacing = 1.2.sp),
                color = c.text,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
