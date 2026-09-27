# Queue drag-and-drop reorder and artwork rows — design doc

Date: 2026-09-27
Status: Approved in chat, awaiting written-spec review
Builds on: `2026-09-27-queue-tags-remove-playlists-design.md` (branch `feature/podcast-tags`)

## Purpose

The Queue and New Episodes lists mix episodes from different podcasts.
Instead of a text line with the podcast name, each row shows a small
square podcast artwork on the left (like AntennaPod). In the Queue, the
up/down arrows are replaced by drag-and-drop reordering via a drag handle
on the far left.

Out of scope: swipe-to-remove, artwork on the Podcast Episodes screen
(all its episodes share one podcast), reordering in New Episodes.

## Row layout

### Queue
`[⠿ handle] [artwork 56dp] [date · duration / episode title (max 2 lines) / progress bar] [play] [✕]`

- The podcast-name text line is no longer shown.
- The up/down arrow buttons are removed.
- Play and remove (✕) stay on the right, 48dp touch targets.

### New Episodes
`[artwork 56dp] [date · duration / episode title / progress bar / download + add-to-queue buttons] [play]`

- The podcast-name text line is no longer shown; everything else stays.

### Podcast Episodes
Unchanged (no artwork, no podcast name).

### Artwork
- 56dp square, 8dp rounded corners, `ContentScale.Crop`, loaded with Coil
  from the row's `podcastImageUrl`.
- Missing or failed image: `surfaceVariant` square with a centred
  `Icons.Filled.Podcasts` icon, so a row is never blank.
- Decorative (`contentDescription = null`); the title carries the meaning.

## Drag and drop

### Library
`sh.calvin.reorderable:reorderable` 3.1.0 (Maven Central) — reorderable
`LazyColumn` items with a drag-handle modifier, item animation and
edge auto-scroll. Chosen over a hand-written pointer implementation
because gestures, auto-scroll and animation are easy to get subtly wrong.

### Behaviour
- Holding the ⠿ handle and moving starts the drag immediately (library
  `draggableHandle`, no extra long-press delay). The handle is the only
  drag start; the rest of the row still plays / removes as today. The dragged row is elevated; other rows animate out
  of the way; a haptic tick fires on drag start.
- While dragging, the screen reorders a local copy of the visible list;
  nothing is written to the database until the drag ends.
- On drop, if the item's position changed, one write is made:
  `QueueRepository.moveEntry(movedId, afterId, beforeId)`, where
  `afterId` is the visible entry directly above the dropped item and
  `beforeId` the visible entry directly below (either may be null).
- The local copy is replaced by the database emission afterwards, so the
  list never shows an order that wasn't stored.

### Filtered queue
- Only visible entries can be dragged and targeted.
- The moved entry is placed directly after `afterId` in the full queue;
  if it was dropped at the very top of the visible list (`afterId` null),
  it is placed directly before `beforeId`.
- Hidden entries keep their relative order.

### Ordering logic (race-safe)
Pure function in `data/local/QueueOrder.kt`:

```
fun reorderQueue(currentOrder: List<Long>, movedId: Long, afterId: Long?, beforeId: Long?): List<Long>
```

- Removes `movedId`, then inserts it right after `afterId` if that id is
  present; otherwise right before `beforeId` if present; otherwise returns
  `currentOrder` unchanged. Returns `currentOrder` unchanged if `movedId`
  isn't in it.

`QueueDao` gains:
- `getOrderedIds(): List<Long>` — `SELECT id FROM queue_entries ORDER BY position`
- `@Transaction moveEntry(movedId, afterId, beforeId)` — reads
  `getOrderedIds()`, applies `reorderQueue`, writes `position = index`
  for every entry. Positions are always 0..n-1 and unique afterwards.

Because the new order is computed inside the transaction from the stored
order, a move issued from a stale UI list cannot produce duplicate
positions.

`QueueRepository.moveItem` and `QueueDao.swapEntries` are removed; the
existing swap tests are replaced by `reorderQueue` / `moveEntry` tests.

### Accessibility
The arrows' function moves to custom accessibility actions on each Queue
row: "Posunout nahoru" (`moveEntry(id, afterId = null, beforeId = visible[i-1])`)
and "Posunout dolů" (`moveEntry(id, afterId = visible[i+1], beforeId = null)`),
offered only when that neighbour exists. The handle has
`contentDescription` "Přesunout".

## Components touched

- `gradle/libs.versions.toml`, `app/build.gradle.kts`: reorderable dependency.
- `ui/common/EpisodeListItem.kt`: `showArtwork: Boolean = false` (uses the
  existing `podcastImageUrl` parameter) and
  `leadingContent: @Composable (() -> Unit)? = null` (drag handle slot).
- `ui/common/PodcastArtwork.kt`: new 56dp artwork composable with fallback.
- `ui/queue/QueueScreen.kt`: reorderable list, handle, no arrows, no
  podcast title, accessibility actions.
- `ui/queue/QueueViewModel.kt`: `onMoveEntry(movedId, afterId, beforeId)`
  replaces `onMoveUp` / `onMoveDown`.
- `ui/newepisodes/NewEpisodesScreen.kt`: `showArtwork = true`,
  `podcastTitle = null`.
- `data/local/QueueOrder.kt` (new), `data/local/QueueDao.kt`,
  `data/repository/QueueRepository.kt`.
- `strings.xml`: `action_drag_to_reorder` = "Přesunout".

## Error handling

- Unknown ids (entry removed while dragging) → `reorderQueue` returns the
  order unchanged; nothing breaks.
- A drop at the same place → no write.

## Testing

- JVM unit tests for `reorderQueue`: move down / up without filter;
  filtered move keeps hidden entries' relative order; drop at the top of
  the visible list uses `beforeId`; unknown moved id and unknown anchors
  return the order unchanged.
- `QueueRepository.moveEntry` via a fake DAO that inherits the real
  transaction default method: positions end up 0..n-1 and unique, also
  when called twice with stale anchors.
- Manual on the emulator: rows in Queue and New Episodes show artwork and
  no podcast name; dragging reorders with animation and persists across
  restart; dragging in a filtered queue keeps hidden entries in place;
  play/remove still work; no arrows remain.
