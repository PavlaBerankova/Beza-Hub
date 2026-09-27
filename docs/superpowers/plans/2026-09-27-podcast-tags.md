# Podcast Tags Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the Podcasts grid to match AntennaPod's subscriptions screen and add user-defined tags with a single-select filter chip row, tag assignment (long-press cover / podcast detail), rename and delete.

**Architecture:** Two new Room tables (`tags`, `podcast_tags`) behind a `TagDao` and a `TagRepository` that owns naming rules. A reusable `TagAssignmentController` (plain class, driven by a view model's scope) holds the assign-dialog state so both entry points share one implementation and one stateless `TagAssignDialog`. `PodcastListViewModel` keeps the selected filter and switches the grid's source flow with `flatMapLatest`.

**Tech Stack:** Kotlin 2.4, Jetpack Compose (BOM 2026.09.00, Material 3, material-icons-extended), Room 2.8.5 with KSP and exported schemas, kotlinx-coroutines, JUnit 4 + kotlinx-coroutines-test (JVM tests only — no Robolectric/instrumented tests in this project).

**Spec:** `docs/superpowers/specs/2026-09-27-podcast-tags-design.md`

## Global Constraints

- All user-visible text is Czech and lives in `app/src/main/res/values/strings.xml` (error messages from repositories are Czech string literals, matching `PlaylistRepository`).
- Room DB version goes 3 → 4 via `MIGRATION_3_4`; existing data must survive (never `fallbackToDestructiveMigration`).
- Tag names: trimmed; blank rejected; unique case-insensitively (`COLLATE NOCASE`, ASCII folding only — accepted).
- Filter is single-select: `null` = "Všechny", otherwise one tag id. Resets to "Všechny" on app restart; not persisted.
- Grid: `GridCells.Fixed(4)`, square covers, 12dp edge padding, 12dp spacing, 12dp corner radius, order `dateAdded DESC`.
- Touch targets ≥ 48dp (established in the previous redesign).
- Don't rename packages/classes (`com.berpod.app`, `Berpod*`); the app's display name is `BEZA Hub`.
- Build: `./gradlew :app:assembleDebug`; tests: `./gradlew :app:testDebugUnitTest`. Run from repo root `/Users/Berda/Code/berpod`.

## Review Focus

1. Renaming a tag to a different-case version of its own name ("news" → "News") must succeed, not report a duplicate — pinned in Task 1 (`rename allows changing case of own name`).
2. Deleting the currently selected tag must drop the filter back to "Všechny", not leave an empty grid for a vanished tag — pinned in Task 2 (`resolveSelectedTagId` tests).
3. Typing an existing tag name with different case/whitespace in "Nový štítek" must reuse that tag and not crash on the unique index — pinned in Task 1 (`createAndAssign reuses existing tag case-insensitively`).
4. Toggling a checkbox in the assign dialog must be reflected immediately while the dialog is open (live observation), and closing then reopening for another podcast must not show the previous podcast's checks — pinned in Task 2 (controller tests).
5. The FAB must not permanently cover the last grid row, and a podcast with no artwork must still be visible and long-pressable — handled in Task 3 (bottom content padding, placeholder background), checked in the manual pass.

---

### Task 1: Tag data layer (entities, DAO, migration, repository)

**Files:**
- Create: `app/src/main/java/com/berpod/app/data/local/TagEntity.kt`
- Create: `app/src/main/java/com/berpod/app/data/local/PodcastTagCrossRef.kt`
- Create: `app/src/main/java/com/berpod/app/data/local/TagDao.kt`
- Create: `app/src/main/java/com/berpod/app/data/repository/TagRepository.kt`
- Modify: `app/src/main/java/com/berpod/app/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/berpod/app/data/local/Migrations.kt` (append)
- Modify: `app/src/main/java/com/berpod/app/di/AppContainer.kt`
- Create (generated): `app/schemas/com.berpod.app.data.local.AppDatabase/4.json`
- Create: `app/src/test/java/com/berpod/app/testutil/FakeTagDao.kt`
- Test: `app/src/test/java/com/berpod/app/data/repository/TagRepositoryTest.kt`

**Interfaces:**
- Consumes: `PodcastFeedEntity`, `PodcastFeedDao.observeAll()` (existing).
- Produces:
  - `data class TagEntity(id: Long = 0, name: String)`
  - `data class PodcastTagCrossRef(podcastId: Long, tagId: Long)`
  - `interface TagDao` (methods below)
  - `fun normalizeTagName(raw: String): String?`
  - `class TagRepository(tagDao: TagDao, podcastFeedDao: PodcastFeedDao)` with `observeTags(): Flow<List<TagEntity>>`, `observeTagIdsForPodcast(podcastId: Long): Flow<List<Long>>`, `observeFeeds(filterTagId: Long?): Flow<List<PodcastFeedEntity>>`, `suspend createAndAssign(podcastId: Long, rawName: String): Result<Unit>`, `suspend setAssigned(podcastId: Long, tagId: Long, assigned: Boolean)`, `suspend rename(tagId: Long, rawName: String): Result<Unit>`, `suspend delete(tagId: Long)`
  - `AppContainer.tagRepository: TagRepository`
  - Test fake: `class FakeTagDao(feeds: StateFlow<List<PodcastFeedEntity>>) : TagDao` with `val tags: StateFlow<List<TagEntity>>` and `val links: StateFlow<Set<PodcastTagCrossRef>>` (test package `com.berpod.app.testutil`), plus `class FakePodcastFeedDao(initial: List<PodcastFeedEntity>) : PodcastFeedDao` and `fun feed(id: Long, dateAdded: Long = id): PodcastFeedEntity`.

- [ ] **Step 1: Commit the pending app rename separately**

`strings.xml` already contains the uncommitted rename `Berpod` → `BEZA Hub`. Commit it on its own so it doesn't get mixed into tag work:

```bash
cd /Users/Berda/Code/berpod
git add app/src/main/res/values/strings.xml
git commit -m "Rename app display name to BEZA Hub"
```

- [ ] **Step 2: Create the entities**

`app/src/main/java/com/berpod/app/data/local/TagEntity.kt`:

```kotlin
package com.berpod.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // NOCASE makes the unique index and `name = :name` lookups case-insensitive (ASCII letters only).
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String
)
```

`app/src/main/java/com/berpod/app/data/local/PodcastTagCrossRef.kt`:

```kotlin
package com.berpod.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "podcast_tags",
    primaryKeys = ["podcastId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = PodcastFeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["podcastId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tagId"])]
)
data class PodcastTagCrossRef(
    val podcastId: Long,
    val tagId: Long
)
```

- [ ] **Step 3: Create `TagDao`**

`app/src/main/java/com/berpod/app/data/local/TagDao.kt`:

```kotlin
package com.berpod.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT tagId FROM podcast_tags WHERE podcastId = :podcastId")
    fun observeTagIdsForPodcast(podcastId: Long): Flow<List<Long>>

    @Query(
        """
        SELECT podcast_feeds.* FROM podcast_feeds
        INNER JOIN podcast_tags ON podcast_feeds.id = podcast_tags.podcastId
        WHERE podcast_tags.tagId = :tagId
        ORDER BY podcast_feeds.dateAdded DESC
        """
    )
    fun observeFeedsWithTag(tagId: Long): Flow<List<PodcastFeedEntity>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tag: TagEntity): Long

    @Query("UPDATE tags SET name = :name WHERE id = :tagId")
    suspend fun rename(tagId: Long, name: String)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun delete(tagId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun assign(crossRef: PodcastTagCrossRef)

    @Query("DELETE FROM podcast_tags WHERE podcastId = :podcastId AND tagId = :tagId")
    suspend fun unassign(podcastId: Long, tagId: Long)
}
```

- [ ] **Step 4: Register entities, bump version, add migration**

In `AppDatabase.kt`, add `TagEntity::class` and `PodcastTagCrossRef::class` to `entities`, change `version = 3` to `version = 4`, and add:

```kotlin
    abstract fun tagDao(): TagDao
```

Append to `Migrations.kt`:

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL COLLATE NOCASE)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `podcast_tags` (
                `podcastId` INTEGER NOT NULL,
                `tagId` INTEGER NOT NULL,
                PRIMARY KEY(`podcastId`, `tagId`),
                FOREIGN KEY(`podcastId`) REFERENCES `podcast_feeds`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_podcast_tags_tagId` ON `podcast_tags` (`tagId`)")
    }
}
```

In `AppContainer.kt`: import `MIGRATION_3_4` and `TagRepository`; change `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` to `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`; add `val tagRepository: TagRepository` to the `AppContainer` interface and to `DefaultAppContainer`:

```kotlin
    override val tagRepository: TagRepository by lazy {
        TagRepository(database.tagDao(), database.podcastFeedDao())
    }
```

- [ ] **Step 5: Write the test fakes**

`app/src/test/java/com/berpod/app/testutil/FakeTagDao.kt`:

```kotlin
package com.berpod.app.testutil

import com.berpod.app.data.local.PodcastFeedDao
import com.berpod.app.data.local.PodcastFeedEntity
import com.berpod.app.data.local.PodcastTagCrossRef
import com.berpod.app.data.local.TagDao
import com.berpod.app.data.local.TagEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

fun feed(id: Long, dateAdded: Long = id) = PodcastFeedEntity(
    id = id,
    rssUrl = "https://example.com/$id.xml",
    title = "Podcast $id",
    imageUrl = null,
    description = null,
    dateAdded = dateAdded,
    lastFetchedAt = null
)

class FakePodcastFeedDao(initial: List<PodcastFeedEntity> = emptyList()) : PodcastFeedDao {
    val feeds = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<PodcastFeedEntity>> =
        feeds.map { list -> list.sortedByDescending { it.dateAdded } }

    override suspend fun existsByUrl(rssUrl: String) = feeds.value.any { it.rssUrl == rssUrl }
    override suspend fun getAll() = feeds.value
    override suspend fun getById(feedId: Long) = feeds.value.find { it.id == feedId }
    override suspend fun insert(feed: PodcastFeedEntity): Long {
        feeds.value = feeds.value + feed
        return feed.id
    }
    override suspend fun delete(feed: PodcastFeedEntity) {
        feeds.value = feeds.value.filterNot { it.id == feed.id }
    }
}

/** In-memory TagDao that mimics NOCASE lookups, IGNORE on assign, and cascade on delete. */
class FakeTagDao(private val feeds: StateFlow<List<PodcastFeedEntity>>) : TagDao {
    private val _tags = MutableStateFlow<List<TagEntity>>(emptyList())
    private val _links = MutableStateFlow<Set<PodcastTagCrossRef>>(emptySet())
    val tags: StateFlow<List<TagEntity>> = _tags
    val links: StateFlow<Set<PodcastTagCrossRef>> = _links
    private var nextId = 1L

    override fun observeAll(): Flow<List<TagEntity>> =
        _tags.map { list -> list.sortedBy { it.name.lowercase() } }

    override fun observeTagIdsForPodcast(podcastId: Long): Flow<List<Long>> =
        _links.map { links -> links.filter { it.podcastId == podcastId }.map { it.tagId } }

    override fun observeFeedsWithTag(tagId: Long): Flow<List<PodcastFeedEntity>> =
        combine(feeds, _links) { feedList, links ->
            val ids = links.filter { it.tagId == tagId }.map { it.podcastId }.toSet()
            feedList.filter { it.id in ids }.sortedByDescending { it.dateAdded }
        }

    override suspend fun findByName(name: String): TagEntity? =
        _tags.value.find { it.name.equals(name, ignoreCase = true) }

    override suspend fun insert(tag: TagEntity): Long {
        check(findByName(tag.name) == null) { "UNIQUE constraint failed: tags.name" }
        val withId = tag.copy(id = nextId++)
        _tags.value = _tags.value + withId
        return withId.id
    }

    override suspend fun rename(tagId: Long, name: String) {
        _tags.value = _tags.value.map { if (it.id == tagId) it.copy(name = name) else it }
    }

    override suspend fun delete(tagId: Long) {
        _tags.value = _tags.value.filterNot { it.id == tagId }
        _links.value = _links.value.filterNot { it.tagId == tagId }.toSet()
    }

    override suspend fun assign(crossRef: PodcastTagCrossRef) {
        _links.value = _links.value + crossRef
    }

    override suspend fun unassign(podcastId: Long, tagId: Long) {
        _links.value = _links.value - PodcastTagCrossRef(podcastId, tagId)
    }
}
```

- [ ] **Step 6: Write the failing repository tests**

`app/src/test/java/com/berpod/app/data/repository/TagRepositoryTest.kt`:

```kotlin
package com.berpod.app.data.repository

import com.berpod.app.data.local.PodcastTagCrossRef
import com.berpod.app.testutil.FakePodcastFeedDao
import com.berpod.app.testutil.FakeTagDao
import com.berpod.app.testutil.feed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagRepositoryTest {

    private val feedDao = FakePodcastFeedDao(listOf(feed(1), feed(2), feed(3)))
    private val tagDao = FakeTagDao(feedDao.feeds)
    private val repository = TagRepository(tagDao, feedDao)

    @Test
    fun `normalizeTagName trims and rejects blank`() {
        assertEquals("News", normalizeTagName("  News  "))
        assertNull(normalizeTagName(""))
        assertNull(normalizeTagName("   "))
    }

    @Test
    fun `createAndAssign creates tag and links podcast`() = runTest {
        val result = repository.createAndAssign(podcastId = 1, rawName = " Health ")

        assertTrue(result.isSuccess)
        assertEquals(listOf("Health"), tagDao.tags.value.map { it.name })
        assertEquals(setOf(PodcastTagCrossRef(1, tagDao.tags.value.single().id)), tagDao.links.value)
    }

    @Test
    fun `createAndAssign reuses existing tag case-insensitively`() = runTest {
        repository.createAndAssign(podcastId = 1, rawName = "News")

        val result = repository.createAndAssign(podcastId = 2, rawName = "  news ")

        assertTrue(result.isSuccess)
        assertEquals(1, tagDao.tags.value.size)
        val tagId = tagDao.tags.value.single().id
        assertEquals(setOf(PodcastTagCrossRef(1, tagId), PodcastTagCrossRef(2, tagId)), tagDao.links.value)
    }

    @Test
    fun `createAndAssign rejects blank name`() = runTest {
        assertTrue(repository.createAndAssign(podcastId = 1, rawName = "  ").isFailure)
        assertTrue(tagDao.tags.value.isEmpty())
    }

    @Test
    fun `rename rejects blank and duplicate names`() = runTest {
        repository.createAndAssign(1, "Health")
        repository.createAndAssign(1, "News")
        val newsId = tagDao.findByName("News")!!.id

        assertTrue(repository.rename(newsId, " ").isFailure)
        val duplicate = repository.rename(newsId, "health")
        assertTrue(duplicate.isFailure)
        assertEquals("Štítek s tímto názvem už existuje", duplicate.exceptionOrNull()?.message)
        assertEquals("News", tagDao.tags.value.first { it.id == newsId }.name)
    }

    @Test
    fun `rename allows changing case of own name`() = runTest {
        repository.createAndAssign(1, "news")
        val id = tagDao.tags.value.single().id

        assertTrue(repository.rename(id, "News").isSuccess)
        assertEquals("News", tagDao.tags.value.single().name)
    }

    @Test
    fun `setAssigned adds and removes a link`() = runTest {
        repository.createAndAssign(1, "News")
        val id = tagDao.tags.value.single().id

        repository.setAssigned(podcastId = 2, tagId = id, assigned = true)
        assertTrue(PodcastTagCrossRef(2, id) in tagDao.links.value)

        repository.setAssigned(podcastId = 2, tagId = id, assigned = false)
        assertTrue(PodcastTagCrossRef(2, id) !in tagDao.links.value)
    }

    @Test
    fun `observeFeeds returns all feeds for null filter and tagged feeds otherwise`() = runTest {
        repository.createAndAssign(1, "News")
        repository.createAndAssign(3, "News")
        val id = tagDao.tags.value.single().id

        assertEquals(listOf(3L, 2L, 1L), repository.observeFeeds(null).first().map { it.id })
        assertEquals(listOf(3L, 1L), repository.observeFeeds(id).first().map { it.id })
    }

    @Test
    fun `delete removes tag and its links`() = runTest {
        repository.createAndAssign(1, "News")
        val id = tagDao.tags.value.single().id

        repository.delete(id)

        assertTrue(tagDao.tags.value.isEmpty())
        assertTrue(tagDao.links.value.isEmpty())
    }
}
```

- [ ] **Step 7: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.berpod.app.data.repository.TagRepositoryTest"`
Expected: compilation FAIL — `Unresolved reference: TagRepository` / `normalizeTagName`.

- [ ] **Step 8: Implement `TagRepository`**

`app/src/main/java/com/berpod/app/data/repository/TagRepository.kt`:

```kotlin
package com.berpod.app.data.repository

import com.berpod.app.data.local.PodcastFeedDao
import com.berpod.app.data.local.PodcastFeedEntity
import com.berpod.app.data.local.PodcastTagCrossRef
import com.berpod.app.data.local.TagDao
import com.berpod.app.data.local.TagEntity
import kotlinx.coroutines.flow.Flow

/** Returns the trimmed tag name, or null when it's blank. */
fun normalizeTagName(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }

class TagRepository(
    private val tagDao: TagDao,
    private val podcastFeedDao: PodcastFeedDao
) {

    fun observeTags(): Flow<List<TagEntity>> = tagDao.observeAll()

    fun observeTagIdsForPodcast(podcastId: Long): Flow<List<Long>> = tagDao.observeTagIdsForPodcast(podcastId)

    /** All feeds when [filterTagId] is null, otherwise only feeds carrying that tag. */
    fun observeFeeds(filterTagId: Long?): Flow<List<PodcastFeedEntity>> =
        if (filterTagId == null) podcastFeedDao.observeAll() else tagDao.observeFeedsWithTag(filterTagId)

    suspend fun createAndAssign(podcastId: Long, rawName: String): Result<Unit> = runCatching {
        val name = requireNotNull(normalizeTagName(rawName)) { "Zadejte název štítku" }
        val tagId = tagDao.findByName(name)?.id ?: tagDao.insert(TagEntity(name = name))
        tagDao.assign(PodcastTagCrossRef(podcastId, tagId))
    }

    suspend fun setAssigned(podcastId: Long, tagId: Long, assigned: Boolean) {
        if (assigned) tagDao.assign(PodcastTagCrossRef(podcastId, tagId)) else tagDao.unassign(podcastId, tagId)
    }

    suspend fun rename(tagId: Long, rawName: String): Result<Unit> = runCatching {
        val name = requireNotNull(normalizeTagName(rawName)) { "Zadejte název štítku" }
        val existing = tagDao.findByName(name)
        require(existing == null || existing.id == tagId) { "Štítek s tímto názvem už existuje" }
        tagDao.rename(tagId, name)
    }

    suspend fun delete(tagId: Long) = tagDao.delete(tagId)
}
```

- [ ] **Step 9: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all existing tests plus the 9 new ones).

