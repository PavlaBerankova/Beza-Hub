# Podcast tags and subscription grid redesign — design doc

Date: 2026-09-27
Status: Approved in chat, awaiting written-spec review

## Purpose

The Podcasts screen (`PodcastListScreen`) shows every subscribed podcast in
one undifferentiated 4-column grid with 2dp gaps, no screen-edge padding and
4dp corners. The user wants it to look like AntennaPod's "Odběry" screen
(reference screenshot supplied in chat): square, noticeably rounded covers
with breathing room, and a row of filter chips at the top to show either all
podcasts or only those carrying one chosen tag.

Tags don't exist in the app today, so this adds a small tagging feature:
create, assign, rename, delete, filter.

Out of scope: new-episode count badges on covers, search, bottom-navigation
changes, persisting the selected filter across app restarts.

## User-facing behavior

### Grid
- Stays `LazyVerticalGrid` with `GridCells.Fixed(4)`, square covers
  (`aspectRatio(1f)`, `ContentScale.Crop`).
- 12dp content padding from the screen edges, 12dp horizontal and vertical
  spacing between items, 12dp corner radius.
- The grid shows only podcasts matching the selected filter. Order is
  unchanged (`dateAdded DESC`).

### Filter chip row
- Horizontally scrollable row (`LazyRow` of Material 3 `FilterChip`s)
  directly under the top app bar, above the grid.
- First chip: **"Všechny"**, then all tags sorted alphabetically
  (case-insensitive).
- Single-select: exactly one chip is selected at any time. Selected chip
  shows a check icon and the filled/selected FilterChip style (purple from
  the theme); others are outlined.
- The row is hidden entirely when no tags exist.
- Selecting a tag with no podcasts shows an empty-filter message
  ("Žádné podcasty s tímto štítkem") instead of the grid; the main empty
  state ("Zatím žádné podcasty") still applies only when there are no
  podcasts at all.
- Selection lives in `PodcastListViewModel`; it survives tab switches within
  the session and resets to "Všechny" on app restart.

### Assigning tags — `TagAssignDialog`
Opened from two places:
1. **Long-press on a cover** in the grid (`combinedClickable`; tap still
   opens the podcast).
2. **Tag icon button** (`Icons.AutoMirrored.Outlined.Label`) in the top app bar actions of
   `PodcastEpisodesScreen`.

Dialog content:
- Title: the podcast's name.
- Checkbox list of all tags (alphabetical); checked = the podcast has it.
  Toggling a checkbox applies immediately (no separate Save).
- Text field "Nový štítek" + "Přidat" button: creates the tag and assigns it
  to this podcast in one step. If a tag with that name already exists
  (case-insensitive, after trimming), it is just assigned instead of
  duplicated. Blank input disables the button.
- "Hotovo" button closes the dialog.

A podcast can have any number of tags.

### Managing tags
- **Long-press on a tag chip** (not "Všechny") opens a small menu:
  **Přejmenovat** and **Smazat**.
- Přejmenovat: dialog with a prefilled text field. Blank names are rejected
  (button disabled). Renaming to a name used by another tag
  (case-insensitive) shows the error "Štítek s tímto názvem už existuje".
- Smazat: confirmation dialog ("Smazat štítek „X“? Podcasty zůstanou.").
  Deleting removes the tag from all podcasts; the podcasts stay.
- If the deleted tag was the selected filter, selection falls back to
  "Všechny".
- Tags with no podcasts are kept until deleted explicitly.

## Data model

Room database version 3 → 4, new `MIGRATION_3_4` in `Migrations.kt`,
registered in `AppContainer` next to the existing migrations.

```kotlin
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String
)

@Entity(
    tableName = "podcast_tags",
    primaryKeys = ["podcastId", "tagId"],
    foreignKeys = [
        ForeignKey(PodcastFeedEntity::class, ["id"], ["podcastId"], onDelete = CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = CASCADE)
    ],
    indices = [Index(value = ["tagId"])]
)
data class PodcastTagCrossRef(val podcastId: Long, val tagId: Long)
```

