package com.berpod.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berpod.app.R
import com.berpod.app.ui.dot.DotChip
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.DotPrimaryButton
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType
import kotlinx.coroutines.launch

/** Wording of the sheet: Podcasty add a podcast with štítky, Články a source with kategorie. */
data class AddFeedTexts(
    val title: String,
    val urlLabel: String,
    val urlHint: String,
    val searching: String,
    val notFound: String,
    val found: String,
    val tags: TagTexts
)

/**
 * "Přidat…" bottom sheet (after the canvas' "Y5 · Přidat kanál"): an address field that looks the feed
 * up as it is typed, a preview card ([foundContent]), tags, and the add button.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun <T> AddFeedSheet(
    state: AddFeedState<T>,
    controller: AddFeedController<T>,
    texts: AddFeedTexts,
    /** The feed's own address, shown at the bottom of the preview card. */
    feedUrlOf: (T) -> String,
    foundContent: @Composable (T) -> Unit
) {
    val c = DotTheme.colors
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var isNewTagOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = controller::dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp).size(width = 44.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(c.outline))
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 32.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    texts.title.upperCs(),
                    style = DotType.title.copy(fontSize = 22.sp, fontWeight = FontWeight.W800, letterSpacing = 2.sp),
                    color = c.text,
                    modifier = Modifier.weight(1f)
                )
                DotIconButton(DotIcons.Close, stringResource(R.string.action_close), controller::dismiss)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(texts.urlLabel.upperCs(), style = DotType.label, color = c.textMuted)
                var focused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.background)
                        .border(if (focused) 1.5.dp else 1.dp, if (focused) c.accent else c.outline, RoundedCornerShape(14.dp))
                        .padding(start = 14.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        if (state.url.isEmpty()) {
                            Text(texts.urlHint, style = DotType.rowTitle.copy(fontWeight = FontWeight.W500), color = c.textMuted)
                        }
                        BasicTextField(
                            value = state.url,
                            onValueChange = { controller.onUrlChange(it) },
                            singleLine = true,
                            textStyle = DotType.rowTitle.copy(fontWeight = FontWeight.W500, color = c.text),
                            cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { controller.onUrlChange(state.url, immediate = true) }),
                            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, c.outline, RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) {
                                scope.launch {
                                    val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                                    if (!text.isNullOrBlank()) controller.onUrlChange(text.trim(), immediate = true)
                                }
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.action_paste).upperCs(), style = DotType.label.copy(fontWeight = FontWeight.W700, letterSpacing = 1.sp), color = c.text)
                    }
                }
            }

            LookupCard(state.lookup, texts, feedUrlOf, foundContent)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(texts.tags.listTitle.upperCs(), style = DotType.label, color = c.textMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.tags.forEach { tag ->
                        DotChip(label = tag.name, selected = tag.id in state.selectedTagIds, onClick = { controller.toggleTag(tag.id) })
                    }
                    DotChip(label = texts.tags.newChip, selected = false, onClick = { isNewTagOpen = true })
                }
            }

            state.errorMessage?.let { Text(it, style = DotType.body, color = c.accent) }

            DotPrimaryButton(
                label = texts.title,
                icon = null,
                onClick = controller::submit,
                enabled = state.lookup is FeedLookup.Found && !state.isSubmitting
            )
        }
    }

    if (isNewTagOpen) {
        NewTagDialog(
            title = texts.tags.newTitle,
            nameLabel = texts.tags.nameLabel,
            onConfirm = { name ->
                controller.createTag(name)
                isNewTagOpen = false
            },
            onDismiss = { isNewTagOpen = false }
        )
    }
}

@Composable
private fun <T> LookupCard(
    lookup: FeedLookup<T>,
    texts: AddFeedTexts,
    feedUrlOf: (T) -> String,
    foundContent: @Composable (T) -> Unit
) {
    val c = DotTheme.colors
    if (lookup == FeedLookup.Idle) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.background)
            .border(1.dp, c.divider, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (lookup) {
            FeedLookup.Searching -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), color = c.accent, strokeWidth = 2.dp)
                Text(texts.searching.upperCs(), style = DotType.label.copy(fontWeight = FontWeight.W700), color = c.textMuted)
            }
            is FeedLookup.NotFound -> {
                Text(texts.notFound.upperCs(), style = DotType.label.copy(fontWeight = FontWeight.W700), color = c.textMuted)
                Text(lookup.message, style = DotType.body, color = c.text)
            }
            is FeedLookup.Found -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(1.dp)).background(c.accent))
                    Text(texts.found.upperCs(), style = DotType.label.copy(fontWeight = FontWeight.W700), color = c.accent)
                }
                foundContent(lookup.feed)
                Text(
                    feedUrlOf(lookup.feed).removePrefix("https://").removePrefix("http://"),
                    style = DotType.meta,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.surface).padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            FeedLookup.Idle -> Unit
        }
    }
}
