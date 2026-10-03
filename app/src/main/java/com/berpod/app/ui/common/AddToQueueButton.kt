package com.berpod.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.berpod.app.R
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons

/** "Add to queue"; hidden once the episode is queued (the queue itself is where it's taken out). */
@Composable
fun AddToQueueButton(isQueued: Boolean, onAdd: () -> Unit) {
    if (!isQueued) DotIconButton(DotIcons.AddToQueue, stringResource(R.string.action_add_to_queue), onAdd, radius = 12.dp, iconSize = 22.dp)
}
