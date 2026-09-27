# Queue Tag Filter and Playlist Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the playlist feature (UI, code, and database tables) and give the Queue screen the same single-select tag filter chip row as the Podcasts screen.

**Architecture:** Playlists are deleted end to end, with a `MIGRATION_4_5` that drops their two tables. The Queue filter runs in memory: `QueueViewModel` combines the queue flow with the podcast ids carrying the selected tag (`TagDao.observePodcastIdsWithTag`) through a pure `filterQueueByTag`. Moves pass the *visible* list to the unchanged `QueueRepository.moveItem`. `TagChipRow` and `resolveSelectedTagId` move to `ui/common` so both screens share them; the chip menu becomes optional.

**Tech Stack:** Kotlin 2.4, Jetpack Compose (BOM 2026.09.00, Material 3), Room 2.8.5 with KSP and exported schemas, kotlinx-coroutines, JUnit 4 + kotlinx-coroutines-test (JVM tests only).

**Spec:** `docs/superpowers/specs/2026-09-27-queue-tags-remove-playlists-design.md` (builds on `docs/superpowers/specs/2026-09-27-podcast-tags-design.md`)

## Global Constraints

- All user-visible text is Czech and lives in `app/src/main/res/values/strings.xml`.
- Room DB version goes 4 → 5 via `MIGRATION_4_5` that runs `DROP TABLE IF EXISTS playlist_episodes` then `DROP TABLE IF EXISTS playlists`. Podcasts, episodes, queue entries, tags and tag links must survive. Never `fallbackToDestructiveMigration`.
- Existing `MIGRATION_1_2`…`MIGRATION_3_4` stay unchanged.
- Bottom navigation has exactly three tabs: Podcasty, Fronta, Nové.
- Tags stay on podcasts; an episode matches a filter when its podcast (`episode.feedId`) has the tag.
- The Queue filter is independent of the Podcasts filter, single-select (`null` = "Všechny"), not persisted, and falls back to "Všechny" when the selected tag is deleted.
- Tag rename/delete lives on the Podcasts screen only; Queue chips have no long-press action.
- Touch targets ≥ 48dp. Don't rename packages/classes (`com.berpod.app`, `Berpod*`).
- Work continues on branch `feature/podcast-tags`. Build: `./gradlew :app:assembleDebug`; tests: `./gradlew :app:testDebugUnitTest`; run from `/Users/Berda/Code/berpod`.

## Review Focus

1. Upgrading an install whose v4 database contains playlists must not crash and must keep queue entries, podcasts and tags — checked in Task 1 Step 7 (schema check) and the Task 3 manual pass (install over the current emulator database).
2. In a filtered Queue, "up" on the first visible item and "down" on the last visible item must be disabled, and moving must only swap the two visible neighbours — pinned in Task 2 (`moveItem swaps only the two given visible entries`, `moveItem ignores out-of-range indices`) and the screen uses `visibleItems.lastIndex` (Task 3).
3. Unassigning a tag from a podcast while the Queue is filtered by that tag must remove its episodes from the visible list immediately — pinned in Task 2 (`observePodcastIdsWithTag reflects unassign live`).
4. Deleting, on the Podcasts screen, the tag currently selected in the Queue must reset the Queue to "Všechny" — covered by the existing `TagFilterTest` (moved in Task 3) plus the Task 3 manual pass.
5. An empty filter result must show the filter-specific empty state, while a truly empty queue keeps "Fronta je prázdná" — handled in Task 3 (`items` vs `visibleItems` check), checked in the manual pass.

---

### Task 1: Remove playlists

