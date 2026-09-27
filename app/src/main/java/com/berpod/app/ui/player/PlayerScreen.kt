package com.berpod.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.berpod.app.R
import com.berpod.app.ui.common.formatPlaybackTime
import com.berpod.app.ui.dot.CellRole
import com.berpod.app.ui.dot.DotIcon
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.ringDots
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.dot.waveHeights
import com.berpod.app.ui.dot.waveSeekFraction
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/** Dot Matrix player (step 1): dot ring around the cover, seekable decorative wave, times and play/pause. */
@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val c = DotTheme.colors
    var draggingFraction by remember { mutableStateOf<Float?>(null) }
    val known = state.durationMs > 0L
    val duration = state.durationMs.coerceAtLeast(1L)
    // Gesture handlers are installed once; they read the latest duration through this.
    val currentDuration by rememberUpdatedState(state.durationMs)
    val fraction = draggingFraction ?: if (known) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val heights = remember(state.currentEpisodeId) { waveHeights(state.currentEpisodeId ?: 0L) }

    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DotIconButton(DotIcons.CloseDown, stringResource(R.string.action_close_player), onBack)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                DotIcon(DotIcons.WaveGlyph, 14.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.now_playing).upperCs(), style = DotType.chip.copy(fontWeight = FontWeight.W700, letterSpacing = 2.5.sp), color = c.text)
            }
            Spacer(Modifier.size(44.dp))
        }
        Box(Modifier.padding(top = 18.dp).size(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val scale = size.width / 280f
                ringDots(56, center = 140f, radius = 132f, fraction = fraction).forEach { dot ->
                    drawCircle(
                        color = when (dot.role) { CellRole.Played -> c.accent; CellRole.Current -> c.text; CellRole.Rest -> c.ringIdle },
                        radius = 3f * scale,
                        center = Offset(dot.x * scale, dot.y * scale)
                    )
                }
            }
            AsyncImage(
                model = state.artworkUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(210.dp).clip(CircleShape).border(1.dp, c.divider, CircleShape).background(c.divider)
            )
        }
        Text(state.title, style = DotType.title.copy(fontSize = 20.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 18.dp))
        Text(state.podcastTitle.upperCs(), style = DotType.chip.copy(letterSpacing = 2.sp), color = c.textMuted, modifier = Modifier.padding(top = 6.dp))
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
        DotIconButton(
            if (state.isPlaying) DotIcons.PauseLarge else DotIcons.PlayLarge,
            stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
            viewModel::togglePlayPause,
            modifier = Modifier.padding(top = 24.dp),
            size = 78.dp, radius = 24.dp, iconSize = 36.dp, accentBorder = true, filled = c.surface
        )
    }
}
