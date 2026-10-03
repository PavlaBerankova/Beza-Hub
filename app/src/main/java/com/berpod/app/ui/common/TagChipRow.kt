package com.berpod.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.berpod.app.R
import com.berpod.app.data.local.TagEntity
import com.berpod.app.ui.dot.DotChip

private val ChipShape = RoundedCornerShape(8.dp)

/**
 * Single-select tag filter row. Pass [onRenameRequest]/[onDeleteRequest] to offer a long-press
 * menu on tag chips; leave both null for a filter-only row.
 */
@Composable
fun TagChipRow(
    tags: List<TagEntity>,
    selectedTagId: Long?,
    onSelect: (Long?) -> Unit,
    onRenameRequest: ((TagEntity) -> Unit)? = null,
    onDeleteRequest: ((TagEntity) -> Unit)? = null
) {
    val hasMenu = onRenameRequest != null || onDeleteRequest != null
    val listState = rememberLazyListState()
    // "Vše" is item 0, the tags follow. A selection that has scrolled out of sight (or a row that starts at the
    // left again after leaving the screen) is brought back into view, so the highlighted chip is always visible.
    val selectedIndex = if (selectedTagId == null) 0 else (tags.indexOfFirst { it.id == selectedTagId } + 1).coerceAtLeast(0)
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(selectedIndex, tags.size) {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        val fullyVisible = item != null && item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset
        if (fullyVisible) {
            placed = true
            return@LaunchedEffect
        }
        // One chip of context on the left; the first time without animation so the row simply opens in place.
        val target = (selectedIndex - 1).coerceAtLeast(0)
        if (placed) listState.animateScrollToItem(target) else listState.scrollToItem(target)
        placed = true
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") {
            DotChip(
                label = stringResource(R.string.filter_all),
                selected = selectedTagId == null,
                onClick = { onSelect(null) }
            )
        }
        items(tags, key = { it.id }) { tag ->
            var isMenuOpen by remember { mutableStateOf(false) }
            Box {
                DotChip(
                    label = tag.name,
                    selected = selectedTagId == tag.id,
                    onClick = { onSelect(tag.id) },
                    onLongClick = if (hasMenu) ({ isMenuOpen = true }) else null
                )
                if (hasMenu) {
                    DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                        if (onRenameRequest != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_rename)) },
                                onClick = {
                                    isMenuOpen = false
                                    onRenameRequest(tag)
                                }
                            )
                        }
                        if (onDeleteRequest != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = {
                                    isMenuOpen = false
                                    onDeleteRequest(tag)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenameTagDialog(tag: TagEntity, errorMessage: String?, onConfirm: (String) -> Unit, onDismiss: () -> Unit, texts: TagTexts = podcastTagTexts()) {
    var name by remember(tag.id) { mutableStateOf(tag.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.renameTitle) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(texts.nameLabel) },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun DeleteTagDialog(tag: TagEntity, onConfirm: () -> Unit, onDismiss: () -> Unit, texts: TagTexts = podcastTagTexts()) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.deleteTitle) },
        text = { Text(texts.deleteMessage(tag.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