**Files:**
- Delete: `app/src/main/java/com/berpod/app/ui/playlists/` (all files: `CreatePlaylistDialog.kt`, `PlaylistDetailScreen.kt`, `PlaylistDetailViewModel.kt`, `PlaylistsScreen.kt`, `PlaylistsViewModel.kt`)
- Delete: `app/src/main/java/com/berpod/app/ui/common/AddToPlaylistDialog.kt`
- Delete: `app/src/main/java/com/berpod/app/data/repository/PlaylistRepository.kt`, `app/src/main/java/com/berpod/app/data/local/PlaylistDao.kt`, `app/src/main/java/com/berpod/app/data/local/PlaylistEntity.kt`, `app/src/main/java/com/berpod/app/data/local/PlaylistEpisodeCrossRef.kt`
- Modify: `app/src/main/java/com/berpod/app/data/local/EpisodeWithFeed.kt` (remove `PlaylistEpisodeWithEpisode`)
- Modify: `app/src/main/java/com/berpod/app/data/local/AppDatabase.kt`, `app/src/main/java/com/berpod/app/data/local/Migrations.kt`, `app/src/main/java/com/berpod/app/di/AppContainer.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/navigation/Screen.kt`, `app/src/main/java/com/berpod/app/ui/navigation/BerpodApp.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/newepisodes/NewEpisodesViewModel.kt`, `app/src/main/java/com/berpod/app/ui/newepisodes/NewEpisodesScreen.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/podcastlist/PodcastEpisodesViewModel.kt`, `app/src/main/java/com/berpod/app/ui/podcastlist/PodcastEpisodesScreen.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/common/EpisodeListItem.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create (generated): `app/schemas/com.berpod.app.data.local.AppDatabase/5.json`

**Interfaces:**
- Consumes: nothing new.
- Produces: `MIGRATION_4_5`; `AppDatabase` version 5 without playlist entities; `AppContainer` without `playlistRepository`; `NewEpisodesViewModel.factory(episodeRepository, queueRepository, playbackRepository)`; `PodcastEpisodesViewModel.factory(podcastRepository, episodeRepository, queueRepository, playbackRepository, tagRepository, feedId)`; `EpisodeListItem` without `onAddToPlaylist`.

This task deletes code; there is no new behavior to drive with a failing unit test. Its gates are: the build compiles with zero playlist references, the existing suite stays green, and the exported schema 5 has no playlist tables.

- [ ] **Step 1: Delete the playlist files**

```bash
cd /Users/Berda/Code/berpod
git rm -r app/src/main/java/com/berpod/app/ui/playlists
git rm app/src/main/java/com/berpod/app/ui/common/AddToPlaylistDialog.kt \
  app/src/main/java/com/berpod/app/data/repository/PlaylistRepository.kt \
  app/src/main/java/com/berpod/app/data/local/PlaylistDao.kt \
  app/src/main/java/com/berpod/app/data/local/PlaylistEntity.kt \
  app/src/main/java/com/berpod/app/data/local/PlaylistEpisodeCrossRef.kt
```

- [ ] **Step 2: Database — entities, version, migration, container**

`EpisodeWithFeed.kt`: delete the whole `data class PlaylistEpisodeWithEpisode(...)` declaration (keep `EpisodeWithFeed` and `QueueItemWithEpisode`).

`AppDatabase.kt`: remove `PlaylistEntity::class,` and `PlaylistEpisodeCrossRef::class,` from `entities`, change `version = 4` to `version = 5`, and delete `abstract fun playlistDao(): PlaylistDao`. The entities list becomes:

```kotlin
    entities = [
        PodcastFeedEntity::class,
        EpisodeEntity::class,
        QueueEntryEntity::class,
        TagEntity::class,
        PodcastTagCrossRef::class
    ],
    version = 5,
```

Append to `Migrations.kt` (leave the older migrations untouched):

```kotlin

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Child table first: playlist_episodes has a foreign key to playlists.
        db.execSQL("DROP TABLE IF EXISTS `playlist_episodes`")
        db.execSQL("DROP TABLE IF EXISTS `playlists`")
    }
}
```

`AppContainer.kt`:
- delete `import com.berpod.app.data.repository.PlaylistRepository`
- add `import com.berpod.app.data.local.MIGRATION_4_5`
- delete `val playlistRepository: PlaylistRepository` from the interface
- delete the `override val playlistRepository ... by lazy { PlaylistRepository(database.playlistDao()) }` block
- change `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)` to `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`

- [ ] **Step 3: Navigation — three tabs**

Replace `Screen.kt` with:

```kotlin
package com.berpod.app.ui.navigation

sealed class Screen(val route: String) {
    data object Podcasts : Screen("podcasts")
    data object Queue : Screen("queue")
    data object NewEpisodes : Screen("new_episodes")
    data object PodcastEpisodes : Screen("podcast/{feedId}") {
        const val ARG_FEED_ID = "feedId"
        fun createRoute(feedId: Long) = "podcast/$feedId"
    }
    data object Player : Screen("player")
}
```

In `BerpodApp.kt`:
- delete the imports `androidx.compose.material.icons.automirrored.filled.PlaylistPlay`, `com.berpod.app.ui.playlists.PlaylistDetailScreen`, `com.berpod.app.ui.playlists.PlaylistDetailViewModel`, `com.berpod.app.ui.playlists.PlaylistsScreen`, `com.berpod.app.ui.playlists.PlaylistsViewModel`
- change `private val bottomTabs = listOf(Screen.Podcasts, Screen.Queue, Screen.NewEpisodes, Screen.Playlists)` to `private val bottomTabs = listOf(Screen.Podcasts, Screen.Queue, Screen.NewEpisodes)`
- delete the line `Screen.Playlists -> Icons.AutoMirrored.Filled.PlaylistPlay` in `iconFor` and `Screen.Playlists -> stringResource(R.string.nav_playlists)` in `labelFor`
- delete the whole `composable(Screen.Playlists.route) { ... }` block and the whole `composable(route = Screen.PlaylistDetail.route, ...) { ... }` block
- change the New Episodes factory call to:

```kotlin
                NewEpisodesScreen(
                    viewModelFactory = NewEpisodesViewModel.factory(
                        container.episodeRepository,
                        container.queueRepository,
                        container.playbackRepository
                    )
                )
