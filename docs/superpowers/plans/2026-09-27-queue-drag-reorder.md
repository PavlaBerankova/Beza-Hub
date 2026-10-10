# Queue Drag Reorder and Artwork Rows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show podcast artwork instead of the podcast name in Queue and New Episodes rows, and replace the Queue's up/down arrows with drag-and-drop via a left drag handle.

**Architecture:** A pure `reorderQueue` computes the full new queue order from the stored order, the moved id and its visible neighbours; `QueueDao.moveEntry` runs it inside a transaction and rewrites positions 0..n-1. The Queue screen uses `sh.calvin.reorderable` to reorder a local copy of the visible list during a drag and commits once on drop. `EpisodeListItem` gains an optional artwork and a leading slot.

**Tech Stack:** Kotlin 2.4, Jetpack Compose (BOM 2026.09.00, Material 3, material-icons-extended), Coil 3, Room 2.8.5, `sh.calvin.reorderable:reorderable` 3.1.0, JUnit 4 + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-27-queue-drag-reorder-design.md`

## Global Constraints

- All user-visible text is Czech in `app/src/main/res/values/strings.xml`.
- Artwork: 56dp square, 8dp corners, `ContentScale.Crop`, fallback `surfaceVariant` square with centred `Icons.Filled.Podcasts`; `contentDescription = null`.
- Queue row: `[handle] [artwork] [date · duration / title / progress] [play] [✕]`; no podcast-name line; no arrows.
- New Episodes row: artwork added, podcast-name line removed, buttons unchanged. Podcast Episodes rows unchanged.
- Drag starts immediately when the ⠿ handle is pressed and moved (`draggableHandle`); nothing is written until drop; one `moveEntry` write per changed drop.
- Filtered queue: moved entry goes right after its visible predecessor, or right before its visible successor when dropped at the top; hidden entries keep relative order.
- Positions are always 0..n-1 and unique after a move; the new order is computed inside the DB transaction.
- Touch targets ≥ 48dp. Branch `feature/podcast-tags`. Tests: `./gradlew :app:testDebugUnitTest`; build: `./gradlew :app:assembleDebug`; run from the repo root.

## Review Focus

1. The queue query re-emits while an episode plays (playback position is persisted every few seconds); that emission must not reset the list mid-drag — handled in Task 3 (sync local list only while not dragging), checked manually by dragging during playback.
2. Dropping an item back where it started must not write anything — pinned in Task 1 (`anchorsFor` + unchanged-order check) and Task 3 (compare before/after order).
3. A move issued from a stale list (quick repeated drags, or an entry removed meanwhile) must keep positions unique and not crash — pinned in Task 1 (`moveEntry with stale anchors keeps positions 0..n-1`, `unknown moved id leaves order unchanged`).
4. Dragging in a filtered queue must keep hidden entries in their relative order — pinned in Task 1 (`filtered move keeps hidden entries in relative order`), checked manually.
5. TalkBack users must still be able to reorder without dragging — Task 3 adds "Posunout nahoru/dolů" custom actions backed by the Task 1 `onMoveUp`/`onMoveDown`.

---

### Task 1: Race-safe queue ordering

**Files:**
- Create: `app/src/main/java/com/berpod/app/data/local/QueueOrder.kt`
- Modify: `app/src/main/java/com/berpod/app/data/local/QueueDao.kt`
- Modify: `app/src/main/java/com/berpod/app/data/repository/QueueRepository.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/queue/QueueViewModel.kt`
- Test: `app/src/test/java/com/berpod/app/data/local/QueueOrderTest.kt`
- Test: `app/src/test/java/com/berpod/app/data/repository/QueueRepositoryTest.kt` (rewrite)

**Interfaces:**
- Consumes: `QueueItemWithEpisode.queueEntryId`, `QueueUiState.visibleItems` (existing); test helper `queueItem(entryId, position, feedId)` in `com.berpod.app.testutil`.
- Produces:
  - `fun reorderQueue(currentOrder: List<Long>, movedId: Long, afterId: Long?, beforeId: Long?): List<Long>` (package `com.berpod.app.data.local`)
  - `fun anchorsFor(order: List<Long>, movedId: Long): Pair<Long?, Long?>` — (`afterId`, `beforeId`) = neighbours of `movedId` in `order`; `(null, null)` if absent
  - `QueueDao.getOrderedIds(): List<Long>`, `QueueDao.moveEntry(movedId: Long, afterId: Long?, beforeId: Long?)` (`@Transaction` default method)
  - `QueueRepository.moveEntry(movedId: Long, afterId: Long?, beforeId: Long?)`
  - `QueueViewModel.onMoveEntry(movedId: Long, afterId: Long?, beforeId: Long?)`; `onMoveUp(index: Int)` / `onMoveDown(index: Int)` kept, now implemented with `moveEntry`
- Removed: `QueueRepository.moveItem`, `QueueDao.swapEntries`, `QueueDao.getPosition`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/berpod/app/data/local/QueueOrderTest.kt`:

