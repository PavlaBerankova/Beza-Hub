package com.berpod.app.ui.dot

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.berpod.app.R
import com.berpod.app.ui.theme.LocalThemeController

/** Flips the app between light and dark: a moon while it is light, a sun while it is dark. */
@Composable
fun ThemeToggleButton() {
    val theme = LocalThemeController.current
    DotIconButton(
        icon = if (theme.isDark) DotIcons.Sun else DotIcons.Sleep,
        contentDescription = stringResource(if (theme.isDark) R.string.theme_to_light else R.string.theme_to_dark),
        onClick = theme.toggle
    )
}