```

- in the `PodcastEpisodesViewModel.factory(...)` call, delete the line `container.playlistRepository,`

- [ ] **Step 4: New Episodes — drop playlist state**

Replace `NewEpisodesViewModel.kt` with:

```kotlin
package com.berpod.app.ui.newepisodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.EpisodeWithFeed
import com.berpod.app.data.repository.EpisodeRepository
import com.berpod.app.data.repository.PlaybackRepository
import com.berpod.app.data.repository.QueueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// TODO: read/unread tracking is out of scope for this iteration — "new" just means
// "all known episodes, newest published first".
data class NewEpisodesUiState(
    val episodes: List<EpisodeWithFeed> = emptyList(),
    val isRefreshing: Boolean = false,
    val snackbarMessage: String? = null,
    val currentlyPlayingEpisodeId: Long? = null,
    val isPlaying: Boolean = false
)

class NewEpisodesViewModel(
    private val episodeRepository: EpisodeRepository,
    private val queueRepository: QueueRepository,
    private val playbackRepository: PlaybackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewEpisodesUiState())
    val uiState: StateFlow<NewEpisodesUiState> = _uiState.asStateFlow()

    init {
        episodeRepository.observeNewEpisodes()
            .onEach { episodes -> _uiState.update { it.copy(episodes = episodes) } }
            .launchIn(viewModelScope)
        playbackRepository.uiState
            .onEach { playback ->
                _uiState.update {
                    it.copy(currentlyPlayingEpisodeId = playback.currentEpisodeId, isPlaying = playback.isPlaying)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onPlay(item: EpisodeWithFeed) {
        playbackRepository.play(item.episode, item.podcastTitle, item.podcastImageUrl)
    }

    fun onRefresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            episodeRepository.refreshAllFeeds()
                .onSuccess { failureCount ->
                    val message = if (failureCount > 0) {
                        "Aktualizace dokončena, $failureCount zdrojů se nepodařilo načíst"
                    } else {
                        null
                    }
                    _uiState.update { it.copy(isRefreshing = false, snackbarMessage = message) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isRefreshing = false, snackbarMessage = error.message ?: "Aktualizace selhala")
                    }
                }
        }
    }

    fun onAddToQueue(episodeId: Long) {
        viewModelScope.launch {
            queueRepository.addToQueue(episodeId)
                .onSuccess { _uiState.update { it.copy(snackbarMessage = "Přidáno do fronty") } }
                .onFailure { error -> _uiState.update { it.copy(snackbarMessage = error.message) } }
        }
    }

    fun onSnackbarMessageShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    companion object {
        fun factory(
            episodeRepository: EpisodeRepository,
            queueRepository: QueueRepository,
            playbackRepository: PlaybackRepository
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NewEpisodesViewModel(episodeRepository, queueRepository, playbackRepository) as T
            }
        }
    }
}
```

In `NewEpisodesScreen.kt`: delete `import com.berpod.app.ui.common.AddToPlaylistDialog`, delete the argument line `onAddToPlaylist = { viewModel.onOpenPlaylistPicker(item.episode.id) },`, and delete the whole `if (uiState.playlistPickerEpisodeId != null) { AddToPlaylistDialog(...) }` block.

- [ ] **Step 5: Podcast Episodes — drop playlist state**

In `PodcastEpisodesViewModel.kt`:
- delete imports `com.berpod.app.data.local.PlaylistEntity` and `com.berpod.app.data.repository.PlaylistRepository`
- delete the four `PodcastEpisodesUiState` fields `playlists`, `playlistPickerEpisodeId`, `isCreatingPlaylist`, `playlistPickerError`
- delete the constructor parameter `private val playlistRepository: PlaylistRepository,`
- delete the `playlistRepository.observePlaylists()...launchIn(viewModelScope)` block in `init`
- delete the functions `onOpenPlaylistPicker`, `onDismissPlaylistPicker`, `onAddToExistingPlaylist`, `onCreatePlaylistAndAdd`
- in `factory`, delete the parameter `playlistRepository: PlaylistRepository,` and the argument `playlistRepository,`

The constructor and factory become:

```kotlin
class PodcastEpisodesViewModel(
    private val podcastRepository: PodcastRepository,
    private val episodeRepository: EpisodeRepository,
    private val queueRepository: QueueRepository,
    private val playbackRepository: PlaybackRepository,
    private val tagRepository: TagRepository,
    private val feedId: Long
) : ViewModel() {
```

```kotlin
    companion object {
        fun factory(
            podcastRepository: PodcastRepository,
            episodeRepository: EpisodeRepository,
            queueRepository: QueueRepository,
            playbackRepository: PlaybackRepository,
            tagRepository: TagRepository,
            feedId: Long
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PodcastEpisodesViewModel(
                    podcastRepository,
                    episodeRepository,
                    queueRepository,
                    playbackRepository,
                    tagRepository,
                    feedId
                ) as T
            }
        }
    }