- [ ] **Step 10: Build and check the generated schema matches the migration**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL and a new `app/schemas/com.berpod.app.data.local.AppDatabase/4.json`.

Run: `grep -n '"createSql"' app/schemas/com.berpod.app.data.local.AppDatabase/4.json | grep -E 'tags|podcast_tags'`
Expected: the `tags` / `index_tags_name` / `podcast_tags` / `index_podcast_tags_tagId` statements have the same columns, types, `NOT NULL`, `COLLATE NOCASE`, primary key, foreign keys and index columns as `MIGRATION_3_4` (whitespace may differ). If anything differs, fix `MIGRATION_3_4` to match the JSON — Room validates the migrated schema at open time and crashes on mismatch.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/berpod/app/data/local/TagEntity.kt \
  app/src/main/java/com/berpod/app/data/local/PodcastTagCrossRef.kt \
  app/src/main/java/com/berpod/app/data/local/TagDao.kt \
  app/src/main/java/com/berpod/app/data/local/AppDatabase.kt \
  app/src/main/java/com/berpod/app/data/local/Migrations.kt \
  app/src/main/java/com/berpod/app/data/repository/TagRepository.kt \
  app/src/main/java/com/berpod/app/di/AppContainer.kt \
  app/schemas/com.berpod.app.data.local.AppDatabase/4.json \
  app/src/test/java/com/berpod/app/testutil/FakeTagDao.kt \
  app/src/test/java/com/berpod/app/data/repository/TagRepositoryTest.kt
