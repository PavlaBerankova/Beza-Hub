package com.berpod.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.berpod.app.R
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.theme.DotTheme

/** Queue button: filled accent with a check while queued; tapping it again takes the episode out. */
@Composable
fun QueueToggleButton(isQueued: Boolean, onToggle: () -> Unit) {
    if (isQueued) {
        DotIconButton(DotIcons.Check, stringResource(R.string.action_remove_from_queue), onToggle, radius = 12.dp, iconSize = 20.dp, filled = DotTheme.colors.accent)
    } else {
        DotIconButton(DotIcons.Queue, stringResource(R.string.action_add_to_queue), onToggle, radius = 12.dp, iconSize = 22.dp)
    }
}