```

In `PodcastEpisodesScreen.kt`: delete `import com.berpod.app.ui.common.AddToPlaylistDialog`, delete the argument line `onAddToPlaylist = { viewModel.onOpenPlaylistPicker(episode.id) },`, and delete the whole `if (uiState.playlistPickerEpisodeId != null) { AddToPlaylistDialog(...) }` block (keep the `tagAssignState?.let { ... }` block that follows it).

- [ ] **Step 6: `EpisodeListItem` and strings**

In `EpisodeListItem.kt`:
- delete `import androidx.compose.material.icons.automirrored.filled.PlaylistAdd`
- delete the parameter `onAddToPlaylist: (() -> Unit)? = null,`
- change `if (onDownload != null || onAddToQueue != null || onAddToPlaylist != null) {` to `if (onDownload != null || onAddToQueue != null) {`
- delete the whole `if (onAddToPlaylist != null) { IconButton(...) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, ...) } }` block

In `strings.xml`, delete these lines: `nav_playlists`, `action_add_to_playlist`, `playlists_title`, `playlists_empty`, `playlist_detail_empty`, `fab_add_playlist`, `create_playlist_dialog_title`, `create_playlist_name_label`, `add_to_playlist_dialog_title`, `action_new_playlist_inline`, `action_remove_from_playlist`.

- [ ] **Step 7: Build, verify no references remain, check schema**

Run: `grep -rn -i "playlist" app/src/main app/src/test`
Expected: only the two historical migrations in `Migrations.kt` (`MIGRATION_1_2` creating the tables) and `MIGRATION_4_5` (dropping them). No other hits.

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: all existing tests PASS (29), BUILD SUCCESSFUL, new `app/schemas/com.berpod.app.data.local.AppDatabase/5.json`.

Run: `grep -c playlist app/schemas/com.berpod.app.data.local.AppDatabase/5.json`
Expected: `0`.

- [ ] **Step 8: Commit**

```bash
git add -A app/src/main app/schemas/com.berpod.app.data.local.AppDatabase/5.json
git commit -m "Remove playlists and drop their tables in database migration 4 to 5"
```

---

### Task 2: Queue filtering logic

**Files:**
- Modify: `app/src/main/java/com/berpod/app/data/local/TagDao.kt`
- Modify: `app/src/main/java/com/berpod/app/data/repository/TagRepository.kt`
- Create: `app/src/main/java/com/berpod/app/ui/queue/QueueFilter.kt`
- Create: `app/src/main/java/com/berpod/app/ui/common/TagFilter.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/podcastlist/PodcastListViewModel.kt` (remove `resolveSelectedTagId`, import it from `ui/common`)
- Modify: `app/src/main/java/com/berpod/app/ui/queue/QueueViewModel.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/navigation/BerpodApp.kt` (Queue factory call)
- Modify: `app/src/test/java/com/berpod/app/testutil/FakeTagDao.kt`
- Move: `app/src/test/java/com/berpod/app/ui/podcastlist/TagFilterTest.kt` → `app/src/test/java/com/berpod/app/ui/common/TagFilterTest.kt`
- Test: `app/src/test/java/com/berpod/app/data/repository/TagRepositoryTest.kt` (add tests)
- Test: `app/src/test/java/com/berpod/app/ui/queue/QueueFilterTest.kt`
- Test: `app/src/test/java/com/berpod/app/data/repository/QueueRepositoryTest.kt`

**Interfaces:**
- Consumes: `TagRepository`, `TagEntity`, `FakeTagDao`, `FakePodcastFeedDao`, `feed()` (existing); `QueueRepository.moveItem(currentOrder, fromIndex, toIndex)` (existing, unchanged).
- Produces:
  - `TagDao.observePodcastIdsWithTag(tagId: Long): Flow<List<Long>>`
  - `TagRepository.observePodcastIdsWithTag(tagId: Long): Flow<List<Long>>`
  - `fun filterQueueByTag(items: List<QueueItemWithEpisode>, podcastIds: Set<Long>?): List<QueueItemWithEpisode>` in package `com.berpod.app.ui.queue`
  - `fun resolveSelectedTagId(selected: Long?, tags: List<TagEntity>): Long?` now in package `com.berpod.app.ui.common` (file `TagFilter.kt`)
  - `QueueUiState` with `items`, `visibleItems`, `tags`, `selectedTagId`, `currentlyPlayingEpisodeId`, `isPlaying`
  - `QueueViewModel(repository, playbackRepository, tagRepository)` with `onSelectTag(tagId: Long?)`; `QueueViewModel.factory(repository, playbackRepository, tagRepository)`
  - Test helpers in `com.berpod.app.testutil`: `fun episode(id: Long, feedId: Long): EpisodeEntity`, `fun queueItem(entryId: Long, position: Int, feedId: Long): QueueItemWithEpisode`

- [ ] **Step 1: Write the failing tests**

Add to `app/src/test/java/com/berpod/app/testutil/FakeTagDao.kt` (inside `class FakeTagDao`, next to `observeTagIdsForPodcast`):

```kotlin
    override fun observePodcastIdsWithTag(tagId: Long): Flow<List<Long>> =
        _links.map { links -> links.filter { it.tagId == tagId }.map { it.podcastId } }
