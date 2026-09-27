# Queue tag filter and playlist removal — design doc

Date: 2026-09-27
Status: Approved in chat, awaiting written-spec review
Builds on: `2026-09-27-podcast-tags-design.md` (same branch `feature/podcast-tags`)

## Purpose

With podcast tags in place, the user no longer needs playlists: the queue
plus tags covers the use case. Playlists are removed entirely, and the
Queue screen gets the same tag filter chip row as the Podcasts screen so
the user can show, e.g., only queued episodes from podcasts tagged "IT".

Tags stay on podcasts. An episode matches a tag filter when its podcast
has that tag.

Out of scope: tagging individual episodes, managing tags from the Queue,
persisting the selected filter across restarts, a shared filter between
screens.

## Part 1 — Remove playlists

User decision: delete playlists including their stored data.

### User-facing
- The **Playlisty** bottom-navigation tab and the playlist detail screen
  are removed. Bottom navigation has three tabs: Podcasty, Fronta, Nové.
- The "Přidat do playlistu" action disappears from every episode row
  (New Episodes, Podcast Episodes; Queue never had it).
- The "Nový playlist" and "Přidat do playlistu" dialogs are removed.

### Data
- Room database version 4 → 5 via `MIGRATION_4_5`:
  `DROP TABLE IF EXISTS playlist_episodes` then
  `DROP TABLE IF EXISTS playlists` (child table first because of its
  foreign key). Podcasts, episodes, queue entries, tags and tag links are
  untouched.
- `PlaylistEntity` and `PlaylistEpisodeCrossRef` are removed from
  `AppDatabase.entities`; `playlistDao()` is removed. The exported
  `5.json` schema must no longer contain either table.
- Existing migrations `MIGRATION_1_2`…`MIGRATION_3_4` stay unchanged
  (they still create the playlist tables for old installs; `4_5` drops
  them).

### Code removed
- `ui/playlists/` (all five files), `ui/common/AddToPlaylistDialog.kt`
- `data/repository/PlaylistRepository.kt`, `data/local/PlaylistDao.kt`,
  `data/local/PlaylistEntity.kt`, `data/local/PlaylistEpisodeCrossRef.kt`,
  `PlaylistEpisodeWithEpisode` in `data/local/EpisodeWithFeed.kt`
- `Screen.Playlists` and `Screen.PlaylistDetail` routes and their
  `BerpodApp` composables, icon and label mappings
- `AppContainer.playlistRepository`
- Playlist state and actions in `NewEpisodesViewModel` and
  `PodcastEpisodesViewModel` (and the `playlistRepository` factory
  parameter), the `AddToPlaylistDialog` usages in their screens
- The `onAddToPlaylist` parameter of `EpisodeListItem` and its button
- Strings used only by playlists: `nav_playlists`,
  `action_add_to_playlist`, `playlists_title`, `playlists_empty`,
  `playlist_detail_empty`, `fab_add_playlist`,
  `create_playlist_dialog_title`, `create_playlist_name_label`,
  `add_to_playlist_dialog_title`, `action_new_playlist_inline`,
  `action_remove_from_playlist`

## Part 2 — Tag filter on the Queue

### User-facing
- A horizontally scrollable chip row directly under the Queue top app
  bar, identical in look to the Podcasts one: "Všechny" first, then tags
  alphabetically; single-select; hidden when no tags exist.
- The list shows only queue entries whose podcast has the selected tag,
  in queue order.
- The Queue keeps its **own** selection, independent of the Podcasts
  screen. It resets to "Všechny" on app restart, and falls back to
  "Všechny" if the selected tag is deleted.
- Long-pressing a chip in the Queue does nothing (tag management lives
  on the Podcasts screen only).
- Empty queue (no entries at all): existing empty state unchanged.
  Entries exist but none match the filter: empty state with title
  "Ve frontě nejsou epizody s tímto štítkem".
- Move up / move down act on the **visible** list: an item swaps queue
  positions with its visible neighbour. Hidden entries keep their
  positions. The first visible item's "up" and last visible item's
  "down" do nothing.
- Remove and Play behave as today.

### Implementation
- `TagDao.observePodcastIdsWithTag(tagId: Long): Flow<List<Long>>` —
  `SELECT podcastId FROM podcast_tags WHERE tagId = :tagId`.
- `TagRepository.observePodcastIdsWithTag(tagId)` passes it through.
- Pure function in the queue package:
  `filterQueueByTag(items: List<QueueItemWithEpisode>, podcastIds: Set<Long>?): List<QueueItemWithEpisode>`
  — `null` returns `items` unchanged; otherwise keeps items whose
  `episode.feedId` is in the set.
- `QueueViewModel` gains `tagRepository`, `tags`, `selectedTagId` and
  `visibleItems` in `QueueUiState`. It combines the queue flow with
  `selectedTagId.flatMapLatest { id -> id?.let { observePodcastIdsWithTag(it) } ?: flowOf(null) }`
  and applies `filterQueueByTag`. The tags observer uses
  `resolveSelectedTagId` to fall back to "Všechny".
- `onMoveUp`/`onMoveDown` pass `visibleItems` (not the full queue) to the
  existing `QueueRepository.moveItem`, which already swaps the two given
  entries' stored positions — so moving within a filtered view needs no
  repository change.
- `TagChipRow` and `resolveSelectedTagId` move to `ui/common` so both
  screens share them. `TagChipRow`'s `onRenameRequest` / `onDeleteRequest`
  become nullable; when both are null the chips have no long-press
  action and no menu.

## Error handling

- Dropping tables uses `IF EXISTS`, so a partially migrated database
  can't fail the migration.
- A tag deleted while selected in the Queue falls back to "Všechny".

## Testing

- JVM unit tests:
  - `filterQueueByTag`: null filter returns all; a set keeps only
    matching feeds in original order; empty set returns empty.
  - `FakeTagDao.observePodcastIdsWithTag` + `TagRepository` pass-through.
  - Moving within a filtered list swaps the two visible entries'
    positions and leaves hidden entries untouched (test against
    `QueueRepository.moveItem` with a fake `QueueDao`).
- Build must succeed with no remaining references to playlist types.
- Manual on the emulator: install over the v4 database — queue entries,
  podcasts and tags survive; the Playlisty tab is gone; episode rows have
  no playlist button; the Queue chip row filters; move up/down in a
  filtered queue; deleting the selected tag on Podcasts resets the Queue
  filter.
