package com.berpod.app.ui.articles

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.ui.common.htmlText
import com.berpod.app.ui.dot.DotIcon
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.CenteredSeparator
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.formatDotDate
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType
import com.berpod.app.ui.theme.Literata

/** Article reader (canvas "C2 · Článek"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArticleReaderScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    onOpenArticle: (Long) -> Unit
) {
    val viewModel: ArticleReaderViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val c = DotTheme.colors
    val uriHandler = LocalUriHandler.current
    val link = uiState.item?.article?.link

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DotIconButton(DotIcons.Back, stringResource(R.string.action_back), onBack)
            Spacer(Modifier.weight(1f))
            DotIconButton(DotIcons.Back, stringResource(R.string.action_font_size), viewModel::onCycleFontSize) {
                Text("Aa", style = DotType.reading.copy(fontSize = 17.sp, fontWeight = FontWeight.W600), color = c.text)
            }
            if (link != null) {
                DotIconButton(DotIcons.OpenExternal, stringResource(R.string.action_open_in_browser), { uriHandler.openUri(link) })
            }
        }

        val item = uiState.item
        if (item == null) {
            if (uiState.isLoaded) {
                Text(stringResource(R.string.article_missing), style = DotType.body, color = c.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 48.dp))
            }
            return@Column
        }
        val article = item.article
        val body = remember(article.content, article.summary) { htmlText(article.content ?: article.summary, c.accent) }
        val minutes = remember(article.content, article.summary) { readingMinutes(article.content ?: article.summary) }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.feedTitle.upperCs(), style = DotType.label, color = c.accent)
                listOfNotNull(formatDotDate(article.pubDateMillis), minutes?.let { stringResource(R.string.reading_minutes, it).upperCs() }).forEach {
                    Text("·", style = DotType.label, color = c.textMuted)
                    Text(it, style = DotType.label, color = c.textMuted)
                }
            }
            Text(
                article.title,
                style = DotType.reading.copy(fontSize = 30.sp, lineHeight = 37.sp, fontWeight = FontWeight.W700),
                color = c.text,
                modifier = Modifier.semantics { heading() }
            )
            if (uiState.categoryNames.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.categoryNames.forEach { name ->
                        Box(
                            modifier = Modifier.height(28.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, c.outline, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(name.upperCs(), style = DotType.label.copy(letterSpacing = 1.sp), color = c.textStrong) }
                    }
                }
            }
        }
        HorizontalDivider(color = c.divider)
        if (body != null) {
            Text(
                body,
                style = DotType.reading.copy(fontFamily = Literata, fontSize = uiState.fontSize.sp, lineHeight = (uiState.fontSize * 1.7).sp),
                color = c.text
            )
        }
        // Only the opening came with the feed: the rest is on the web.
        if (article.content == null && link != null) {
            OutlinedWideButton(stringResource(R.string.read_full_in_browser), DotIcons.OpenExternal) { uriHandler.openUri(link) }
        }
        CenteredSeparator(stringResource(R.string.end_of_article), Modifier.padding(top = 8.dp))
        uiState.nextUnreadId?.let { nextId ->
            OutlinedWideButton(stringResource(R.string.next_unread), DotIcons.Forward, iconAfter = true) { onOpenArticle(nextId) }
        }
    }
}

@Composable
private fun OutlinedWideButton(label: String, icon: com.berpod.app.ui.dot.DotIconSpec, iconAfter: Boolean = false, onClick: () -> Unit) {
    val c = DotTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).clip(shape).border(1.dp, c.outline, shape).clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!iconAfter) { DotIcon(icon, 18.dp); Spacer(Modifier.width(10.dp)) }
        Text(label.upperCs(), style = DotType.button.copy(fontSize = 13.sp), color = c.text)
        if (iconAfter) { Spacer(Modifier.width(10.dp)); DotIcon(icon, 18.dp) }
    }
}
