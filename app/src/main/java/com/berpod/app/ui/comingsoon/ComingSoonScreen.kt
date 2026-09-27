package com.berpod.app.ui.comingsoon

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.berpod.app.R
import com.berpod.app.ui.common.EmptyState

/** Placeholder for a tab that isn't built yet (YouTube, Články). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComingSoonScreen(title: String, icon: ImageVector) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        )
        EmptyState(
            icon = icon,
            title = stringResource(R.string.coming_soon_title),
            subtitle = stringResource(R.string.coming_soon_subtitle)
        )
    }
}