```kotlin
package com.berpod.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueOrderTest {

    private val order = listOf(1L, 2L, 3L, 4L, 5L)

    @Test
    fun `moves an entry down after its new predecessor`() {
        assertEquals(listOf(2L, 3L, 1L, 4L, 5L), reorderQueue(order, movedId = 1, afterId = 3, beforeId = 4))
    }

    @Test
    fun `moves an entry up after its new predecessor`() {
        assertEquals(listOf(1L, 4L, 2L, 3L, 5L), reorderQueue(order, movedId = 4, afterId = 1, beforeId = 2))
    }

    @Test
    fun `drop at the top of the visible list goes right before the successor`() {
        // Visible (filtered) entries are 2, 4, 5; 5 is dropped above 2.
        assertEquals(listOf(1L, 5L, 2L, 3L, 4L), reorderQueue(order, movedId = 5, afterId = null, beforeId = 2))
    }

    @Test
    fun `filtered move keeps hidden entries in relative order`() {
        // Visible entries are 1, 3, 5 (2 and 4 hidden); 1 is dropped between 3 and 5.
        val result = reorderQueue(order, movedId = 1, afterId = 3, beforeId = 5)
        assertEquals(listOf(2L, 3L, 1L, 4L, 5L), result)
        assertEquals(listOf(2L, 4L), result.filter { it == 2L || it == 4L })
    }

    @Test
    fun `unknown moved id leaves order unchanged`() {
        assertEquals(order, reorderQueue(order, movedId = 99, afterId = 1, beforeId = 2))
    }

    @Test
    fun `unknown anchors leave order unchanged`() {
        assertEquals(order, reorderQueue(order, movedId = 3, afterId = 98, beforeId = 99))
        assertEquals(order, reorderQueue(order, movedId = 3, afterId = null, beforeId = null))
    }

    @Test
    fun `anchorsFor returns the neighbours in the given order`() {
        assertEquals(3L to 4L, anchorsFor(listOf(2L, 3L, 1L, 4L), movedId = 1))
        assertEquals(null to 2L, anchorsFor(listOf(1L, 2L), movedId = 1))
        assertEquals(1L to null, anchorsFor(listOf(1L, 2L), movedId = 2))
        assertEquals(null to null, anchorsFor(listOf(1L, 2L), movedId = 9))
    }
}
```

Replace `app/src/test/java/com/berpod/app/data/repository/QueueRepositoryTest.kt` with:

```kotlin
package com.berpod.app.data.repository

import com.berpod.app.data.local.QueueDao
import com.berpod.app.data.local.QueueEntryEntity
import com.berpod.app.data.local.QueueItemWithEpisode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Stores positions per queue entry id; inherits the real moveEntry transaction logic. */
private class RecordingQueueDao(initial: Map<Long, Int>) : QueueDao {
    val positions = initial.toMutableMap()

    override fun observeQueue(): Flow<List<QueueItemWithEpisode>> = emptyFlow()
    override suspend fun getMaxPosition(): Int = positions.values.maxOrNull() ?: -1
    override suspend fun isQueued(episodeId: Long): Boolean = false
    override suspend fun insert(entry: QueueEntryEntity): Long = entry.id
    override suspend fun deleteById(queueEntryId: Long) {
        positions.remove(queueEntryId)
    }
    override suspend fun updatePosition(queueEntryId: Long, position: Int) {
        positions[queueEntryId] = position
    }
    override suspend fun getOrderedIds(): List<Long> = positions.entries.sortedBy { it.value }.map { it.key }

    fun order(): List<Long> = positions.entries.sortedBy { it.value }.map { it.key }
}

class QueueRepositoryTest {

    @Test
    fun `moveEntry stores the new order as positions 0 to n-1`() = runTest {
        val dao = RecordingQueueDao(mapOf(10L to 0, 11L to 1, 12L to 2))
        QueueRepository(dao).moveEntry(movedId = 10, afterId = 12, beforeId = null)

        assertEquals(listOf(11L, 12L, 10L), dao.order())
        assertEquals(setOf(0, 1, 2), dao.positions.values.toSet())
    }

    @Test
    fun `moveEntry with stale anchors keeps positions 0 to n-1`() = runTest {
        // Two quick moves computed from the same (stale) list A,B,C.
        val dao = RecordingQueueDao(mapOf(20L to 0, 21L to 1, 22L to 2))
        val repository = QueueRepository(dao)

        repository.moveEntry(movedId = 22, afterId = 20, beforeId = 21) // C up
        repository.moveEntry(movedId = 21, afterId = null, beforeId = 20) // stale "B up"

        assertEquals(listOf(21L, 20L, 22L), dao.order())
        assertEquals(setOf(0, 1, 2), dao.positions.values.toSet())
    }

    @Test
    fun `moveEntry for a removed entry changes nothing`() = runTest {
        val dao = RecordingQueueDao(mapOf(10L to 0, 11L to 1))
        QueueRepository(dao).moveEntry(movedId = 99, afterId = 10, beforeId = 11)

        assertEquals(mapOf(10L to 0, 11L to 1), dao.positions)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest`