```

and at the top level of the same file (add imports `com.berpod.app.data.local.EpisodeEntity` and `com.berpod.app.data.local.QueueItemWithEpisode`):

```kotlin
fun episode(id: Long, feedId: Long) = EpisodeEntity(
    id = id,
    feedId = feedId,
    guid = "guid-$id",
    title = "Episode $id",
    audioUrl = "https://example.com/$id.mp3",
    pubDateMillis = null,
    durationSeconds = null,
    description = null,
    imageUrl = null,
    dateFetched = 0L
)

fun queueItem(entryId: Long, position: Int, feedId: Long) = QueueItemWithEpisode(
    episode = episode(id = entryId, feedId = feedId),
    podcastTitle = "Podcast $feedId",
    podcastImageUrl = null,
    queueEntryId = entryId,
    queuePosition = position
)
```

Append to `TagRepositoryTest` (add imports `kotlinx.coroutines.ExperimentalCoroutinesApi`, `kotlinx.coroutines.flow.launchIn`, `kotlinx.coroutines.flow.onEach`, `kotlinx.coroutines.test.UnconfinedTestDispatcher`, and annotate the class `@OptIn(ExperimentalCoroutinesApi::class)`):

```kotlin
    @Test
    fun `observePodcastIdsWithTag returns podcasts carrying the tag`() = runTest {
        repository.createAndAssign(1, "IT")
        repository.createAndAssign(3, "IT")
        repository.createAndAssign(2, "News")
        val itId = tagDao.findByName("IT")!!.id

        assertEquals(setOf(1L, 3L), repository.observePodcastIdsWithTag(itId).first().toSet())
    }

    @Test
    fun `observePodcastIdsWithTag reflects unassign live`() = runTest(UnconfinedTestDispatcher()) {
        repository.createAndAssign(1, "IT")
        repository.createAndAssign(3, "IT")
        val itId = tagDao.findByName("IT")!!.id
        var latest: Set<Long> = emptySet()
        repository.observePodcastIdsWithTag(itId).onEach { latest = it.toSet() }.launchIn(backgroundScope)

        repository.setAssigned(podcastId = 3, tagId = itId, assigned = false)

        assertEquals(setOf(1L), latest)
    }
```

`app/src/test/java/com/berpod/app/ui/queue/QueueFilterTest.kt`:

```kotlin
package com.berpod.app.ui.queue

import com.berpod.app.testutil.queueItem
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueFilterTest {

    private val items = listOf(
        queueItem(entryId = 10, position = 0, feedId = 1),
        queueItem(entryId = 11, position = 1, feedId = 2),
        queueItem(entryId = 12, position = 2, feedId = 1),
        queueItem(entryId = 13, position = 3, feedId = 3)
    )

    @Test
    fun `null filter returns the whole queue`() {
        assertEquals(items, filterQueueByTag(items, null))
    }

    @Test
    fun `keeps only items from tagged podcasts in queue order`() {
        assertEquals(listOf(10L, 12L, 13L), filterQueueByTag(items, setOf(1L, 3L)).map { it.queueEntryId })
    }

    @Test
    fun `empty set returns nothing`() {
        assertEquals(emptyList<Any>(), filterQueueByTag(items, emptySet()))
    }
}
```

`app/src/test/java/com/berpod/app/data/repository/QueueRepositoryTest.kt`:

```kotlin
package com.berpod.app.data.repository

import com.berpod.app.data.local.QueueDao
import com.berpod.app.data.local.QueueEntryEntity
import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.testutil.queueItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Records stored positions per queue entry id; only what moveItem touches is implemented. */
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
}

class QueueRepositoryTest {

    // Full queue: 10@0 (feed 1), 11@1 (feed 2), 12@2 (feed 1). Filter shows feed 1 only.
    private val dao = RecordingQueueDao(mapOf(10L to 0, 11L to 1, 12L to 2))
    private val repository = QueueRepository(dao)
    private val visible = listOf(
        queueItem(entryId = 10, position = 0, feedId = 1),
        queueItem(entryId = 12, position = 2, feedId = 1)
    )

    @Test
    fun `moveItem swaps only the two given visible entries`() = runTest {
        repository.moveItem(visible, fromIndex = 1, toIndex = 0)

        assertEquals(mapOf(10L to 2, 11L to 1, 12L to 0), dao.positions)
    }

