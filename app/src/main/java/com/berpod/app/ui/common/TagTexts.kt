package com.berpod.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.berpod.app.R

/** Wording of the tag screens: podcasts carry "štítky", article sources "kategorie". */
data class TagTexts(
    /** Section heading above the chips. */
    val listTitle: String,
    /** The chip that opens "new tag". */
    val newChip: String,
    val newTitle: String,
    val nameLabel: String,
    val renameTitle: String,
    val deleteTitle: String,
    val deleteMessage: (name: String) -> String,
    val deleteAction: (name: String) -> String
)

@Composable
fun podcastTagTexts(): TagTexts {
    val deleteMessage = stringResource(R.string.delete_tag_message)
    val deleteAction = stringResource(R.string.action_delete_tag)
    return TagTexts(
        listTitle = stringResource(R.string.tags),
        newChip = stringResource(R.string.new_tag_chip),
        newTitle = stringResource(R.string.new_tag_title),
        nameLabel = stringResource(R.string.tag_name_label),
        renameTitle = stringResource(R.string.rename_tag_title),
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
        listTitle = stringResource(R.string.categories),
        newChip = stringResource(R.string.new_category_chip),
        newTitle = stringResource(R.string.new_category_title),
        nameLabel = stringResource(R.string.category_name),
        renameTitle = stringResource(R.string.rename_category_title),
        deleteTitle = stringResource(R.string.delete_category_title),
        deleteMessage = { deleteMessage.format(it) },
        deleteAction = { deleteAction.format(it) }
    )
}

@Composable
fun youtubeCategoryTexts(): TagTexts {
    val deleteMessage = stringResource(R.string.delete_channel_category_message)
    val deleteAction = stringResource(R.string.action_delete_channel_category)
    return TagTexts(
        listTitle = stringResource(R.string.categories),
        newChip = stringResource(R.string.new_category_chip),
        newTitle = stringResource(R.string.new_category_title),
        nameLabel = stringResource(R.string.category_name),
        renameTitle = stringResource(R.string.rename_channel_category_title),
        deleteTitle = stringResource(R.string.delete_channel_category_title),
        deleteMessage = { deleteMessage.format(it) },
        deleteAction = { deleteAction.format(it) }
    )
}
