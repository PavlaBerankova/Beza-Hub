package com.berpod.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berpod.app.R
import com.berpod.app.data.local.TagEntity
import com.berpod.app.ui.dot.DotChip
import com.berpod.app.ui.dot.DotIcon
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class TagEditorState(
    /** Every tag of the section, in the user's order (also those no source carries yet). */
    val tags: List<TagEntity> = emptyList(),
    val renaming: TagEntity? = null,
    val renameError: String? = null,
    val deleting: TagEntity? = null
)

/**
 * The editor of all tags of one section (podcasts, YouTube, articles): add, rename, delete and put them in order.
 * State is null while the editor is closed.
 */
class TagEditorController(
    private val scope: CoroutineScope,
    private val observeTags: () -> Flow<List<TagEntity>>,
    private val create: suspend (name: String) -> Result<Long>,
    private val rename: suspend (tagId: Long, name: String) -> Result<Unit>,
    private val delete: suspend (tagId: Long) -> Unit,
    private val reorder: suspend (tagIds: List<Long>) -> Unit
) {
    private val _state = MutableStateFlow<TagEditorState?>(null)
    val state: StateFlow<TagEditorState?> = _state.asStateFlow()
    private var tagsJob: Job? = null

    fun open() {
        _state.value = TagEditorState()
        tagsJob?.cancel()
        tagsJob = observeTags().onEach { tags -> _state.update { it?.copy(tags = tags) } }.launchIn(scope)
    }

    fun dismiss() {
        tagsJob?.cancel()
        _state.value = null
    }

    fun onCreate(name: String) {
        scope.launch { create(name) }
    }

    fun onRequestRename(tag: TagEntity) = _state.update { it?.copy(renaming = tag, renameError = null) }

    fun onDismissRename() = _state.update { it?.copy(renaming = null, renameError = null) }

    fun onConfirmRename(name: String) {
        val tag = _state.value?.renaming ?: return
        scope.launch {
            rename(tag.id, name)
                .onSuccess { _state.update { it?.copy(renaming = null, renameError = null) } }
                .onFailure { e -> _state.update { it?.copy(renameError = e.message) } }
        }
    }

    fun onRequestDelete(tag: TagEntity) = _state.update { it?.copy(deleting = tag) }

    fun onDismissDelete() = _state.update { it?.copy(deleting = null) }

    fun onConfirmDelete() {
        val tag = _state.value?.deleting ?: return
        _state.update { it?.copy(deleting = null) }
        scope.launch { delete(tag.id) }
    }

    /** The order after a drag has ended, first to last. */
    fun onReorder(tagIds: List<Long>) {
        scope.launch { reorder(tagIds) }
    }
}

/** The "⋮" button of a section's title row; its menu opens the tag editor. */
@Composable
fun TagMenuButton(texts: TagTexts, onEdit: () -> Unit) {
    var isOpen by remember { mutableStateOf(false) }
    Box {
        DotIconButton(DotIcons.More, stringResource(R.string.action_more_options), onClick = { isOpen = true })
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            DropdownMenuItem(
                text = { Text(texts.editTitle) },
                onClick = {
                    isOpen = false
                    onEdit()
                }
            )
        }
    }
}

/** The editor itself, as a bottom sheet, and the dialogs it opens. Draws nothing while the editor is closed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditorHost(controller: TagEditorController, texts: TagTexts) {
    val state by controller.state.collectAsState()
    val current = state ?: return
    val c = DotTheme.colors
    var isNewOpen by remember { mutableStateOf(false) }

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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    texts.editTitle.upperCs(),
                    style = DotType.title.copy(fontSize = 22.sp, fontWeight = FontWeight.W800, letterSpacing = 2.sp),
                    color = c.text,
                    modifier = Modifier.weight(1f)
                )
                DotIconButton(DotIcons.Close, stringResource(R.string.action_close), controller::dismiss)
            }
            if (current.tags.isEmpty()) {
                Text(stringResource(R.string.tag_editor_empty), style = DotType.body, color = c.textMuted, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                Text(stringResource(R.string.tag_editor_hint), style = DotType.body.copy(fontSize = 13.sp), color = c.textMuted)
                ReorderableTagList(current.tags, controller, Modifier.heightIn(max = 380.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DotChip(label = texts.newChip, selected = false, onClick = { isNewOpen = true })
                Box(Modifier.weight(1f))
                DotChip(label = stringResource(R.string.action_done), selected = true, onClick = controller::dismiss)
            }
        }
    }

    if (isNewOpen) {
        NewTagDialog(
            title = texts.newTitle,
            nameLabel = texts.nameLabel,
            onConfirm = {
                controller.onCreate(it)
                isNewOpen = false
            },
            onDismiss = { isNewOpen = false }
        )
    }
    current.renaming?.let { tag ->
        RenameTagDialog(tag, current.renameError, controller::onConfirmRename, controller::onDismissRename, texts)
    }
    current.deleting?.let { tag ->
        DeleteTagDialog(tag, controller::onConfirmDelete, controller::onDismissDelete, texts)
    }
}

@Composable
private fun ReorderableTagList(tags: List<TagEntity>, controller: TagEditorController, modifier: Modifier = Modifier) {
    val c = DotTheme.colors
    val haptics = LocalHapticFeedback.current
    // Reordered live while dragging; the database is written once, when the finger lifts.
    var local by remember { mutableStateOf(tags) }
    var dragging by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(tags)
    LaunchedEffect(tags) { if (!dragging) local = tags }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        local = local.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    LazyColumn(state = listState, modifier = modifier) {
        items(local, key = { it.id }) { tag ->
            ReorderableItem(reorderState, key = tag.id) { _ ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .draggableHandle(
                                    onDragStarted = {
                                        dragging = true
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        dragging = false
                                        val ids = local.map { it.id }
                                        if (ids != latest.map { it.id }) controller.onReorder(ids) else local = latest
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            DotIcon(DotIcons.Drag, 22.dp)
                        }
                        Text(
                            tag.name,
                            style = DotType.rowTitle.copy(fontWeight = FontWeight.W600),
                            color = c.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.action_rename)) { controller.onRequestRename(tag) }
                                .padding(horizontal = 8.dp, vertical = 14.dp)
                        )
                        DotIconButton(DotIcons.Trash, stringResource(R.string.action_delete), onClick = { controller.onRequestDelete(tag) })
                    }
                    HorizontalDivider(color = c.divider)
                }
            }
        }
    }
}