git commit -m "Add tags and podcast-tag links to the database with TagRepository"
```

---

### Task 2: Tag assignment controller and filter state

**Files:**
- Create: `app/src/main/java/com/berpod/app/ui/common/TagAssignmentController.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/podcastlist/PodcastListViewModel.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/navigation/BerpodApp.kt:122` (factory call)
- Test: `app/src/test/java/com/berpod/app/ui/common/TagAssignmentControllerTest.kt`
- Test: `app/src/test/java/com/berpod/app/ui/podcastlist/TagFilterTest.kt`

**Interfaces:**
- Consumes: `TagRepository`, `TagEntity`, `FakeTagDao`, `FakePodcastFeedDao`, `feed()` from Task 1.
- Produces:
  - `data class TagAssignState(podcastId: Long, podcastTitle: String, tags: List<TagEntity> = emptyList(), checkedTagIds: Set<Long> = emptySet(), errorMessage: String? = null)`
  - `class TagAssignmentController(tagRepository: TagRepository, scope: CoroutineScope)` with `val state: StateFlow<TagAssignState?>`, `fun open(podcastId: Long, podcastTitle: String)`, `fun dismiss()`, `fun setAssigned(tagId: Long, assigned: Boolean)`, `fun createAndAssign(name: String)`
  - `fun resolveSelectedTagId(selected: Long?, tags: List<TagEntity>): Long?` (top-level in `PodcastListViewModel.kt`)
  - `PodcastListUiState` gains `hasAnyPodcasts: Boolean`, `tags: List<TagEntity>`, `selectedTagId: Long?`, `renamingTag: TagEntity?`, `renameError: String?`, `deletingTag: TagEntity?`
  - `PodcastListViewModel(repository, episodeRepository, tagRepository)` with `val tagAssignment: TagAssignmentController`, `onSelectTag(tagId: Long?)`, `onOpenTagDialog(feed: PodcastFeedEntity)`, `onRequestRenameTag(tag: TagEntity)`, `onDismissRenameTag()`, `onConfirmRenameTag(name: String)`, `onRequestDeleteTag(tag: TagEntity)`, `onDismissDeleteTag()`, `onConfirmDeleteTag()`
  - `PodcastListViewModel.factory(repository, episodeRepository, tagRepository)`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/berpod/app/ui/podcastlist/TagFilterTest.kt`:

