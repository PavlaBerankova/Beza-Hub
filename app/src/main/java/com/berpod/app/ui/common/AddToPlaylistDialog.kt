package com.berpod.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.berpod.app.R
import com.berpod.app.data.local.PlaylistEntity

@Composable
fun AddToPlaylistDialog(
    playlists: List<PlaylistEntity>,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onAddToExisting: (playlistId: Long) -> Unit,
    onCreateNew: (name: String) -> Unit
) {
    var isCreatingNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(stringResource(R.string.add_to_playlist_dialog_title)) },
        text = {
            Column {
                playlists.forEach { playlist ->
                    Text(
                        text = playlist.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isSubmitting) { onAddToExisting(playlist.id) }
                            .padding(vertical = 12.dp)
                    )
                }
                if (isCreatingNew) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        label = { Text(stringResource(R.string.create_playlist_name_label)) },
                        enabled = !isSubmitting,
                        singleLine = true
                    )
                } else {
                    Text(
                        text = stringResource(R.string.action_new_playlist_inline),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isSubmitting) { isCreatingNew = true }
                            .padding(vertical = 12.dp)
                    )
                }
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
            if (isCreatingNew) {
                TextButton(
                    onClick = { onCreateNew(newName) },
                    enabled = newName.isNotBlank() && !isSubmitting
                ) {
                    Text(stringResource(R.string.action_add))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
