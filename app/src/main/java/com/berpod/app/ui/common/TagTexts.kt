package com.berpod.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.berpod.app.R

/** Wording of the tag dialogs: podcasts have "štítky", article sources "kategorie". */
data class TagTexts(
    val renameTitle: String,
    val nameLabel: String,
    val newLabel: String,
    val deleteTitle: String,
    val deleteMessage: (name: String) -> String,
    val deleteAction: (name: String) -> String
)

@Composable
fun podcastTagTexts(): TagTexts {
    val deleteMessage = stringResource(R.string.delete_tag_message)
    val deleteAction = stringResource(R.string.action_delete_tag)
    return TagTexts(
        renameTitle = stringResource(R.string.rename_tag_title),
        nameLabel = stringResource(R.string.tag_name_label),
        newLabel = stringResource(R.string.tag_dialog_new_label),
        deleteTitle = stringResource(R.string.delete_tag_title),
        deleteMessage = { deleteMessage.format(it) },
        deleteAction = { deleteAction.format(it) }
    )
}

@Composable
fun articleCategoryTexts(): TagTexts {
    val deleteMessage = stringResource(R.string.delete_category_message)
    val deleteAction = stringResource(R.string.action_delete_category)
    return TagTexts(
        renameTitle = stringResource(R.string.rename_category_title),
        nameLabel = stringResource(R.string.category_name),
        newLabel = stringResource(R.string.new_category_title),
        deleteTitle = stringResource(R.string.delete_category_title),
        deleteMessage = { deleteMessage.format(it) },
        deleteAction = { deleteAction.format(it) }
    )
}
