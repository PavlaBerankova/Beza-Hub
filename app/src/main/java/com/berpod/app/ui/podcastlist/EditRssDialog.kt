package com.berpod.app.ui.podcastlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.berpod.app.R
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/** Changes the address a podcast is fetched from; the episodes, tags and progress stay as they are. */
@Composable
fun EditRssDialog(
    initialUrl: String,
    isSaving: Boolean,
    errorMessage: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = DotTheme.colors
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_rss_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.edit_rss_label)) },
                    singleLine = true,
                    isError = errorMessage != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let { Text(it, style = DotType.body, color = c.accent) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(url) }, enabled = url.isNotBlank() && !isSaving) {
                Text(stringResource(if (isSaving) R.string.edit_rss_checking else R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