```kotlin
package com.berpod.app.ui.podcastlist

import com.berpod.app.data.local.TagEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagFilterTest {

    private val tags = listOf(TagEntity(1, "Health"), TagEntity(2, "News"))

    @Test
    fun `keeps all-filter as null`() {
        assertNull(resolveSelectedTagId(null, tags))
    }

    @Test
    fun `keeps a selection that still exists`() {
        assertEquals(2L, resolveSelectedTagId(2L, tags))
    }

    @Test
    fun `falls back to all when the selected tag was deleted`() {
        assertNull(resolveSelectedTagId(3L, tags))
        assertNull(resolveSelectedTagId(1L, emptyList()))
    }
}
```

`app/src/test/java/com/berpod/app/ui/common/TagAssignmentControllerTest.kt`:

```kotlin
package com.berpod.app.ui.common

import com.berpod.app.data.repository.TagRepository
import com.berpod.app.testutil.FakePodcastFeedDao
import com.berpod.app.testutil.FakeTagDao
import com.berpod.app.testutil.feed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagAssignmentControllerTest {

    private val feedDao = FakePodcastFeedDao(listOf(feed(1), feed(2)))
    private val tagDao = FakeTagDao(feedDao.feeds)
    private val repository = TagRepository(tagDao, feedDao)

    @Test
    fun `open shows all tags and the podcast's checked ones`() = runTest(UnconfinedTestDispatcher()) {
        repository.createAndAssign(1, "News")
        repository.createAndAssign(2, "Health")
        val controller = TagAssignmentController(repository, backgroundScope)

        controller.open(podcastId = 1, podcastTitle = "Podcast 1")

        val state = controller.state.value!!
        assertEquals("Podcast 1", state.podcastTitle)
        assertEquals(listOf("Health", "News"), state.tags.map { it.name })
        assertEquals(setOf(tagDao.findByName("News")!!.id), state.checkedTagIds)
    }

    @Test
    fun `toggling and creating update the open dialog live`() = runTest(UnconfinedTestDispatcher()) {
        repository.createAndAssign(2, "Health")
        val healthId = tagDao.findByName("Health")!!.id
        val controller = TagAssignmentController(repository, backgroundScope)
        controller.open(1, "Podcast 1")

        controller.setAssigned(healthId, true)
        assertEquals(setOf(healthId), controller.state.value!!.checkedTagIds)

        controller.createAndAssign(" News ")
        val newsId = tagDao.findByName("News")!!.id
        assertEquals(setOf(healthId, newsId), controller.state.value!!.checkedTagIds)

        controller.setAssigned(healthId, false)
        assertEquals(setOf(newsId), controller.state.value!!.checkedTagIds)
    }

    @Test
    fun `reopening for another podcast drops previous checks and dismiss clears state`() =
        runTest(UnconfinedTestDispatcher()) {
            repository.createAndAssign(1, "News")
            val controller = TagAssignmentController(repository, backgroundScope)

            controller.open(1, "Podcast 1")
            controller.dismiss()
            assertNull(controller.state.value)

            controller.open(2, "Podcast 2")
            assertEquals(2L, controller.state.value!!.podcastId)
            assertEquals(emptySet<Long>(), controller.state.value!!.checkedTagIds)

            // A late change to podcast 1 must not leak into podcast 2's dialog.
            repository.setAssigned(1, tagDao.findByName("News")!!.id, false)
            assertEquals(2L, controller.state.value!!.podcastId)
        }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.berpod.app.ui.*"`
