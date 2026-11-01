package com.berpod.app.ui.podcastlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import com.berpod.app.R
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/** Adds or changes a podcast's own link: a name saying where it leads (optional) and the address. */
@Composable
fun EditLinkDialog(
    initialTitle: String,
    initialUrl: String,
    errorMessage: String?,
    onSave: (title: String, url: String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = DotTheme.colors
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_link_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.edit_link_name_label)) },
                    placeholder = { Text(stringResource(R.string.edit_link_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.edit_link_url_label)) },
                    singleLine = true,
                    isError = errorMessage != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                errorMessage?.let { Text(it, style = DotType.body, color = c.accent) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, url) }, enabled = url.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