    @Test
    fun `moveItem ignores out-of-range indices`() = runTest {
        repository.moveItem(visible, fromIndex = 1, toIndex = 2)
        repository.moveItem(visible, fromIndex = 0, toIndex = -1)

        assertEquals(mapOf(10L to 0, 11L to 1, 12L to 2), dao.positions)
    }
}
```

(`swapPositions` is a default method on the `QueueDao` interface that calls `updatePosition`; the fake inherits it, so the test exercises the real swap logic. The two `QueueRepositoryTest` tests pin existing behavior the filtered Queue now relies on, so they are expected to pass as soon as they compile.)

Move the filter test: `git mv app/src/test/java/com/berpod/app/ui/podcastlist/TagFilterTest.kt app/src/test/java/com/berpod/app/ui/common/TagFilterTest.kt`, and change its first line to `package com.berpod.app.ui.common`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest`
Expected: compilation FAIL — `'observePodcastIdsWithTag' overrides nothing` in `FakeTagDao`, `Unresolved reference 'observePodcastIdsWithTag'` in `TagRepositoryTest`, `Unresolved reference 'filterQueueByTag'`, and `Unresolved reference 'resolveSelectedTagId'` in the moved `TagFilterTest`.

- [ ] **Step 3: Implement the DAO query, repository pass-through, filter function and shared resolver**

In `TagDao.kt`, after `observeTagIdsForPodcast`:

```kotlin
    @Query("SELECT podcastId FROM podcast_tags WHERE tagId = :tagId")
    fun observePodcastIdsWithTag(tagId: Long): Flow<List<Long>>
```

In `TagRepository.kt`, after `observeTagIdsForPodcast`:

```kotlin
    fun observePodcastIdsWithTag(tagId: Long): Flow<List<Long>> = tagDao.observePodcastIdsWithTag(tagId)
```

`app/src/main/java/com/berpod/app/ui/queue/QueueFilter.kt`:

```kotlin
package com.berpod.app.ui.queue

import com.berpod.app.data.local.QueueItemWithEpisode

/** Queue entries whose podcast is in [podcastIds], in queue order; null means no filter. */
fun filterQueueByTag(items: List<QueueItemWithEpisode>, podcastIds: Set<Long>?): List<QueueItemWithEpisode> =
    if (podcastIds == null) items else items.filter { it.episode.feedId in podcastIds }
```

`app/src/main/java/com/berpod/app/ui/common/TagFilter.kt`:

```kotlin
package com.berpod.app.ui.common

import com.berpod.app.data.local.TagEntity

/** Keeps [selected] if that tag still exists, otherwise falls back to "Všechny" (null). */
fun resolveSelectedTagId(selected: Long?, tags: List<TagEntity>): Long? =
    selected?.takeIf { id -> tags.any { it.id == id } }
```

In `PodcastListViewModel.kt`, delete the `resolveSelectedTagId` function and its KDoc comment, and add `import com.berpod.app.ui.common.resolveSelectedTagId`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — 29 existing + 2 `TagRepositoryTest` + 3 `QueueFilterTest` + 2 `QueueRepositoryTest` = 36.

- [ ] **Step 5: Wire the filter into `QueueViewModel`**

Replace `QueueViewModel.kt` with:

```kotlin
package com.berpod.app.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.data.local.TagEntity
import com.berpod.app.data.repository.PlaybackRepository
import com.berpod.app.data.repository.QueueRepository
import com.berpod.app.data.repository.TagRepository
import com.berpod.app.ui.common.resolveSelectedTagId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QueueUiState(
    /** The whole queue, independent of the filter. */
    val items: List<QueueItemWithEpisode> = emptyList(),
    /** Queue entries matching the selected tag; what the list shows. */
    val visibleItems: List<QueueItemWithEpisode> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    /** null = "Všechny". */
    val selectedTagId: Long? = null,
    val currentlyPlayingEpisodeId: Long? = null,
    val isPlaying: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModel(
    private val repository: QueueRepository,
    private val playbackRepository: PlaybackRepository,
    private val tagRepository: TagRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QueueUiState())
    val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

    private val selectedTagId = MutableStateFlow<Long?>(null)

    init {
        val taggedPodcastIds = selectedTagId.flatMapLatest { tagId ->
            if (tagId == null) flowOf(null) else tagRepository.observePodcastIdsWithTag(tagId).map { it.toSet() }
        }
        combine(repository.observeQueue(), taggedPodcastIds) { items, podcastIds ->
            items to filterQueueByTag(items, podcastIds)
        }
            .onEach { (items, visible) -> _uiState.update { it.copy(items = items, visibleItems = visible) } }
            .launchIn(viewModelScope)
        tagRepository.observeTags()
            .onEach { tags ->
                selectedTagId.value = resolveSelectedTagId(selectedTagId.value, tags)
                _uiState.update { it.copy(tags = tags, selectedTagId = selectedTagId.value) }
            }
            .launchIn(viewModelScope)
        playbackRepository.uiState
            .onEach { playback ->
                _uiState.update {
                    it.copy(currentlyPlayingEpisodeId = playback.currentEpisodeId, isPlaying = playback.isPlaying)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onSelectTag(tagId: Long?) {
        selectedTagId.value = tagId
        _uiState.update { it.copy(selectedTagId = tagId) }
    }

    // Moves act on the visible list: an item swaps with its visible neighbour, hidden entries keep their positions.
    fun onMoveUp(index: Int) {
        viewModelScope.launch { repository.moveItem(_uiState.value.visibleItems, index, index - 1) }
    }

    fun onMoveDown(index: Int) {
        viewModelScope.launch { repository.moveItem(_uiState.value.visibleItems, index, index + 1) }
    }

    fun onRemove(queueEntryId: Long) {
        viewModelScope.launch { repository.removeFromQueue(queueEntryId) }
    }

    fun onPlay(item: QueueItemWithEpisode) {
        playbackRepository.play(item.episode, item.podcastTitle, item.podcastImageUrl)
    }

    companion object {
        fun factory(
            repository: QueueRepository,
            playbackRepository: PlaybackRepository,
            tagRepository: TagRepository
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return QueueViewModel(repository, playbackRepository, tagRepository) as T
            }
        }
    }
}
```

