# Podcast Detail Header Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store the podcast author and show an AntennaPod-style header (blurred cover background, large cover, title, author) on the podcast detail screen.

**Architecture:** A nullable `author` column (migration 5→6) filled from the RSS channel's iTunes author (fallback: iTunes owner name) on add and on every refresh. The detail view model observes the feed row. The screen puts the header as the first `LazyColumn` item under a pinned top app bar that is transparent while the header is visible.

**Tech Stack:** Kotlin 2.4, Compose (BOM 2026.09.00, Material 3 1.4.0), Coil 3, Room 2.8.5, rssparser 6.1.8, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-27-podcast-detail-header-design.md`

## Global Constraints

- Czech UI text in `strings.xml`; no new user-visible strings are needed.
- Room 5 → 6 via `MIGRATION_5_6` (`ALTER TABLE podcast_feeds ADD COLUMN author TEXT`); existing data kept.
- A null parsed author never overwrites a stored author.
- Header: blur 24dp + 55% black scrim, bottom corners 24dp, cover 112dp / 12dp corners, title `headlineSmall` white max 2 lines, author `bodyMedium` white 80% max 1 line.
- Top bar transparent with white icons and no title while `firstVisibleItemIndex == 0`, solid with title otherwise.
- Branch `feature/podcast-tags`. Tests `./gradlew :app:testDebugUnitTest`, build `./gradlew :app:assembleDebug`, from the repo root.

## Review Focus

1. Upgrading from v5 must keep all data and not crash — Task 1 schema check + Task 2 manual install over the existing database.
2. A refresh whose feed lacks an author must not erase a stored author — pinned in Task 1 (`updateAuthor` only called for non-null; `pickAuthor` blank → null).
3. A podcast without artwork must still get a readable header (fallback background + icon) — Task 2 manual check with the fallback code path reviewed.
4. The transparent bar's white icons must stay visible over a light cover (scrim) and switch back to default colours once solid — Task 2 manual scroll check.
5. The empty-episodes state must still show the header — Task 2 (header rendered before the empty state item).

---

### Task 1: Store the podcast author

**Files:**
- Modify: `app/src/main/java/com/berpod/app/data/local/PodcastFeedEntity.kt`, `PodcastFeedDao.kt`, `AppDatabase.kt`, `Migrations.kt`
- Modify: `app/src/main/java/com/berpod/app/di/AppContainer.kt`
- Modify: `app/src/main/java/com/berpod/app/data/remote/FeedParser.kt`
- Modify: `app/src/main/java/com/berpod/app/data/repository/PodcastRepository.kt`, `EpisodeRepository.kt`
- Modify (fakes): `app/src/test/java/com/berpod/app/testutil/FakeTagDao.kt` (`FakePodcastFeedDao`), `app/src/test/java/com/berpod/app/data/repository/PodcastRepositoryTest.kt` (`FakeDao`)
- Test: `app/src/test/java/com/berpod/app/data/remote/FeedAuthorTest.kt`, `PodcastRepositoryTest.kt` (new test)
- Create (generated): `app/schemas/com.berpod.app.data.local.AppDatabase/6.json`

**Interfaces:**
- Produces: `PodcastFeedEntity.author: String?` (last parameter, default null); `ParsedFeedMeta.author: String?` (last parameter, default null); `fun pickAuthor(itunesAuthor: String?, ownerName: String?): String?` (package `com.berpod.app.data.remote`); `PodcastFeedDao.updateAuthor(feedId: Long, author: String)`, `PodcastFeedDao.observeById(feedId: Long): Flow<PodcastFeedEntity?>`; `PodcastRepository.observeFeed(feedId: Long): Flow<PodcastFeedEntity?>`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/berpod/app/data/remote/FeedAuthorTest.kt`:

```kotlin
package com.berpod.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedAuthorTest {

    @Test
    fun `itunes author wins over owner name`() {
        assertEquals("Český rozhlas", pickAuthor("Český rozhlas", "Owner"))
    }

    @Test
    fun `falls back to owner name and trims`() {
        assertEquals("Filip", pickAuthor("  ", "  Filip "))
    }

    @Test
    fun `blank or missing values give null`() {
        assertNull(pickAuthor(null, null))
        assertNull(pickAuthor("", " "))
    }
}
```

Append to `PodcastRepositoryTest` (inside the class):

```kotlin
    @Test
    fun `addFeed stores the parsed author`() = runTest {
        val dao = FakeDao()
        val fetcher = FakeFeedFetcher(ParsedFeedMeta("Fake Podcast", null, "A fake podcast", emptyList(), author = "Autor"))
        val repository = PodcastRepository(dao, fetcher)

        val id = repository.addFeed("https://example.com/feed.xml").getOrThrow()

        assertEquals("Autor", dao.getById(id)?.author)
    }
```

