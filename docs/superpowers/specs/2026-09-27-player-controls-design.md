# Player controls (Dot Matrix step 2) — design doc

Date: 2026-09-27
Status: Approved in chat, awaiting written-spec review
Branch: `feature/podcast-tags`
Builds on: `2026-09-27-dot-matrix-redesign-design.md` (step 1)

## Purpose

Step 1 restyled the player with only play/pause and seeking. This adds the
controls the Dot Matrix player artboard shows and the queue behaviour behind
them: skip back 15 s / forward 30 s, previous / next through the queue,
auto-advance when an episode ends, playback speed, a sleep timer, and a
queue shortcut.

Out of scope: next/previous buttons in the system notification and lock
screen (they keep play/pause and seeking only), per-podcast speed,
remembering a running sleep timer across app restarts, the "⋯ more" button.

## Player layout (artboard 03)

- Transport row under the time labels, spread across the width:
  `[↺15]` 52 dp outlined (16 dp corners) · `[⏮]` 48 dp borderless ·
  `[▶/⏸]` 78 dp (existing) · `[⏭]` 48 dp borderless · `[30↻]` 52 dp outlined.
  Icons from artboard 04 (`back15`, `previous`, `next`, `forward30`; the
  15/30 numbers are drawn as text inside the icon).
- Option row below, three equal 42 dp buttons (12 dp corners), icon + label:
  - **RYCHLOST** shows the speed ("1,0×"); accent border and accent text
    when the speed isn't 1,0×, otherwise outline and textStrong.
  - **SPÁNEK** shows "SPÁNEK", or the remaining time ("23:41") / "KONEC
    EPIZODY" while a timer runs, then with accent border and text.
  - **FRONTA** closes the player and opens Podcasts › Fronta.
- **Next** is disabled (muted icon, not clickable) when nothing follows the
  current episode in the queue. **Previous** is always enabled.

## Queue behaviour

- **Playing an episode that isn't in the queue** (from Nové or a podcast
  detail) inserts it at the **first** position. Playing one that is already
  queued leaves the queue order alone. So the playing episode is normally
  the first entry.
- **Next** plays the queue entry directly after the current episode (or the
  first entry if the current episode isn't queued). The current episode
  stays in the queue where it is.
- **Previous**: the first press restarts the current episode from 0; a
  second press within 3 s of the first plays the entry directly before the
  current episode in the queue (restarts again if there is none).
- **Episode ended** (`STATE_ENDED`): its stored position is set to 0 (so it
  starts from the beginning next time), it is removed from the queue, and the
  entry that followed it starts automatically — unless the sleep timer is
  "end of episode", which instead stops and clears the timer. Nothing follows
  → playback just stops.
- "Following" is always computed from the queue order at that moment.

## Speed

- Cycle on tap: 0,8× → 1,0× → 1,2× → 1,5× → 2,0× → 0,8×.
- Applied with `setPlaybackSpeed`, kept for every episode, persisted in
  `SharedPreferences` (`playback`, key `speed`) and applied when the
  controller connects.

## Sleep timer

- Tap SPÁNEK opens a small menu: 15 min, 30 min, 45 min, 60 min, Do konce
  epizody, and — while a timer runs — Zrušit časovač.
- A timed timer counts down in `PlaybackRepository` (UI shows mm:ss). At zero
  it fades volume from 1 to 0 over 5 s, pauses, restores volume 1 and clears.
- Pausing manually does not stop the countdown (matches AntennaPod); cancel
  via the menu.
- Not persisted: an app/process restart clears it.

## Architecture

The player keeps a single media item; queue logic lives in
`PlaybackRepository`, driven by the database.

- `data/repository/PlaybackQueue.kt` (pure, tested):
  - `nextInQueue(order: List<Long>, currentEpisodeId: Long?): Long?`
  - `previousInQueue(order: List<Long>, currentEpisodeId: Long?): Long?`
  - `enum PreviousAction { Restart, PlayPrevious }`,
    `previousAction(lastPressAtMs: Long?, nowMs: Long, windowMs = 3000): PreviousAction`
  - `nextSpeed(current: Float): Float` over `SPEEDS = listOf(0.8f, 1f, 1.2f, 1.5f, 2f)`
  - `sealed interface SleepTimer { data class At(endsAtMs: Long); data object EndOfEpisode }`
  - `sleepRemainingMs(timer: SleepTimer.At, nowMs: Long): Long` (≥ 0)
  - `fadeVolume(elapsedMs: Long, fadeMs: Long = 5000): Float` (1 → 0, clamped)
  - `skipTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long` (clamped to 0..duration when known)
- `QueueDao.queueAtFront(episodeId: Long)` `@Transaction`: no-op if queued,
  else insert and move before the current first entry (reuses `moveEntry`).
  `QueueRepository.queueAtFront(episodeId)`.
- `PlaybackRepository` gains a `QueueDao` and `SharedPreferences`:
  - collects `observeQueue()` into `latestQueue`;
  - `play()` also calls `queueAtFront(episode.id)`;
  - `skipBy(deltaMs)`, `playNext()`, `playPrevious()`, `cycleSpeed()`,
    `setSleepTimer(minutes)`, `setSleepAtEndOfEpisode()`, `cancelSleepTimer()`;
  - `onPlaybackStateChanged(STATE_ENDED)` handles the ended rules above;
  - `PlaybackUiState` gains `speed: Float`, `sleepTimer: SleepTimer?`,
    `sleepRemainingMs: Long?`, `hasNext: Boolean`.
- `PlayerViewModel` forwards the new actions.
- `PlayerScreen(viewModel, onBack, onOpenQueue)`; the hub's selected section
  is hoisted into `BerpodApp` (`rememberSaveable`) so "FRONTA" can select
  Queue and pop back to the Podcasts tab.
- Strings: `action_back_15`, `action_forward_30`, `action_previous`,
  `action_next`, `speed`, `sleep`, `sleep_end_of_episode`, `sleep_cancel`,
  `sleep_minutes` ("%d min"), `queue`.

## Testing

- JVM: `nextInQueue` / `previousInQueue` (middle, ends, current not queued,
  empty); `previousAction` (first press, within 3 s, after 3 s);
  `nextSpeed` cycle and unknown value; `sleepRemainingMs` clamps; `fadeVolume`
  endpoints and midpoint; `skipTarget` clamps both ends and unknown duration;
  `QueueRepository.queueAtFront` with the fake DAO (not queued → first, already
  queued → unchanged, empty queue).
- Manual on the emulator: every button; auto-advance at the end (seek near
  the end); "Do konce epizody" stops instead of advancing; a 15-min timer
  counts down (verify the mechanism by temporarily reading the label, not by
  waiting); speed survives restart; FRONTA opens the queue.
