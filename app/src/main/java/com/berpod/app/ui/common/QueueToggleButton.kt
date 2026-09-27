package com.berpod.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.berpod.app.R
import com.berpod.app.ui.dot.DotIcon
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.theme.DotTheme

/** Queue button: filled accent while queued; tapping it again takes the episode out. */
@Composable
fun QueueToggleButton(isQueued: Boolean, onToggle: () -> Unit) {
    if (isQueued) {
        val c = DotTheme.colors
        DotIconButton(DotIcons.Queue, stringResource(R.string.action_remove_from_queue), onToggle, radius = 12.dp, filled = c.accent) {
            DotIcon(DotIcons.Queue, 22.dp, monochrome = c.onAccent)
        }
    } else {
        DotIconButton(DotIcons.Queue, stringResource(R.string.action_add_to_queue), onToggle, radius = 12.dp, iconSize = 22.dp)
    }
}
