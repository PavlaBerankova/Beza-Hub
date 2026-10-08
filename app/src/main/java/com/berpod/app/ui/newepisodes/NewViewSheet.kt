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