Expected: compilation FAIL — `Unresolved reference: resolveSelectedTagId` / `TagAssignmentController`.

- [ ] **Step 3: Implement `TagAssignmentController`**

`app/src/main/java/com/berpod/app/ui/common/TagAssignmentController.kt`:

```kotlin
package com.berpod.app.ui.common

import com.berpod.app.data.local.TagEntity
import com.berpod.app.data.repository.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TagAssignState(
    val podcastId: Long,
    val podcastTitle: String,
    val tags: List<TagEntity> = emptyList(),
    val checkedTagIds: Set<Long> = emptySet(),
    val errorMessage: String? = null
)

/**
 * State and actions for the "assign tags to a podcast" dialog, shared by every screen that offers it.
 * The owning view model passes its viewModelScope; state is null while the dialog is closed.
 */
class TagAssignmentController(
    private val tagRepository: TagRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<TagAssignState?>(null)
    val state: StateFlow<TagAssignState?> = _state.asStateFlow()

    private var observeJob: Job? = null

    fun open(podcastId: Long, podcastTitle: String) {
        observeJob?.cancel()
        _state.value = TagAssignState(podcastId, podcastTitle)
        observeJob = combine(
            tagRepository.observeTags(),
            tagRepository.observeTagIdsForPodcast(podcastId)
        ) { tags, checkedIds -> tags to checkedIds.toSet() }
            .onEach { (tags, checkedIds) ->
                _state.update { it?.copy(tags = tags, checkedTagIds = checkedIds) }
            }
            .launchIn(scope)
    }

    fun dismiss() {
        observeJob?.cancel()
        observeJob = null
        _state.value = null
    }

    fun setAssigned(tagId: Long, assigned: Boolean) {
        val podcastId = _state.value?.podcastId ?: return
        scope.launch { tagRepository.setAssigned(podcastId, tagId, assigned) }
    }

    fun createAndAssign(name: String) {
        val podcastId = _state.value?.podcastId ?: return
        scope.launch {
            tagRepository.createAndAssign(podcastId, name)
                .onSuccess { _state.update { it?.copy(errorMessage = null) } }
                .onFailure { error -> _state.update { it?.copy(errorMessage = error.message) } }
        }
    }
}
```

- [ ] **Step 4: Extend `PodcastListViewModel`**

Replace the `PodcastListUiState` data class and the class header/`init` block in `PodcastListViewModel.kt`, keep `onFabClick`, `onDismissAddDialog`, `onAddFeed` unchanged, add the new methods, and update the factory. Resulting file:

