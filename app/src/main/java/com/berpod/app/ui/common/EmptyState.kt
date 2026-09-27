package com.berpod.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.berpod.app.ui.dot.DotIcon
import com.berpod.app.ui.dot.DotIconSpec
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

@Composable
fun EmptyState(icon: DotIconSpec, title: String, subtitle: String) {
    val c = DotTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DotIcon(icon, 56.dp)
        Text(
            text = title.upperCs(),
            style = DotType.rowTitle.copy(fontWeight = FontWeight.W700, letterSpacing = DotType.label.letterSpacing),
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = subtitle,
            style = DotType.body,
            color = c.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