Expected: compilation FAIL — `Unresolved reference 'reorderQueue'`, `'anchorsFor'`, `'getOrderedIds' overrides nothing`, `Unresolved reference 'moveEntry'`.

- [ ] **Step 3: Implement the ordering logic**

`app/src/main/java/com/berpod/app/data/local/QueueOrder.kt`:

```kotlin
package com.berpod.app.data.local

/**
 * New full queue order after moving [movedId]: right after [afterId] if present, otherwise right
 * before [beforeId]. Returns [currentOrder] unchanged when the moved id or both anchors are missing.
 */
fun reorderQueue(currentOrder: List<Long>, movedId: Long, afterId: Long?, beforeId: Long?): List<Long> {
    if (movedId !in currentOrder) return currentOrder
    val rest = currentOrder - movedId
    val insertAt = when {
        afterId != null && afterId in rest -> rest.indexOf(afterId) + 1
        beforeId != null && beforeId in rest -> rest.indexOf(beforeId)
        else -> return currentOrder
    }
    return rest.toMutableList().apply { add(insertAt, movedId) }
}

/** (afterId, beforeId): the neighbours of [movedId] in [order], or (null, null) if it's absent. */
fun anchorsFor(order: List<Long>, movedId: Long): Pair<Long?, Long?> {
    val index = order.indexOf(movedId)
    if (index < 0) return null to null
    return order.getOrNull(index - 1) to order.getOrNull(index + 1)
}
```

In `QueueDao.kt`, delete `getPosition` and the whole `swapEntries` method (with its comment and `@Transaction`), and add in their place:

```kotlin
    @Query("SELECT id FROM queue_entries ORDER BY position ASC")
    suspend fun getOrderedIds(): List<Long>

    // The new order is computed from the stored order inside the transaction, so a move issued
    // from a stale UI list can't leave two entries on the same position.
    @Transaction
    suspend fun moveEntry(movedId: Long, afterId: Long?, beforeId: Long?) {
        val current = getOrderedIds()
        val reordered = reorderQueue(current, movedId, afterId, beforeId)
        if (reordered == current) return
        reordered.forEachIndexed { index, id -> updatePosition(id, index) }
    }
```

In `QueueRepository.kt`, replace the whole `moveItem` function with:

```kotlin
    suspend fun moveEntry(movedId: Long, afterId: Long?, beforeId: Long?) =
        queueDao.moveEntry(movedId, afterId, beforeId)
```

In `QueueViewModel.kt`, replace the comment line and the `onMoveUp` / `onMoveDown` functions with:

```kotlin
    fun onMoveEntry(movedId: Long, afterId: Long?, beforeId: Long?) {
        viewModelScope.launch { repository.moveEntry(movedId, afterId, beforeId) }
    }

    // One step within the visible list (used by accessibility actions); hidden entries keep their positions.
    fun onMoveUp(index: Int) {
        val visible = _uiState.value.visibleItems
        val moved = visible.getOrNull(index) ?: return
        val above = visible.getOrNull(index - 1) ?: return
        onMoveEntry(moved.queueEntryId, afterId = null, beforeId = above.queueEntryId)
    }

    fun onMoveDown(index: Int) {
        val visible = _uiState.value.visibleItems
        val moved = visible.getOrNull(index) ?: return
        val below = visible.getOrNull(index + 1) ?: return
        onMoveEntry(moved.queueEntryId, afterId = below.queueEntryId, beforeId = null)
    }
```

