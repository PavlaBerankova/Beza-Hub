package com.berpod.app.ui.common

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.berpod.app.ui.dot.DotIcon
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.theme.DotTheme

private const val TOGGLE_GUARD_MS = 800L

/**
 * Swipe right to toggle "played". The row springs back (the change comes from the data), so it works both
 * where played rows disappear (Nové) and where they stay (podcast detail).
 */
@Suppress("DEPRECATION")
@Composable
fun MarkPlayedSwipe(onToggle: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val currentOnToggle by rememberUpdatedState(onToggle)
    // confirmValueChange runs more than once per swipe (release and settle); toggle only once per gesture.
    var lastToggleAtMs by remember { mutableLongStateOf(0L) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val now = SystemClock.uptimeMillis()
            if (value == SwipeToDismissBoxValue.StartToEnd && now - lastToggleAtMs > TOGGLE_GUARD_MS) {
                lastToggleAtMs = now
                currentOnToggle()
            }
            false
        }
    )
    val c = DotTheme.colors
    SwipeToDismissBox(
        modifier = modifier,
        state = state,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) MarkPlayedBackground()
        }
    ) {
        Box(Modifier.background(c.background)) { content() }
    }
}

/** Accent background with a check, shown behind a row being swiped right to mark it played. */
@Composable
fun MarkPlayedBackground() {
    Box(
        modifier = Modifier.fillMaxSize().background(DotTheme.colors.accent).padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) { DotIcon(DotIcons.Check, 28.dp) }
}
