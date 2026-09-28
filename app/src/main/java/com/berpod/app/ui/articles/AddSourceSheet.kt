package com.berpod.app.ui.articles

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.berpod.app.ui.dot.DotIcon
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.DotPrimaryButton
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType
import kotlinx.coroutines.launch

/** "PŘIDAT ZDROJ" bottom sheet (after the canvas' "Y5 · Přidat kanál", for article sources). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddSourceSheet(state: AddSourceState, controller: AddSourceController) {
    val c = DotTheme.colors
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var isNewCategoryOpen by remember { mutableStateOf(false) }

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
                    stringResource(R.string.add_source_title).upperCs(),
                    style = DotType.title.copy(fontSize = 22.sp, fontWeight = FontWeight.W800, letterSpacing = 2.sp),
                    color = c.text,
                    modifier = Modifier.weight(1f)
                )
                DotIconButton(DotIcons.Close, stringResource(R.string.action_close), controller::dismiss)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.add_source_label).upperCs(), style = DotType.label, color = c.textMuted)
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
                            Text(stringResource(R.string.add_source_hint), style = DotType.rowTitle.copy(fontWeight = FontWeight.W500), color = c.textMuted)
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

            LookupCard(state.lookup)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.categories).upperCs(), style = DotType.label, color = c.textMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.tags.forEach { tag ->
                        DotChip(label = tag.name, selected = tag.id in state.selectedTagIds, onClick = { controller.toggleTag(tag.id) })
                    }
                    DotChip(label = stringResource(R.string.new_category_chip), selected = false, onClick = { isNewCategoryOpen = true })
                }
            }

            state.errorMessage?.let { Text(it, style = DotType.body, color = c.accent) }

            DotPrimaryButton(
                label = stringResource(R.string.add_source_title),
                icon = null,
                onClick = controller::submit,
                enabled = state.lookup is SourceLookup.Found && !state.isSubmitting
            )
        }
    }

    if (isNewCategoryOpen) {
        NewCategoryDialog(
            onConfirm = { name ->
                controller.createTag(name)
                isNewCategoryOpen = false
            },
            onDismiss = { isNewCategoryOpen = false }
        )
    }
}

@Composable
private fun LookupCard(lookup: SourceLookup) {
    val c = DotTheme.colors
    if (lookup == SourceLookup.Idle) return
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
            SourceLookup.Searching -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), color = c.accent, strokeWidth = 2.dp)
                Text(stringResource(R.string.source_searching).upperCs(), style = DotType.label.copy(fontWeight = FontWeight.W700), color = c.textMuted)
            }
            is SourceLookup.NotFound -> {
                Text(stringResource(R.string.source_not_found).upperCs(), style = DotType.label.copy(fontWeight = FontWeight.W700), color = c.textMuted)
                Text(lookup.message, style = DotType.body, color = c.text)
            }
            is SourceLookup.Found -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(1.dp)).background(c.accent))
                    Text(stringResource(R.string.source_found).upperCs(), style = DotType.label.copy(fontWeight = FontWeight.W700), color = c.accent)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SourceBadge(lookup.feed.title)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(lookup.feed.title, style = DotType.rowTitle.copy(fontSize = 16.sp, fontWeight = FontWeight.W700), color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "RSS · " + czechCount(lookup.feed.articles.size, "článek", "články", "článků"),
                            style = DotType.label.copy(letterSpacing = 1.sp),
                            color = c.textMuted
                        )
                    }
                }
                Text(
                    lookup.feed.feedUrl.removePrefix("https://").removePrefix("http://"),
                    style = DotType.meta,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.surface).padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            SourceLookup.Idle -> Unit
        }
    }
}

@Composable
private fun NewCategoryDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_category_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.category_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