- [ ] **Step 4: Run tests and build**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: PASS — 37 − 3 old `QueueRepositoryTest` + 3 new + 7 `QueueOrderTest` = 44 tests; BUILD SUCCESSFUL. (The Queue screen still has arrows at this point; they now call the new `onMoveUp`/`onMoveDown`.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/berpod/app/data/local/QueueOrder.kt \
  app/src/main/java/com/berpod/app/data/local/QueueDao.kt \
  app/src/main/java/com/berpod/app/data/repository/QueueRepository.kt \
  app/src/main/java/com/berpod/app/ui/queue/QueueViewModel.kt \
  app/src/test/java/com/berpod/app/data/local/QueueOrderTest.kt \
  app/src/test/java/com/berpod/app/data/repository/QueueRepositoryTest.kt
git commit -m "Reorder the queue by moving an entry between neighbours inside a transaction"
```

---

### Task 2: Artwork in episode rows

**Files:**
- Create: `app/src/main/java/com/berpod/app/ui/common/PodcastArtwork.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/common/EpisodeListItem.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/newepisodes/NewEpisodesScreen.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `@Composable fun PodcastArtwork(imageUrl: String?, modifier: Modifier = Modifier)`; `EpisodeListItem(..., showArtwork: Boolean = false, leadingContent: @Composable (() -> Unit)? = null)`.

Compose UI with no UI-test setup: gate is build + manual check.

- [ ] **Step 1: Create `PodcastArtwork`**

```kotlin
package com.berpod.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private val ArtworkShape = RoundedCornerShape(8.dp)

/** 56dp square podcast cover; a podcast icon shows through while loading or when there's no image. */
@Composable
fun PodcastArtwork(imageUrl: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(ArtworkShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Podcasts,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
```

- [ ] **Step 2: Add `showArtwork` and `leadingContent` to `EpisodeListItem`**

Add the two parameters at the end of the parameter list:

```kotlin
    progressFraction: Float? = null,
    showArtwork: Boolean = false,
    leadingContent: @Composable (() -> Unit)? = null
) {
```

Directly inside the outer `Row(...) {`, before `Column(modifier = Modifier.weight(1f)) {`, add:

```kotlin
        if (leadingContent != null) {
            leadingContent()
        }
        if (showArtwork) {
            PodcastArtwork(imageUrl = podcastImageUrl, modifier = Modifier.padding(end = 12.dp))
        }
```

- [ ] **Step 3: Use artwork in New Episodes**

In `NewEpisodesScreen.kt`'s `EpisodeListItem(...)` call, change `podcastTitle = item.podcastTitle,` to `podcastTitle = null,` and add `showArtwork = true,` after the `progressFraction = ...` argument.

- [ ] **Step 4: Build**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: 44 tests PASS, BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/berpod/app/ui/common/PodcastArtwork.kt \
  app/src/main/java/com/berpod/app/ui/common/EpisodeListItem.kt \
  app/src/main/java/com/berpod/app/ui/newepisodes/NewEpisodesScreen.kt
git commit -m "Show podcast artwork instead of the podcast name in New Episodes rows"
```

---

### Task 3: Drag-and-drop Queue screen

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Modify: `app/src/main/java/com/berpod/app/ui/queue/QueueScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `QueueViewModel.onMoveEntry`, `onMoveUp`, `onMoveDown`, `anchorsFor` (Task 1); `EpisodeListItem(showArtwork, leadingContent)` (Task 2); library `rememberReorderableLazyListState(lazyListState) { from, to -> }`, `LazyItemScope.ReorderableItem(state, key) { isDragging -> }`, `ReorderableCollectionItemScope.Modifier.draggableHandle(onDragStarted: (Offset) -> Unit, onDragStopped: () -> Unit)` (verified against the 3.1.0 sources).
- Produces: string `action_drag_to_reorder`.

- [ ] **Step 1: Add the dependency**

`gradle/libs.versions.toml`: under `[versions]` add `reorderable = "3.1.0"`; under `[libraries]` add:

```toml
reorderable = { group = "sh.calvin.reorderable", name = "reorderable", version.ref = "reorderable" }
```

`app/build.gradle.kts`: after `implementation(libs.androidx.material.icons.extended)` add `implementation(libs.reorderable)`.

- [ ] **Step 2: Add the string**

Before `</resources>` in `strings.xml`:

```xml
    <string name="action_drag_to_reorder">Přesunout</string>
```

- [ ] **Step 3: Rewrite the Queue list**

In `QueueScreen.kt`:
- Remove imports `androidx.compose.material.icons.filled.KeyboardArrowDown`, `androidx.compose.material.icons.filled.KeyboardArrowUp`, `androidx.compose.foundation.layout.Row` (keep `itemsIndexed`).
- Add imports:

```kotlin
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.data.local.anchorsFor
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
```

- Replace the `else -> LazyColumn { ... }` branch with `else -> ReorderableQueueList(uiState = uiState, viewModel = viewModel)` and add this composable to the file:

```kotlin
@Composable
private fun ReorderableQueueList(uiState: QueueUiState, viewModel: QueueViewModel) {
    val haptics = LocalHapticFeedback.current
    // Local copy reordered live while dragging; the database is written once on drop.
    var localItems by remember { mutableStateOf(uiState.visibleItems) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    // The queue re-emits during playback (position saves); never overwrite the list mid-drag.
    LaunchedEffect(uiState.visibleItems) {
        if (draggingId == null) localItems = uiState.visibleItems
    }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        localItems = localItems.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    val moveUpLabel = stringResource(R.string.action_move_up)
    val moveDownLabel = stringResource(R.string.action_move_down)

    LazyColumn(state = lazyListState) {
        itemsIndexed(localItems, key = { _, item -> item.queueEntryId }) { index, item ->
            ReorderableItem(reorderState, key = item.queueEntryId) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "dragElevation")
                Surface(
                    shadowElevation = elevation,
                    modifier = Modifier.semantics {
                        customActions = buildList {
                            if (index > 0) add(CustomAccessibilityAction(moveUpLabel) { viewModel.onMoveUp(index); true })
                            if (index < localItems.lastIndex) {
                                add(CustomAccessibilityAction(moveDownLabel) { viewModel.onMoveDown(index); true })
                            }
                        }
                    }
                ) {
                    QueueRow(
                        item = item,
                        uiState = uiState,
                        viewModel = viewModel,
                        handleModifier = Modifier.draggableHandle(
                            onDragStarted = {
                                draggingId = item.queueEntryId
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragStopped = {
                                val before = uiState.visibleItems.map { it.queueEntryId }
                                val after = localItems.map { it.queueEntryId }
                                draggingId = null
                                if (before != after) {
                                    val (afterId, beforeId) = anchorsFor(after, item.queueEntryId)
                                    viewModel.onMoveEntry(item.queueEntryId, afterId, beforeId)
                                }
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItemWithEpisode,
    uiState: QueueUiState,
    viewModel: QueueViewModel,
    handleModifier: Modifier
) {
    EpisodeListItem(
        episode = item.episode,
        podcastTitle = null,
        podcastImageUrl = item.podcastImageUrl,
        onPlay = { viewModel.onPlay(item) },
        isCurrentlyPlaying = uiState.currentlyPlayingEpisodeId == item.episode.id && uiState.isPlaying,
        progressFraction = episodeProgressFraction(item.episode.lastPositionMs, item.episode.durationSeconds),
        showArtwork = true,
        leadingContent = {
            IconButton(onClick = {}, modifier = handleModifier) {
                Icon(
                    Icons.Filled.DragIndicator,
                    contentDescription = stringResource(R.string.action_drag_to_reorder)
                )
            }
        },
        trailingContent = {
            IconButton(onClick = { viewModel.onRemove(item.queueEntryId) }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_remove_from_queue)
                )
            }
        }
    )
}
```

(`draggableHandle` is an extension inside `ReorderableCollectionItemScope`, which is the receiver of the `ReorderableItem` content lambda, so the handle modifier is built there and passed down.)

- [ ] **Step 4: Build**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: 44 tests PASS, BUILD SUCCESSFUL. If `draggableHandle` or `ReorderableItem` fail to resolve, re-check the imports against the library sources (`sh.calvin.reorderable`) before changing any logic.

- [ ] **Step 5: Manual pass on the emulator**

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.berpod.app
adb shell monkey -p com.berpod.app -c android.intent.category.LAUNCHER 1
```

Drag with `adb shell input draganddrop <x1> <y1> <x2> <y2> 800` (or `input swipe ... 1200` starting on the handle), taking and looking at screenshots:
- Queue rows: handle ⠿, 56dp artwork, no podcast-name line, play and ✕, no arrows. New Episodes rows: artwork, no podcast-name line, buttons unchanged. Podcast detail rows unchanged.
- Drag the first queue row below the last: order changes, persists after force-stop + relaunch.
- Filter by a tag with ≥2 visible entries, drag one: switching to "Všechny" shows hidden entries still in their relative places.
- Drop a row back where it started: order unchanged.
- Play an episode, then drag while it plays: the list doesn't jump back mid-drag.
- ✕ still removes; play still plays.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
  app/src/main/java/com/berpod/app/ui/queue/QueueScreen.kt \
  app/src/main/res/values/strings.xml
git commit -m "Reorder the queue by dragging a handle and show artwork in queue rows"
```
