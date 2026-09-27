package com.berpod.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.berpod.app.R
import com.berpod.app.data.local.TagEntity

@Composable
fun TagAssignDialog(
    state: TagAssignState,
    onToggle: (tagId: Long, assigned: Boolean) -> Unit,
    onCreate: (name: String) -> Unit,
    onDelete: (tagId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember(state.podcastId) { mutableStateOf("") }
    var deleting by remember { mutableStateOf<TagEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.podcastTitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                state.tags.forEach { tag ->
                    val checked = tag.id in state.checkedTagIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(tag.id, it) }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(tag.name, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        IconButton(onClick = { deleting = tag }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.action_delete_tag, tag.name),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.tag_dialog_new_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            onCreate(newName)
                            newName = ""
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Text(stringResource(R.string.action_add))
                    }
                }
                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        }
    )
    deleting?.let { tag ->
        DeleteTagDialog(
            tag = tag,
            onConfirm = {
                onDelete(tag.id)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}
