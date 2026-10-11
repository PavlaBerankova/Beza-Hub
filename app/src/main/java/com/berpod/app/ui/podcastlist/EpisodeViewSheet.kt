package com.berpod.app.ui.podcastlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berpod.app.R
import com.berpod.app.ui.dot.DotChip
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

fun EpisodeSort.labelRes(): Int = when (this) {
    EpisodeSort.NewestFirst -> R.string.section_newest
    EpisodeSort.OldestFirst -> R.string.sort_oldest
}

/** What the list under the header currently shows, for the label beside the sort button. */
fun EpisodeFilter.shownRes(): Int = when (this) {
    EpisodeFilter.All -> R.string.shown_all
    EpisodeFilter.Unplayed -> R.string.shown_unplayed
    EpisodeFilter.Played -> R.string.shown_played
}

fun EpisodeFilter.labelRes(): Int = when (this) {
    EpisodeFilter.All -> R.string.filter_everything
    EpisodeFilter.Unplayed -> R.string.filter_unplayed
    EpisodeFilter.Played -> R.string.filter_played
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SheetSection(title: String, chips: @Composable () -> Unit) {
    val c = DotTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.upperCs(), style = DotType.label, color = c.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

@Composable
internal fun SheetAction(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = DotTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .border(1.dp, c.outline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label.upperCs(), style = DotType.button.copy(fontSize = 13.sp, letterSpacing = 1.sp), color = c.text)
    }
}

/** Asks before deleting a podcast: its episodes, listening progress and offline copies go with it. */
@Composable
fun DeletePodcastDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_podcast_title)) },
        text = { Text(stringResource(R.string.delete_podcast_message, title)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** Asks before marking a whole podcast played or unplayed, since it can't be undone. */
@Composable
fun MarkAllDialog(markPlayed: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (markPlayed) R.string.mark_all_played_title else R.string.mark_all_unplayed_title)) },
        text = { Text(stringResource(if (markPlayed) R.string.mark_all_played_message else R.string.mark_all_unplayed_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_confirm_mark)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