In `BerpodApp.kt`, change the Queue composable to:

```kotlin
            composable(Screen.Queue.route) {
                QueueScreen(
                    viewModelFactory = QueueViewModel.factory(
                        container.queueRepository,
                        container.playbackRepository,
                        container.tagRepository
                    )
                )
            }
```

`QueueScreen.kt` still reads `uiState.items` at this point, so the app keeps behaving exactly as before until Task 3 switches the list to `visibleItems`.

- [ ] **Step 6: Run tests and build**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: 36 tests PASS, BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/berpod/app/data/local/TagDao.kt \
  app/src/main/java/com/berpod/app/data/repository/TagRepository.kt \
  app/src/main/java/com/berpod/app/ui/queue/QueueFilter.kt \
  app/src/main/java/com/berpod/app/ui/common/TagFilter.kt \
  app/src/main/java/com/berpod/app/ui/podcastlist/PodcastListViewModel.kt \
  app/src/main/java/com/berpod/app/ui/queue/QueueViewModel.kt \
  app/src/main/java/com/berpod/app/ui/navigation/BerpodApp.kt \
  app/src/test/java/com/berpod/app/testutil/FakeTagDao.kt \
  app/src/test/java/com/berpod/app/ui/common/TagFilterTest.kt \
  app/src/test/java/com/berpod/app/ui/podcastlist/TagFilterTest.kt \
  app/src/test/java/com/berpod/app/data/repository/TagRepositoryTest.kt \
  app/src/test/java/com/berpod/app/ui/queue/QueueFilterTest.kt \
  app/src/test/java/com/berpod/app/data/repository/QueueRepositoryTest.kt
git commit -m "Add tag filtering state to the queue view model"
```

---

### Task 3: Shared chip row and Queue screen UI

**Files:**
- Move: `app/src/main/java/com/berpod/app/ui/podcastlist/TagChipRow.kt` → `app/src/main/java/com/berpod/app/ui/common/TagChipRow.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/podcastlist/PodcastListScreen.kt` (imports)
- Modify: `app/src/main/java/com/berpod/app/ui/queue/QueueScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `QueueUiState.items`, `visibleItems`, `tags`, `selectedTagId`, `QueueViewModel.onSelectTag` (Task 2); `EmptyState`, `EpisodeListItem` (existing).
- Produces: `@Composable fun TagChipRow(tags: List<TagEntity>, selectedTagId: Long?, onSelect: (Long?) -> Unit, onRenameRequest: ((TagEntity) -> Unit)? = null, onDeleteRequest: ((TagEntity) -> Unit)? = null)` in package `com.berpod.app.ui.common`; `RenameTagDialog` and `DeleteTagDialog` also move to `com.berpod.app.ui.common`; strings `queue_tag_filter_empty`, `queue_tag_filter_empty_subtitle`.

This task is Compose UI; the project has no UI-test setup, so its gate is the build plus the manual emulator pass. The logic it displays is covered by Task 2's tests.

- [ ] **Step 1: Move `TagChipRow` to `ui/common` and make the menu optional**

```bash
git mv app/src/main/java/com/berpod/app/ui/podcastlist/TagChipRow.kt app/src/main/java/com/berpod/app/ui/common/TagChipRow.kt
```

In the moved file, change `package com.berpod.app.ui.podcastlist` to `package com.berpod.app.ui.common`, and replace the `TagChipRow` function (only that function; `TagChip`, `RenameTagDialog`, `DeleteTagDialog` stay as they are) with:

```kotlin
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
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") {
            TagChip(
                label = stringResource(R.string.filter_all),
                selected = selectedTagId == null,
                onClick = { onSelect(null) },
                onLongClick = null
            )
        }
        items(tags, key = { it.id }) { tag ->
            var isMenuOpen by remember { mutableStateOf(false) }
            Box {
                TagChip(
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
```

In `PodcastListScreen.kt`, add the imports:

```kotlin
import com.berpod.app.ui.common.DeleteTagDialog
import com.berpod.app.ui.common.RenameTagDialog
import com.berpod.app.ui.common.TagChipRow
```

(Its `TagChipRow(...)` call already passes both callbacks by name, so it keeps the menu.)

- [ ] **Step 2: Add the Queue filter strings**

Insert before `</resources>` in `strings.xml`:

```xml
    <string name="queue_tag_filter_empty">Ve frontě nejsou epizody s tímto štítkem</string>
    <string name="queue_tag_filter_empty_subtitle">Vyberte jiný štítek nebo Všechny</string>
```

- [ ] **Step 3: Rewrite the Queue screen body**

In `QueueScreen.kt`, add imports:

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.automirrored.outlined.Label
import com.berpod.app.ui.common.TagChipRow
```

and delete `import androidx.compose.foundation.layout.Box`. Replace the `Scaffold(...) { paddingValues -> ... }` content lambda so the function body reads:

```kotlin
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_queue)) }) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.tags.isNotEmpty()) {
                TagChipRow(
                    tags = uiState.tags,
                    selectedTagId = uiState.selectedTagId,
                    onSelect = viewModel::onSelectTag
                )
            }
            when {
                uiState.items.isEmpty() -> EmptyState(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = stringResource(R.string.queue_empty_title),
                    subtitle = stringResource(R.string.queue_empty_subtitle)
                )
                uiState.visibleItems.isEmpty() -> EmptyState(
                    icon = Icons.AutoMirrored.Outlined.Label,
                    title = stringResource(R.string.queue_tag_filter_empty),
                    subtitle = stringResource(R.string.queue_tag_filter_empty_subtitle)
                )
                else -> LazyColumn {
                    itemsIndexed(uiState.visibleItems, key = { _, item -> item.queueEntryId }) { index, item ->
                        EpisodeListItem(
                            episode = item.episode,
                            podcastTitle = item.podcastTitle,
                            podcastImageUrl = item.podcastImageUrl,
                            onPlay = { viewModel.onPlay(item) },
                            isCurrentlyPlaying = uiState.currentlyPlayingEpisodeId == item.episode.id && uiState.isPlaying,
                            progressFraction = episodeProgressFraction(item.episode.lastPositionMs, item.episode.durationSeconds),
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { viewModel.onMoveUp(index) }, enabled = index > 0) {
                                        Icon(
                                            Icons.Filled.KeyboardArrowUp,
                                            contentDescription = stringResource(R.string.action_move_up)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.onMoveDown(index) },
                                        enabled = index < uiState.visibleItems.lastIndex
                                    ) {
                                        Icon(
                                            Icons.Filled.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.action_move_down)
                                        )
                                    }
                                    IconButton(onClick = { viewModel.onRemove(item.queueEntryId) }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.action_remove_from_queue)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
```

- [ ] **Step 4: Run tests and build**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: 36 tests PASS, BUILD SUCCESSFUL.

- [ ] **Step 5: Manual pass on the emulator**

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am force-stop com.berpod.app
adb shell monkey -p com.berpod.app -c android.intent.category.LAUNCHER 1
adb logcat -d | grep -E "FATAL EXCEPTION|IllegalStateException"
```

Expected: no crash lines. Then verify with screenshots (`adb exec-out screencap -p > <scratchpad>/q.png`, look at each):
- Bottom navigation shows exactly Podcasty, Fronta, Nové; podcasts and tags from before the upgrade are still there (migration 4→5 ran).
- Nové and the podcast detail: episode rows show download and add-to-queue buttons, no playlist button.
- Add at least one episode from each of two podcasts (one tagged "IT Tech", one not) to the queue via Nové.
- Fronta: chip row "Všechny" + tags; selecting "IT Tech" shows only that podcast's episodes; selecting a tag with no queued episodes shows "Ve frontě nejsou epizody s tímto štítkem"; long-pressing a Queue chip does nothing.
- With a filter active and ≥2 visible episodes: "up" is disabled on the first visible row and "down" on the last; moving swaps the visible pair; switching back to "Všechny" shows hidden episodes still in their places.
- On Podcasty, delete the tag selected in Fronta → Fronta shows "Všechny" again.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/berpod/app/ui/common/TagChipRow.kt \
  app/src/main/java/com/berpod/app/ui/podcastlist/TagChipRow.kt \
  app/src/main/java/com/berpod/app/ui/podcastlist/PodcastListScreen.kt \
  app/src/main/java/com/berpod/app/ui/queue/QueueScreen.kt \
  app/src/main/res/values/strings.xml
git commit -m "Show the tag filter chip row on the queue screen"
```