```kotlin
package com.berpod.app.ui.podcastlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.PodcastFeedEntity
import com.berpod.app.data.local.TagEntity
import com.berpod.app.data.repository.EpisodeRepository
import com.berpod.app.data.repository.PodcastRepository
import com.berpod.app.data.repository.TagRepository
import com.berpod.app.ui.common.TagAssignmentController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PodcastListUiState(
    /** Feeds matching the selected filter. */
    val feeds: List<PodcastFeedEntity> = emptyList(),
    /** Whether any podcast exists at all, independent of the filter. */
    val hasAnyPodcasts: Boolean = false,
    val tags: List<TagEntity> = emptyList(),
    /** null = "Všechny". */
    val selectedTagId: Long? = null,
    val renamingTag: TagEntity? = null,
    val renameError: String? = null,
    val deletingTag: TagEntity? = null,
    val isAddDialogOpen: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

/** Keeps [selected] if that tag still exists, otherwise falls back to "Všechny" (null). */
fun resolveSelectedTagId(selected: Long?, tags: List<TagEntity>): Long? =
    selected?.takeIf { id -> tags.any { it.id == id } }

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastListViewModel(
    private val repository: PodcastRepository,
    private val episodeRepository: EpisodeRepository,
    private val tagRepository: TagRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastListUiState())
    val uiState: StateFlow<PodcastListUiState> = _uiState.asStateFlow()

    val tagAssignment = TagAssignmentController(tagRepository, viewModelScope)

    private val selectedTagId = MutableStateFlow<Long?>(null)

    init {
        repository.observeFeeds()
            .onEach { feeds -> _uiState.update { it.copy(hasAnyPodcasts = feeds.isNotEmpty()) } }
            .launchIn(viewModelScope)
        selectedTagId
            .flatMapLatest { tagId -> tagRepository.observeFeeds(tagId) }
            .onEach { feeds -> _uiState.update { it.copy(feeds = feeds) } }
            .launchIn(viewModelScope)
        tagRepository.observeTags()
            .onEach { tags ->
                selectedTagId.value = resolveSelectedTagId(selectedTagId.value, tags)
                _uiState.update { it.copy(tags = tags, selectedTagId = selectedTagId.value) }
            }
            .launchIn(viewModelScope)
    }

    fun onSelectTag(tagId: Long?) {
        selectedTagId.value = tagId
        _uiState.update { it.copy(selectedTagId = tagId) }
    }

    fun onOpenTagDialog(feed: PodcastFeedEntity) = tagAssignment.open(feed.id, feed.title)

    fun onRequestRenameTag(tag: TagEntity) {
        _uiState.update { it.copy(renamingTag = tag, renameError = null) }
    }

    fun onDismissRenameTag() {
        _uiState.update { it.copy(renamingTag = null, renameError = null) }
    }

    fun onConfirmRenameTag(name: String) {
        val tag = _uiState.value.renamingTag ?: return
        viewModelScope.launch {
            tagRepository.rename(tag.id, name)
                .onSuccess { _uiState.update { it.copy(renamingTag = null, renameError = null) } }
                .onFailure { error -> _uiState.update { it.copy(renameError = error.message) } }
        }
    }

    fun onRequestDeleteTag(tag: TagEntity) {
        _uiState.update { it.copy(deletingTag = tag) }
    }

    fun onDismissDeleteTag() {
        _uiState.update { it.copy(deletingTag = null) }
    }

    fun onConfirmDeleteTag() {
        val tag = _uiState.value.deletingTag ?: return
        _uiState.update { it.copy(deletingTag = null) }
        // The tags observer resets the filter to "Všechny" if this was the selected tag.
        viewModelScope.launch { tagRepository.delete(tag.id) }
    }

    // onFabClick, onDismissAddDialog, onAddFeed: unchanged from the current file — keep them here verbatim.

    companion object {
        fun factory(
            repository: PodcastRepository,
            episodeRepository: EpisodeRepository,
            tagRepository: TagRepository
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PodcastListViewModel(repository, episodeRepository, tagRepository) as T
            }
        }
    }
}
```

