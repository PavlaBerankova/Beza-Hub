# Berpod visual redesign — design doc

Date: 2026-09-21
Status: Approved, ready for implementation plan

## Purpose

Berpod's UI is still on Compose's default scaffold theme (`Purple80`/`Purple40`
demo colors, one custom text style, no shape/spacing tokens) and its episode
rows carry artwork and a `⋮` overflow menu. This redesign gives the app a
consistent, intentional visual identity — a minimalist, light-only, purple
accent style — and reworks the shared episode row to match, including a
playback-progress indicator that persists across app restarts.

Scope, agreed with the user via the visual-companion brainstorming flow:
1. Overall visual style (color palette; typography untouched)
2. Episode list item redesign
3. Bottom navigation + mini-player visual polish

Explicitly out of scope: dark mode (kept structurally possible, not
implemented), navigation structure/tab count (stays 4 tabs), episode
downloading (the new "download" button is visual-only, no-op — the app has
no download feature to wire it to).

## Visual direction (validated via mockups)

**Palette (P3):** white background/surface, accent `#5B2D8E` (dark purple),
primary text `#161616` (near-black), secondary text `#767676`. Applied to
Material 3's light color scheme only; `dynamicColor` is turned off in
`BerpodTheme` so Android 12+ Material You wallpaper colors don't override the
brand purple.

**Episode row (B1 + progress):** no artwork. Top line is
`<pub date> · <duration>` (duration now formatted as "1 h 7 min" / "34 min"
instead of the current "1:07:00" colon style). Below that, the episode title
(bold, up to 2 lines). Below the title, a thin purple progress bar — shown
only when the episode has a saved playback position greater than zero.
Below the progress bar (or directly below the title when there's no
progress), two small icon buttons: **Download** (visual-only, no-op) and
**Add to queue**. The play/pause button stays where it is today: a circular
purple-outline button on the far right of the row, vertically centered.

**Navigation:** bottom nav keeps its 4 tabs (Podcasts, Queue, New Episodes,
Playlists). The active tab's icon gets a soft purple pill background
(`#F1E9F8`); the label stays neutral gray even when active (only today's
full-color-on-active goes away). `MiniPlayerBar`, docked above the nav bar,
gets a very light purple-tinted background (`#FBF9FC`) instead of
`tonalElevation`, so it visually reads as part of the same navigation cluster
sitting above the tab bar.

## Data model change: persisted playback position

Today `PlaybackRepository` only tracks the position of whatever episode is
currently loaded into the `MediaController`, in memory — nothing is written
back once you navigate away or close the app. Showing a progress bar for
*any previously-played* episode (not just the one live in the player)
requires persisting position per episode.

**Schema:** add `lastPositionMs: Long = 0L` to `EpisodeEntity`. New Room
migration `MIGRATION_2_3` (bumps `AppDatabase.version` to 3):
```sql
ALTER TABLE episodes ADD COLUMN lastPositionMs INTEGER NOT NULL DEFAULT 0
```

**DAO:** add to `EpisodeDao`:
```kotlin
@Query("UPDATE episodes SET lastPositionMs = :positionMs WHERE id = :episodeId")
suspend fun updatePosition(episodeId: Long, positionMs: Long)
```

**Write path:** `PlaybackRepository` already polls `controller.currentPosition`
every 500ms into `PlaybackUiState` while playing (`restartPositionPolling`).
Writing to Room on every 500ms tick is unnecessary churn — instead,
`PlaybackRepository` persists the position on a coarser cadence (e.g. every
~5s of playback) and, importantly, on every `onIsPlayingChanged(false)`
(covers pause, and — combined with the coarse timer's last-written value
being close enough — app backgrounding) and on `onMediaItemTransition`
(saves the position of the episode being switched away from). This needs a
reference to `EpisodeDao` (or a small wrapper) injected into
`PlaybackRepository`; `DefaultAppContainer` already constructs it with
`appContext`, so it gains a `database.episodeDao()` dependency the same way
`EpisodeRepository` does today.

**Read path:** each screen's ViewModel that renders `EpisodeListItem`
(`QueueViewModel`, `NewEpisodesViewModel`, `PodcastEpisodesViewModel`,
`PlaylistDetailViewModel`) already maps `EpisodeEntity` → UI state; each adds
a `progressFraction: Float?` computed as
`(lastPositionMs.toFloat() / (durationSeconds * 1000).toFloat()).coerceIn(0f, 1f)`,
`null` when `lastPositionMs <= 0` or `durationSeconds` is null/zero (no bar
shown in either case).

## Components touched

- `ui/theme/Color.kt`, `ui/theme/Theme.kt` — new palette, `dynamicColor` off
- `ui/common/EpisodeFormatting.kt` — `formatDuration` output changes to
  "1 h 7 min" / "34 min" style (only caller is `EpisodeListItem`, confirmed
  via search — safe to change in place rather than adding a parallel
  function). `formatPlaybackTime` (used by `PlayerScreen`'s scrubber) is
  unrelated and unchanged — it stays colon-style, which is the correct
  convention for a seek position readout.
- `ui/common/EpisodeListItem.kt` — full layout rewrite per the B1+progress
  spec above; new params `onDownload: (() -> Unit)? = null` and
  `progressFraction: Float? = null`; drop the artwork `AsyncImage` and the
  `⋮` dropdown menu (replaced by the two inline icon buttons)
- `data/local/EpisodeEntity.kt`, `EpisodeDao.kt`, `Migrations.kt`,
  `AppDatabase.kt` — schema change described above
- `data/repository/PlaybackRepository.kt` — periodic + event-driven position
  persistence
- `di/AppContainer.kt` — thread `episodeDao` into `PlaybackRepository`
  construction
- `ui/queue/QueueViewModel.kt`, `ui/newepisodes/NewEpisodesViewModel.kt`,
  `ui/podcastlist/PodcastEpisodesViewModel.kt`,
  `ui/playlists/PlaylistDetailViewModel.kt` — compute and expose
  `progressFraction` per item; pass `onDownload = {}` (no-op) through to
  `EpisodeListItem`
- `ui/navigation/BerpodApp.kt` — bottom nav active-state pill styling,
  `MiniPlayerBar` background tint
- `ui/player/MiniPlayerBar.kt` — background color per the navigation mockup

## Error handling

No new failure modes: the migration is an additive column with a default
(no data loss, no nullable-handling needed downstream), and position writes
are fire-and-forget `suspend` calls on the existing coroutine scope — a
failed write just means a stale progress bar next launch, not a crash.

## Testing

- `./gradlew :app:compileDebugKotlin` after each major step
- Manual pass on device/emulator: play an episode partway, pause, navigate
  away, force-stop the app, relaunch, confirm the progress bar shows the
  right fraction in Queue/New Episodes/Podcast Episodes/Playlist Detail
- Confirm switching between episodes mid-playback saves the outgoing
  episode's position (via `onMediaItemTransition`)
- Visual pass across all 4 list screens + bottom nav + mini-player against
  the approved mockups