`NOCASE` collation on `name` makes the unique index enforce
case-insensitive uniqueness at the database level and makes
`ORDER BY name` alphabetical without case issues. Diacritics are compared
as-is (SQLite NOCASE folds ASCII only), which is acceptable.

The migration SQL must match what Room generates for these entities
(verified against the exported schema JSON in `app/schemas/`).

### `TagDao`
- `observeAll(): Flow<List<TagEntity>>` — `ORDER BY name`
- `observeTagIdsForPodcast(podcastId): Flow<List<Long>>`
- `observeFeedsWithTag(tagId): Flow<List<PodcastFeedEntity>>` — join,
  `ORDER BY dateAdded DESC`
- `findByName(name): TagEntity?`
- `insert(tag): Long`, `rename(id, name)`, `delete(id)`
- `assign(crossRef)` (`OnConflictStrategy.IGNORE`), `unassign(podcastId, tagId)`

### `TagRepository`
Wraps `TagDao`; owns name rules so the UI never talks to the DAO directly.
- `observeTags()`, `observeTagIdsForPodcast(id)`, `observeFeeds(filterTagId: Long?)`
  (null → all feeds via `PodcastFeedDao.observeAll()`)
- `createAndAssign(podcastId, rawName): Result<Unit>` — trims; blank →
  failure; existing name → reuse its id
- `setAssigned(podcastId, tagId, assigned: Boolean)`
- `rename(tagId, rawName): Result<Unit>` — trims; blank or duplicate →
  failure with a Czech message
- `delete(tagId)`

Name normalization (`trim()`, blank check) lives in a small pure function
`normalizeTagName(raw: String): String?` so it's unit-testable.

## Components touched

- New: `data/local/TagEntity.kt`, `data/local/PodcastTagCrossRef.kt`,
  `data/local/TagDao.kt`, `data/repository/TagRepository.kt`,
  `ui/common/TagAssignDialog.kt`, `ui/podcastlist/TagChipRow.kt`
  (chip row + long-press menu + rename/delete dialogs).
- `AppDatabase.kt`: add entities, `tagDao()`, version 4.
- `Migrations.kt`: `MIGRATION_3_4`.
- `AppContainer.kt`: register migration, build `TagRepository`, pass it to
  the view model factory.
- `PodcastListViewModel.kt`: `tags`, `selectedTagId`, filtered `feeds`
  (`flatMapLatest` on the selected tag), tag-dialog target podcast,
  rename/delete actions.
- `PodcastListScreen.kt`: padding/spacing/corners, chip row, long-press on
  covers, dialogs.
- `PodcastEpisodesViewModel.kt` / `PodcastEpisodesScreen.kt`: tag button in
  the top bar and the same `TagAssignDialog`.
- `strings.xml`: new Czech strings.

`TagAssignDialog` is stateless UI (tags, checked ids, callbacks); each
screen's view model supplies the data, so both entry points share one
component.

## Error handling

- Duplicate/blank names are handled in `TagRepository` and surfaced as
  inline dialog errors; they never crash.
- Assigning an already-assigned tag is a no-op (`IGNORE`).
- Deleting a podcast removes its tag links via cascade; deleting a tag
  removes its links via cascade.

## Testing

- JVM unit tests (the project has no Robolectric/instrumented setup):
  - `normalizeTagName`: trims, rejects blank/whitespace-only.
  - `TagRepository` with a fake `TagDao`: create reuses an existing
    same-name tag; rename rejects blank and duplicates; `observeFeeds(null)`
    returns all feeds.
  - `PodcastListViewModel`: deleting the selected tag resets the filter to
    "Všechny".
- Manual on the emulator: install over the existing v3 database (the
  Vinohradská 12 subscription must survive the migration); create tags via
  both entry points; filter; rename; delete; confirm the grid spacing
  matches the reference screenshot.
