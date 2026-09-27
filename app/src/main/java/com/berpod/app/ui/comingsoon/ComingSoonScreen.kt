package com.berpod.app.ui.comingsoon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.berpod.app.R
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.dot.DotIconSpec
import com.berpod.app.ui.dot.DotTitle
import com.berpod.app.ui.theme.DotTheme

/** Placeholder for a tab that isn't built yet (YouTube, Články). */
@Composable
fun ComingSoonScreen(title: String, icon: DotIconSpec) {
    Column(modifier = Modifier.fillMaxSize().background(DotTheme.colors.background).statusBarsPadding()) {
        DotTitle(title, Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp))
        EmptyState(
            icon = icon,
            title = stringResource(R.string.coming_soon_title),
            subtitle = stringResource(R.string.coming_soon_subtitle)
        )
    }
}