(add `import org.junit.Assert.assertEquals` if missing).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest`
Expected: compilation FAIL — `Unresolved reference 'pickAuthor'`, `No parameter with name 'author'`.

- [ ] **Step 3: Implement**

`PodcastFeedEntity`: add `val author: String? = null` as the last constructor parameter.

`PodcastFeedDao`: add

```kotlin
    @Query("SELECT * FROM podcast_feeds WHERE id = :feedId")
    fun observeById(feedId: Long): Flow<PodcastFeedEntity?>

    @Query("UPDATE podcast_feeds SET author = :author WHERE id = :feedId")
    suspend fun updateAuthor(feedId: Long, author: String)
```

`AppDatabase`: `version = 5` → `version = 6`.

`Migrations.kt` (append):

```kotlin

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE podcast_feeds ADD COLUMN author TEXT")
    }
}
```

`AppContainer`: import `MIGRATION_5_6` and append it to `.addMigrations(...)`.

`FeedParser.kt`: add `val author: String? = null` as the last field of `ParsedFeedMeta`; add the top-level function

```kotlin
/** The first non-blank of the iTunes author and the iTunes owner name, trimmed; null if neither. */
fun pickAuthor(itunesAuthor: String?, ownerName: String?): String? =
    listOf(itunesAuthor, ownerName).firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
```

and in `fetchAndParse` pass
`author = pickAuthor(channel.itunesChannelData?.author, channel.itunesChannelData?.owner?.name)` to `ParsedFeedMeta(...)`.

`PodcastRepository`: in `addFeed`'s `PodcastFeedEntity(...)` add `author = meta.author`; add

```kotlin
    fun observeFeed(feedId: Long): Flow<PodcastFeedEntity?> = dao.observeById(feedId)
```

`EpisodeRepository.syncEpisodesForFeed`: right after `val meta = feedFetcher.fetchAndParse(feed.rssUrl)` add

```kotlin
        // Fills the author for podcasts added before it was stored; a missing author never erases one.
        meta.author?.let { podcastFeedDao.updateAuthor(feed.id, it) }
```

Fakes: in both `FakePodcastFeedDao` (testutil) and `FakeDao` (PodcastRepositoryTest) implement

```kotlin
    override fun observeById(feedId: Long): Flow<PodcastFeedEntity?> =
        <feeds state>.map { list -> list.find { it.id == feedId } }
    override suspend fun updateAuthor(feedId: Long, author: String) {
        <feeds state>.value = <feeds state>.value.map { if (it.id == feedId) it.copy(author = author) else it }
    }
```

where `<feeds state>` is `feeds` in `FakePodcastFeedDao` and `state` in `FakeDao` (add `import kotlinx.coroutines.flow.map` where missing).

- [ ] **Step 4: Run tests, build, check schema**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: all PASS (52 + 4 = 56), BUILD SUCCESSFUL, new `6.json`.

Run: `grep -o '"fieldPath": "author"[^}]*' app/schemas/com.berpod.app.data.local.AppDatabase/6.json`
Expected: an `author` field with `"affinity": "TEXT"` and no `"notNull": true`.

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main/java/com/berpod/app/data app/src/main/java/com/berpod/app/di \
  app/src/test app/schemas/com.berpod.app.data.local.AppDatabase/6.json
git commit -m "Store the podcast author from the RSS feed on add and refresh"
```

---

### Task 2: Detail header UI

**Files:**
- Create: `app/src/main/java/com/berpod/app/ui/podcastlist/PodcastHeader.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/podcastlist/PodcastEpisodesViewModel.kt`, `PodcastEpisodesScreen.kt`

**Interfaces:**
- Consumes: `PodcastRepository.observeFeed` (Task 1).
- Produces: `PodcastEpisodesUiState.feedAuthor: String?`; `@Composable fun PodcastHeader(title: String, author: String?, imageUrl: String?, topPadding: Dp)`.

Compose UI without UI-test setup: gate is build + manual pass.

- [ ] **Step 1: Observe the feed in the view model**

In `PodcastEpisodesUiState` add `val feedAuthor: String? = null` after `feedImageUrl`. In `init`, replace the `viewModelScope.launch { val feed = podcastRepository.getFeedById(feedId) ... }` block with:

```kotlin
        podcastRepository.observeFeed(feedId)
            .onEach { feed ->
                _uiState.update {
                    it.copy(feedTitle = feed?.title.orEmpty(), feedImageUrl = feed?.imageUrl, feedAuthor = feed?.author)
                }
            }
            .launchIn(viewModelScope)
```

