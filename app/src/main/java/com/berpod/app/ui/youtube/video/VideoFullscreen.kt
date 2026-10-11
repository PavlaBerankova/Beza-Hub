package com.berpod.app.ui.youtube.video

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

/**
 * Whether the video page is in fullscreen. The app's frame reads it too, to take away what must not be on screen then
 * (the mini player and the bottom tabs) and the insets around the content.
 */
val LocalVideoFullscreen = compositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }
