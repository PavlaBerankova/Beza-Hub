package com.berpod.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berpod.app.R
import com.berpod.app.data.local.TagEntity
import com.berpod.app.ui.dot.DotChip
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.DotPrimaryButton
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/**
 * Picks the tags of one podcast or article source, in the same sheet as "add a feed": the feed on a
 * card, its tags as chips (tap toggles, long press deletes), a "+ new" chip and a done button.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagPickerSheet(
    title: String,
    subtitle: String?,
    texts: TagTexts,
    tags: List<TagEntity>,
    checkedTagIds: Set<Long>,
    onToggle: (tagId: Long, assigned: Boolean) -> Unit,
    onCreate: (name: String) -> Unit,
    onDelete: (tagId: Long) -> Unit,
    errorMessage: String?,
    onDismiss: () -> Unit,
    badge: @Composable () -> Unit
) {
    val c = DotTheme.colors
    var isNewTagOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TagEntity?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                    texts.listTitle.upperCs(),
                    style = DotType.title.copy(fontSize = 22.sp, fontWeight = FontWeight.W800, letterSpacing = 2.sp),
                    color = c.text,
                    modifier = Modifier.weight(1f)
                )
                DotIconButton(DotIcons.Close, stringResource(R.string.action_close), onDismiss)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.background)
                    .border(1.dp, c.divider, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                badge()
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        title,
                        style = DotType.rowTitle.copy(fontSize = 16.sp, fontWeight = FontWeight.W700),
                        color = c.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = DotType.label.copy(letterSpacing = 1.sp),
                            color = c.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    var isMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        val checked = tag.id in checkedTagIds
                        DotChip(
                            label = tag.name,
                            selected = checked,
                            onClick = { onToggle(tag.id, !checked) },
                            onLongClick = { isMenuOpen = true }
                        )
                        DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = {
                                    isMenuOpen = false
                                    deleting = tag
                                }
                            )
                        }
                    }
                }
                DotChip(label = texts.newChip, selected = false, onClick = { isNewTagOpen = true })
            }

            errorMessage?.let { Text(it, style = DotType.body, color = c.accent) }

            DotPrimaryButton(label = stringResource(R.string.action_done), icon = null, onClick = onDismiss)
        }
    }

    if (isNewTagOpen) {
        NewTagDialog(
            title = texts.newTitle,
            nameLabel = texts.nameLabel,
            onConfirm = {
                onCreate(it)
                isNewTagOpen = false
            },
            onDismiss = { isNewTagOpen = false }
        )
    }
    deleting?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(texts.deleteTitle) },
            text = { Text(texts.deleteMessage(tag.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(tag.id)
                    deleting = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

/** Asks for a new tag's name; shared by the tag picker and the "add a feed" sheet. */
@Composable
fun NewTagDialog(title: String, nameLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(nameLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
