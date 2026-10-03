package com.berpod.app.ui.newepisodes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berpod.app.R
import com.berpod.app.ui.dot.DotChip
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.podcastlist.EpisodeSort
import com.berpod.app.ui.podcastlist.SheetAction
import com.berpod.app.ui.podcastlist.SheetSection
import com.berpod.app.ui.podcastlist.labelRes
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

fun NewPeriod.shortRes() = when (this) {
    NewPeriod.All -> R.string.period_all
    NewPeriod.Today -> R.string.period_today
    NewPeriod.Week -> R.string.period_week
    NewPeriod.TwoWeeks -> R.string.period_two_weeks
    NewPeriod.Month -> R.string.period_month
}

fun NewPeriod.titleRes() = when (this) {
    NewPeriod.All -> R.string.new_period_all_title
    NewPeriod.Today -> R.string.new_period_today_title
    NewPeriod.Week -> R.string.new_period_week_title
    NewPeriod.TwoWeeks -> R.string.new_period_two_weeks_title
    NewPeriod.Month -> R.string.new_period_month_title
}

/** "Filtr a řazení" sheet of Nové: how far back, the order, and taking everything listed out of Nové. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewViewSheet(
    period: NewPeriod,
    sort: EpisodeSort,
    hasEpisodes: Boolean,
    onPeriodChange: (NewPeriod) -> Unit,
    onSortChange: (EpisodeSort) -> Unit,
    onRemoveAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = DotTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp).size(width = 44.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(c.outline))
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 32.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.new_view_title).upperCs(),
                    style = DotType.title.copy(fontSize = 22.sp, fontWeight = FontWeight.W800, letterSpacing = 2.sp),
                    color = c.text,
                    modifier = Modifier.weight(1f)
                )
                DotIconButton(DotIcons.Close, stringResource(R.string.action_close), onDismiss)
            }
            SheetSection(stringResource(R.string.new_period_label)) {
                NewPeriod.entries.forEach { DotChip(stringResource(it.shortRes()), selected = period == it, onClick = { onPeriodChange(it) }) }
            }
            SheetSection(stringResource(R.string.episodes_sort_label)) {
                EpisodeSort.entries.forEach { DotChip(stringResource(it.labelRes()), selected = sort == it, onClick = { onSortChange(it) }) }
            }
            SheetAction(stringResource(R.string.action_remove_all_new), enabled = hasEpisodes, onClick = onRemoveAll, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Asks before taking everything Nové lists out of it (it is marked played), naming how many that is. */
@Composable
fun RemoveAllNewDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remove_all_new_title)) },
        text = { Text("Označí se jako přehrané: $count. Jejich rozposlechnutý postup se vynuluje.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_confirm_remove)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