(The comment line about `onFabClick`/`onDismissAddDialog`/`onAddFeed` is an instruction to the implementer, not code to paste: copy those three existing functions into that spot unchanged and don't keep the comment.)

In `BerpodApp.kt`, change the Podcasts factory call to:

```kotlin
                    viewModelFactory = PodcastListViewModel.factory(
                        container.podcastRepository,
                        container.episodeRepository,
                        container.tagRepository
                    ),
```

- [ ] **Step 5: Run tests and build**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: all tests PASS, BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/berpod/app/ui/common/TagAssignmentController.kt \
  app/src/main/java/com/berpod/app/ui/podcastlist/PodcastListViewModel.kt \
  app/src/main/java/com/berpod/app/ui/navigation/BerpodApp.kt \
  app/src/test/java/com/berpod/app/ui/common/TagAssignmentControllerTest.kt \
  app/src/test/java/com/berpod/app/ui/podcastlist/TagFilterTest.kt
git commit -m "Add tag filter state and shared tag assignment controller"
```

---

### Task 3: Podcasts screen UI — grid restyle, chip row, dialogs

**Files:**
- Create: `app/src/main/java/com/berpod/app/ui/common/TagAssignDialog.kt`
- Create: `app/src/main/java/com/berpod/app/ui/podcastlist/TagChipRow.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/podcastlist/PodcastListScreen.kt` (full rewrite below)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `TagAssignState`, `PodcastListViewModel` API and `PodcastListUiState` fields from Task 2; `EmptyState(icon, title, subtitle)` from `ui/common/EmptyState.kt`.
- Produces:
  - `@Composable fun TagAssignDialog(state: TagAssignState, onToggle: (tagId: Long, assigned: Boolean) -> Unit, onCreate: (name: String) -> Unit, onDismiss: () -> Unit)`
  - `@Composable fun TagChipRow(tags: List<TagEntity>, selectedTagId: Long?, onSelect: (Long?) -> Unit, onRenameRequest: (TagEntity) -> Unit, onDeleteRequest: (TagEntity) -> Unit)`
  - `@Composable fun RenameTagDialog(tag: TagEntity, errorMessage: String?, onConfirm: (String) -> Unit, onDismiss: () -> Unit)`
  - `@Composable fun DeleteTagDialog(tag: TagEntity, onConfirm: () -> Unit, onDismiss: () -> Unit)`
  - String resources listed in Step 1 (Task 4 uses `action_manage_tags`).

- [ ] **Step 1: Add strings**

Insert before `</resources>` in `strings.xml`:

```xml

    <string name="filter_all">Všechny</string>
    <string name="tag_filter_empty">Žádné podcasty s tímto štítkem</string>
    <string name="tag_filter_empty_subtitle">Štítek přiřadíte dlouhým stiskem obálky</string>
    <string name="tag_dialog_new_label">Nový štítek</string>
    <string name="tag_name_label">Název štítku</string>
    <string name="action_manage_tags">Štítky</string>
    <string name="action_done">Hotovo</string>
    <string name="action_save">Uložit</string>
    <string name="action_rename">Přejmenovat</string>
    <string name="action_delete">Smazat</string>
    <string name="rename_tag_title">Přejmenovat štítek</string>
    <string name="delete_tag_title">Smazat štítek</string>
    <string name="delete_tag_message">Štítek „%1$s“ zmizí u všech podcastů. Podcasty zůstanou.</string>
```

- [ ] **Step 2: Create `TagAssignDialog`**

`app/src/main/java/com/berpod/app/ui/common/TagAssignDialog.kt`:

```kotlin
package com.berpod.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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

@Composable
fun TagAssignDialog(
    state: TagAssignState,
    onToggle: (tagId: Long, assigned: Boolean) -> Unit,
    onCreate: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember(state.podcastId) { mutableStateOf("") }

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
                        Text(tag.name, modifier = Modifier.padding(start = 12.dp))
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
}
```

- [ ] **Step 3: Create `TagChipRow` with the rename/delete dialogs**

Material 3 `FilterChip` has no long-press callback, so the chip is a small custom `Surface` styled like the reference (selected = filled primary with a check, unselected = outlined).

`app/src/main/java/com/berpod/app/ui/podcastlist/TagChipRow.kt`:

```kotlin
package com.berpod.app.ui.podcastlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.berpod.app.R
import com.berpod.app.data.local.TagEntity

private val ChipShape = RoundedCornerShape(8.dp)

@Composable
fun TagChipRow(
    tags: List<TagEntity>,
    selectedTagId: Long?,
    onSelect: (Long?) -> Unit,
    onRenameRequest: (TagEntity) -> Unit,
    onDeleteRequest: (TagEntity) -> Unit
) {
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
                    onLongClick = { isMenuOpen = true }
                )
                DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        onClick = {
                            isMenuOpen = false
                            onRenameRequest(tag)
                        }
                    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagChip(label: String, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = ChipShape,
        color = if (selected) colors.primary else colors.surface,
        contentColor = if (selected) colors.onPrimary else colors.onSurface,
        border = if (selected) null else BorderStroke(1.dp, colors.outline),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(40.dp)
            .clip(ChipShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun RenameTagDialog(tag: TagEntity, errorMessage: String?, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(tag.id) { mutableStateOf(tag.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_tag_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tag_name_label)) },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                )
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
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun DeleteTagDialog(tag: TagEntity, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_tag_title)) },
        text = { Text(stringResource(R.string.delete_tag_message, tag.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
```

- [ ] **Step 4: Rewrite `PodcastListScreen`**

Replace the whole of `PodcastListScreen.kt` with the following. The private no-arg `EmptyState()` is removed in favor of the shared `ui/common/EmptyState` (same icon/texts). The grid's bottom padding (88dp) keeps the last row clear of the FAB; the `surfaceVariant` background keeps covers without artwork visible and long-pressable.

```kotlin
package com.berpod.app.ui.podcastlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.berpod.app.R
import com.berpod.app.data.local.PodcastFeedEntity
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.TagAssignDialog

private val CoverShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastListScreen(viewModelFactory: ViewModelProvider.Factory, onPodcastClick: (Long) -> Unit) {
    val viewModel: PodcastListViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val tagAssignState by viewModel.tagAssignment.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onFabClick) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fab_add_podcast))
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.tags.isNotEmpty()) {
                TagChipRow(
                    tags = uiState.tags,
                    selectedTagId = uiState.selectedTagId,
                    onSelect = viewModel::onSelectTag,
                    onRenameRequest = viewModel::onRequestRenameTag,
                    onDeleteRequest = viewModel::onRequestDeleteTag
                )
            }
            when {
                !uiState.hasAnyPodcasts -> EmptyState(
                    icon = Icons.Filled.Podcasts,
                    title = stringResource(R.string.empty_state_title),
                    subtitle = stringResource(R.string.empty_state_subtitle)
                )
                uiState.feeds.isEmpty() -> EmptyState(
                    icon = Icons.AutoMirrored.Outlined.Label,
                    title = stringResource(R.string.tag_filter_empty),
                    subtitle = stringResource(R.string.tag_filter_empty_subtitle)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.feeds, key = { it.id }) { feed ->
                        PodcastGridItem(
                            feed = feed,
                            onClick = { onPodcastClick(feed.id) },
                            onLongClick = { viewModel.onOpenTagDialog(feed) }
                        )
                    }
                }
            }
        }

        if (uiState.isAddDialogOpen) {
            AddFeedDialog(
                isSubmitting = uiState.isSubmitting,
                errorMessage = uiState.errorMessage,
                onDismiss = viewModel::onDismissAddDialog,
                onSubmit = viewModel::onAddFeed
            )
        }
        tagAssignState?.let { state ->
            TagAssignDialog(
                state = state,
                onToggle = viewModel.tagAssignment::setAssigned,
                onCreate = viewModel.tagAssignment::createAndAssign,
                onDismiss = viewModel.tagAssignment::dismiss
            )
        }
        uiState.renamingTag?.let { tag ->
            RenameTagDialog(
                tag = tag,
                errorMessage = uiState.renameError,
                onConfirm = viewModel::onConfirmRenameTag,
                onDismiss = viewModel::onDismissRenameTag
            )
        }
        uiState.deletingTag?.let { tag ->
            DeleteTagDialog(
                tag = tag,
                onConfirm = viewModel::onConfirmDeleteTag,
                onDismiss = viewModel::onDismissDeleteTag
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PodcastGridItem(feed: PodcastFeedEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    AsyncImage(
        model = feed.imageUrl,
        contentDescription = feed.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(CoverShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    )
}
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (If the compiler flags `@OptIn(ExperimentalFoundationApi::class)` as unnecessary because `combinedClickable` is stable in this Compose version, remove the opt-in and its import — a warning, not an error.)

- [ ] **Step 6: Manual check on the emulator**

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.berpod.app
adb shell monkey -p com.berpod.app -c android.intent.category.LAUNCHER 1
```

Verify, taking `adb exec-out screencap -p > <scratchpad>/tags.png` screenshots and looking at them:
- The existing subscription (Vinohradská 12) survived the 3→4 migration and the app didn't crash on start.
- Covers have 12dp margins/gaps and rounded corners; no chip row yet (no tags).
- Long-press a cover → dialog with the podcast title; add "News" → it appears checked; add " news " → no duplicate appears; "Hotovo" closes.
- The chip row appears with "Všechny" (selected) and "News"; tapping "News" filters; the check moves.
- Long-press "News" → Přejmenovat → "IT Tech & AI" → the chip updates; renaming to an existing name shows the error.
- Long-press the selected chip → Smazat → confirm → the row disappears and all podcasts are shown again.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/berpod/app/ui/common/TagAssignDialog.kt \
  app/src/main/java/com/berpod/app/ui/podcastlist/TagChipRow.kt \
  app/src/main/java/com/berpod/app/ui/podcastlist/PodcastListScreen.kt \
  app/src/main/res/values/strings.xml
git commit -m "Restyle podcast grid and add tag filter chips with assign, rename and delete"
```

---

### Task 4: Tag button on the podcast detail screen

**Files:**
- Modify: `app/src/main/java/com/berpod/app/ui/podcastlist/PodcastEpisodesViewModel.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/podcastlist/PodcastEpisodesScreen.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/navigation/BerpodApp.kt:165-172` (factory call)

**Interfaces:**
- Consumes: `TagRepository`, `AppContainer.tagRepository` (Task 1); `TagAssignmentController`, `TagAssignState` (Task 2); `TagAssignDialog`, `R.string.action_manage_tags` (Task 3).
- Produces: `PodcastEpisodesViewModel(..., playbackRepository, tagRepository, feedId)` with `val tagAssignment: TagAssignmentController` and `fun onOpenTagDialog()`; `PodcastEpisodesViewModel.factory(..., playbackRepository, tagRepository, feedId)`.

- [ ] **Step 1: Extend `PodcastEpisodesViewModel`**

Add the import `com.berpod.app.data.repository.TagRepository` and `com.berpod.app.ui.common.TagAssignmentController`. Add `private val tagRepository: TagRepository` to the constructor between `playbackRepository` and `feedId`:

```kotlin
class PodcastEpisodesViewModel(
    private val podcastRepository: PodcastRepository,
    private val episodeRepository: EpisodeRepository,
    private val queueRepository: QueueRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackRepository: PlaybackRepository,
    private val tagRepository: TagRepository,
    private val feedId: Long
) : ViewModel() {
```

Below `val uiState`, add:

```kotlin
    val tagAssignment = TagAssignmentController(tagRepository, viewModelScope)
```

Add the method (next to `onRefresh`):

```kotlin
    fun onOpenTagDialog() = tagAssignment.open(feedId, _uiState.value.feedTitle)
```

Update the factory: add a `tagRepository: TagRepository` parameter between `playbackRepository` and `feedId`, and pass it in the same position to the constructor call.

In `BerpodApp.kt`, add `container.tagRepository,` between `container.playbackRepository,` and `feedId` in the `PodcastEpisodesViewModel.factory(...)` call.

- [ ] **Step 2: Add the top-bar button and dialog to `PodcastEpisodesScreen`**

Add imports:

```kotlin
import androidx.compose.material.icons.automirrored.outlined.Label
import com.berpod.app.ui.common.TagAssignDialog
```

After `val uiState by viewModel.uiState.collectAsState()`, add:

```kotlin
    val tagAssignState by viewModel.tagAssignment.state.collectAsState()
```

In the `TopAppBar` `actions` block, insert this as the first action (before the refresh indicator/button):

```kotlin
                    IconButton(onClick = viewModel::onOpenTagDialog) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Label,
                            contentDescription = stringResource(R.string.action_manage_tags)
                        )
                    }
```

After the `AddToPlaylistDialog` `if` block (still inside the `Scaffold` content lambda), add:

```kotlin
        tagAssignState?.let { state ->
            TagAssignDialog(
                state = state,
                onToggle = viewModel.tagAssignment::setAssigned,
                onCreate = viewModel.tagAssignment::createAndAssign,
                onDismiss = viewModel.tagAssignment::dismiss
            )
        }
```

- [ ] **Step 3: Run tests and build**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: all tests PASS, BUILD SUCCESSFUL.

- [ ] **Step 4: Full manual pass on the emulator**

Install and launch as in Task 3 Step 6, then:
- Open Vinohradská 12 → tap the tag icon in the top bar → the same dialog opens with its current tags checked; toggle one, create a new one, "Hotovo".
- Back on Podcasts: the chip row shows the new tag; filtering by it shows the podcast; unchecking the tag on the detail screen removes it from that filter immediately (empty-filter message "Žádné podcasty s tímto štítkem").
- Force-stop and relaunch: tags and assignments persist; the filter is back on "Všechny".
- Compare the grid against the reference screenshot (padding, gaps, corners, chip look).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/berpod/app/ui/podcastlist/PodcastEpisodesViewModel.kt \
  app/src/main/java/com/berpod/app/ui/podcastlist/PodcastEpisodesScreen.kt \
  app/src/main/java/com/berpod/app/ui/navigation/BerpodApp.kt
git commit -m "Add tag assignment button to the podcast detail screen"
```