- [ ] **Step 2: Create `PodcastHeader`**

```kotlin
package com.berpod.app.ui.podcastlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private val HeaderShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
private val CoverShape = RoundedCornerShape(12.dp)

/**
 * AntennaPod-style podcast header: blurred, darkened cover behind a large cover, title and author.
 * [topPadding] keeps the content below the transparent top app bar drawn over it.
 */
@Composable
fun PodcastHeader(title: String, author: String?, imageUrl: String?, topPadding: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HeaderShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (imageUrl != null) {
            // blur() takes effect on Android 12+; older versions just show the darkened cover.
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(24.dp)
            )
        }
        Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f)))
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = topPadding + 8.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CoverShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Podcasts,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (author != null) {
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Rework the screen**

In `PodcastEpisodesScreen.kt`:
- Add imports: `androidx.compose.foundation.lazy.rememberLazyListState`, `androidx.compose.material3.TopAppBarDefaults`, `androidx.compose.runtime.derivedStateOf`, `androidx.compose.ui.graphics.Color`, `androidx.compose.foundation.layout.Column`.
- Before `Scaffold(`, add:

```kotlin
    val listState = rememberLazyListState()
    // Transparent bar over the header; solid with the title once the header has scrolled away.
    val isHeaderVisible by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val barContentColor = if (isHeaderVisible) Color.White else MaterialTheme.colorScheme.onSurface
```

- Change the `TopAppBar(...)`:
  - `title = { if (!isHeaderVisible) Text(uiState.feedTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },`
  - add `colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isHeaderVisible) Color.Transparent else MaterialTheme.colorScheme.surface, navigationIconContentColor = barContentColor, actionIconContentColor = barContentColor, titleContentColor = MaterialTheme.colorScheme.onSurface),`
  - the `CircularProgressIndicator` gets `color = barContentColor`.
- Replace the whole `Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) { ... }` with:

```kotlin
        // The header draws under the transparent top bar, so only the bottom padding is applied here.
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            item(key = "header") {
                PodcastHeader(
                    title = uiState.feedTitle,
                    author = uiState.feedAuthor,
                    imageUrl = uiState.feedImageUrl,
                    topPadding = paddingValues.calculateTopPadding()
                )
            }
            if (uiState.episodes.isEmpty()) {
                item(key = "empty") {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
                        EmptyState(
                            icon = Icons.Filled.Podcasts,
                            title = stringResource(R.string.podcast_episodes_empty),
                            subtitle = stringResource(R.string.action_refresh)
                        )
                    }
                }
            } else {
                items(uiState.episodes, key = { it.id }) { episode ->
                    EpisodeListItem(
                        episode = episode,
                        podcastTitle = null,
                        podcastImageUrl = uiState.feedImageUrl,
                        onAddToQueue = { viewModel.onAddToQueue(episode.id) },
                        onPlay = { viewModel.onPlay(episode) },
                        isCurrentlyPlaying = uiState.currentlyPlayingEpisodeId == episode.id && uiState.isPlaying,
                        onDownload = {},
                        progressFraction = episodeProgressFraction(episode.lastPositionMs, episode.durationSeconds),
                    )
                }
            }
        }
```

(`EmptyState` uses `fillMaxSize`; inside a lazy item it takes its content height, which is fine. Add imports `androidx.compose.material3.MaterialTheme`, `androidx.compose.ui.text.style.TextOverflow`, `androidx.compose.foundation.layout.fillMaxWidth` if missing; remove the now-unused `Box` import.)

- [ ] **Step 4: Build**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: 56 tests PASS, BUILD SUCCESSFUL.

- [ ] **Step 5: Manual pass on the emulator**

Install over the existing database, open Vinohradská 12 and Coffee break (screenshots, look at them):
- App starts (migration 5→6 ok), podcasts/tags/queue kept.
- Header: blurred darkened cover, 112dp cover, title; author empty before refresh.
- Tap ↻ → author appears (Vinohradská: "Český rozhlas"-like value from the feed).
- Scroll down → bar turns solid with the title and dark icons; scroll back → transparent with white icons.
- Tag button opens the tag dialog with the podcast title; episodes play / add to queue as before.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/berpod/app/ui/podcastlist/PodcastHeader.kt \
  app/src/main/java/com/berpod/app/ui/podcastlist/PodcastEpisodesViewModel.kt \
  app/src/main/java/com/berpod/app/ui/podcastlist/PodcastEpisodesScreen.kt
git commit -m "Show an artwork header with title and author on the podcast detail screen"
```
