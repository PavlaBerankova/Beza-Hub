# Player Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add skip ±15/30 s, queue-driven previous/next with auto-advance, playback speed and a sleep timer to the Dot Matrix player.

**Architecture:** Pure decision helpers (`PlaybackQueue.kt`) with unit tests; a `queueAtFront` DAO transaction; `PlaybackRepository` observes the queue and applies the rules (single media item, app-level queue); the player screen gets the transport and option rows.

**Tech Stack:** Kotlin 2.4, Compose, Media3 MediaController, Room 2.8.5, SharedPreferences, JUnit 4 + coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-27-player-controls-design.md`

## Global Constraints

- Speeds exactly 0.8, 1.0, 1.2, 1.5, 2.0; persisted in `SharedPreferences("playback")` key `speed`.
- Sleep options 15/30/45/60 min and end of episode; 5 s volume fade; not persisted.
- Previous: restart, second press within 3000 ms → previous queue entry.
- Ended episode: position 0, removed from queue, auto-advance unless sleep = end of episode.
- Playing a non-queued episode puts it first in the queue.
- Czech strings in `strings.xml`; Dot Matrix components/colours only; touch targets ≥ 44 dp.
- Tests `./gradlew :app:testDebugUnitTest`, build `./gradlew :app:assembleDebug`, from the repo root.

## Review Focus

1. An episode ending while the queue is filtered or being reordered must still advance to the entry that follows it in the *full* queue — the repository uses the unfiltered `observeQueue()` order (Task 3).
2. Pressing Next on the last queue entry must not crash or loop — `hasNext` disables it; `playNext()` no-ops when `nextInQueue` is null (Task 1 test + Task 3).
3. Skipping forward near the end must not seek past the duration; skipping back near 0 must clamp — Task 1 `skipTarget` tests.
4. The sleep fade must always restore volume to 1 afterwards, also if the user resumes — Task 3 restores volume in `finally`.
5. Removing the ended episode from the queue must not trigger the undo snackbar or break a concurrent drag — it goes through `QueueDao.removeEntry` directly, not the view model (Task 3).

---

### Task 1: Pure playback decisions

**Files:** Create `app/src/main/java/com/berpod/app/data/repository/PlaybackQueue.kt`; Test `app/src/test/java/com/berpod/app/data/repository/PlaybackQueueTest.kt`

- [ ] **Step 1: Failing tests**

```kotlin
package com.berpod.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQueueTest {

    private val order = listOf(10L, 20L, 30L)

    @Test
    fun `next and previous in the queue`() {
        assertEquals(30L, nextInQueue(order, 20))
        assertEquals(10L, previousInQueue(order, 20))
        assertNull(nextInQueue(order, 30))
        assertNull(previousInQueue(order, 10))
    }

    @Test
    fun `current episode not queued uses the first entry for next`() {
        assertEquals(10L, nextInQueue(order, 99))
        assertNull(previousInQueue(order, 99))
        assertEquals(10L, nextInQueue(order, null))
        assertNull(nextInQueue(emptyList(), 5))
    }

    @Test
    fun `previous restarts unless pressed again within three seconds`() {
        assertEquals(PreviousAction.Restart, previousAction(lastPressAtMs = null, nowMs = 10_000))
        assertEquals(PreviousAction.PlayPrevious, previousAction(lastPressAtMs = 8_000, nowMs = 10_000))
        assertEquals(PreviousAction.Restart, previousAction(lastPressAtMs = 6_000, nowMs = 10_000))
    }

    @Test
    fun `speed cycles through the fixed steps`() {
        assertEquals(1.2f, nextSpeed(1.0f))
        assertEquals(0.8f, nextSpeed(2.0f))
        assertEquals(1.0f, nextSpeed(1.33f))
    }

    @Test
    fun `sleep remaining and fade`() {
        assertEquals(5_000L, sleepRemainingMs(SleepTimer.At(15_000), nowMs = 10_000))
        assertEquals(0L, sleepRemainingMs(SleepTimer.At(15_000), nowMs = 20_000))
        assertEquals(1f, fadeVolume(0), 0.001f)
        assertEquals(0.5f, fadeVolume(2_500), 0.001f)
        assertEquals(0f, fadeVolume(9_000), 0.001f)
    }

    @Test
    fun `skip target clamps to the episode`() {
        assertEquals(45_000L, skipTarget(positionMs = 15_000, deltaMs = 30_000, durationMs = 600_000))
        assertEquals(0L, skipTarget(positionMs = 10_000, deltaMs = -15_000, durationMs = 600_000))
        assertEquals(600_000L, skipTarget(positionMs = 590_000, deltaMs = 30_000, durationMs = 600_000))
        assertEquals(40_000L, skipTarget(positionMs = 10_000, deltaMs = 30_000, durationMs = 0))
    }
}
```

- [ ] **Step 2: Run — FAIL (unresolved).**

- [ ] **Step 3: Implement**

```kotlin
package com.berpod.app.data.repository

/** Queue entry (episode id) after [currentEpisodeId]; the first entry if the current one isn't queued. */
fun nextInQueue(order: List<Long>, currentEpisodeId: Long?): Long? {
    val index = order.indexOf(currentEpisodeId)
    return if (index < 0) order.firstOrNull { it != currentEpisodeId } else order.getOrNull(index + 1)
}

/** Queue entry (episode id) before [currentEpisodeId], or null. */
fun previousInQueue(order: List<Long>, currentEpisodeId: Long?): Long? {
    val index = order.indexOf(currentEpisodeId)
    return if (index <= 0) null else order[index - 1]
}

enum class PreviousAction { Restart, PlayPrevious }

/** First press restarts the episode; a second press within [windowMs] goes to the previous entry. */
fun previousAction(lastPressAtMs: Long?, nowMs: Long, windowMs: Long = 3_000): PreviousAction =
    if (lastPressAtMs != null && nowMs - lastPressAtMs in 0..windowMs) PreviousAction.PlayPrevious else PreviousAction.Restart

val SPEEDS = listOf(0.8f, 1.0f, 1.2f, 1.5f, 2.0f)

/** The speed after [current] in [SPEEDS]; an unknown value goes to 1.0. */
fun nextSpeed(current: Float): Float {
    val index = SPEEDS.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
    return if (index < 0) 1.0f else SPEEDS[(index + 1) % SPEEDS.size]
}

sealed interface SleepTimer {
    data class At(val endsAtMs: Long) : SleepTimer
    data object EndOfEpisode : SleepTimer
}

fun sleepRemainingMs(timer: SleepTimer.At, nowMs: Long): Long = (timer.endsAtMs - nowMs).coerceAtLeast(0L)

/** Volume during the fade-out: 1 at the start, 0 after [fadeMs]. */
fun fadeVolume(elapsedMs: Long, fadeMs: Long = 5_000): Float = (1f - elapsedMs.toFloat() / fadeMs).coerceIn(0f, 1f)

/** Position after skipping [deltaMs], kept within the episode (duration ≤ 0 = unknown, only 0 is enforced). */
fun skipTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
    val target = (positionMs + deltaMs).coerceAtLeast(0L)
    return if (durationMs > 0) target.coerceAtMost(durationMs) else target
}
```

- [ ] **Step 4: Run — PASS.** Commit `"Add pure playback decisions: queue next/previous, speed, sleep, skip"`.

---

### Task 2: Queue at front

**Files:** Modify `QueueDao.kt`, `QueueRepository.kt`; Test `QueueRepositoryTest.kt`

- [ ] **Step 1: Failing tests** (append to `QueueRepositoryTest`, using `queueOf`):

```kotlin
    @Test
    fun `queueAtFront puts a new episode first`() = runTest {
        val dao = queueOf(1, 2)
        QueueRepository(dao).queueAtFront(episodeId = 5005)
        assertEquals(listOf(5005L, 1001L, 1002L), dao.episodeOrder())
    }

    @Test
    fun `queueAtFront leaves an already queued episode where it is`() = runTest {
        val dao = queueOf(1, 2)
        QueueRepository(dao).queueAtFront(episodeId = 1002)
        assertEquals(listOf(1001L, 1002L), dao.episodeOrder())
    }

    @Test
    fun `queueAtFront on an empty queue`() = runTest {
        val dao = RecordingQueueDao(emptyMap())
        QueueRepository(dao).queueAtFront(episodeId = 7)
        assertEquals(listOf(7L), dao.episodeOrder())
    }
```

(If `RecordingQueueDao.insert` computes ids from `positions.keys.maxOrNull() ?: 0L` it already works for an empty map.)

- [ ] **Step 2: FAIL. Step 3: Implement** — `QueueDao`:

```kotlin
    /** Puts [episodeId] first in the queue unless it's already queued. */
    @Transaction
    suspend fun queueAtFront(episodeId: Long) {
        if (isQueued(episodeId)) return
        val firstId = getOrderedIds().firstOrNull()
        val newId = insert(QueueEntryEntity(episodeId = episodeId, position = getMaxPosition() + 1, dateAdded = System.currentTimeMillis()))
        if (firstId != null) moveEntry(newId, afterId = null, beforeId = firstId)
    }
```

`QueueRepository`: `suspend fun queueAtFront(episodeId: Long) = queueDao.queueAtFront(episodeId)`.

- [ ] **Step 4: PASS.** Commit `"Queue an episode at the front when it is played from outside the queue"`.

---

### Task 3: PlaybackRepository — queue, skip, speed, sleep

**Files:** Modify `PlaybackRepository.kt`, `di/AppContainer.kt`, `ui/player/PlayerViewModel.kt`

- [ ] **Step 1:** Constructor `PlaybackRepository(context, episodeDao, queueDao: QueueDao)`; `AppContainer` passes `database.queueDao()`. Add:

```kotlin
    private val prefs = appContext.getSharedPreferences("playback", Context.MODE_PRIVATE)
    private var latestQueue: List<QueueItemWithEpisode> = emptyList()
    private var lastPreviousPressAtMs: Long? = null
    private var sleepJob: Job? = null
```

`PlaybackUiState` gains `val speed: Float = 1f, val sleepTimer: SleepTimer? = null, val sleepRemainingMs: Long? = null, val hasNext: Boolean = false`.

In `init` (before building the controller):

```kotlin
        _uiState.update { it.copy(speed = prefs.getFloat("speed", 1f)) }
        queueDao.observeQueue()
            .onEach { queue ->
                latestQueue = queue
                updateHasNext()
            }
            .launchIn(scope)
```

and in the controller-connected callback add `connectedController.setPlaybackSpeed(_uiState.value.speed)`.

Helper: `private fun updateHasNext() { _uiState.update { it.copy(hasNext = nextInQueue(latestQueue.map { q -> q.episode.id }, it.currentEpisodeId) != null) } }`; call it also at the end of `onMediaItemTransition`.

- [ ] **Step 2: play() queues at front**: at the start of `play()` add `scope.launch { runCatching { queueDao.queueAtFront(episode.id) } }`.

- [ ] **Step 3: Actions**

```kotlin
    fun skipBy(deltaMs: Long) {
        val c = controller ?: return
        seekTo(skipTarget(c.currentPosition, deltaMs, _uiState.value.durationMs))
    }

    fun playNext() {
        val nextId = nextInQueue(latestQueue.map { it.episode.id }, _uiState.value.currentEpisodeId) ?: return
        latestQueue.firstOrNull { it.episode.id == nextId }?.let { play(it.episode, it.podcastTitle, it.podcastImageUrl) }
    }

    fun playPrevious() {
        val now = System.currentTimeMillis()
        val action = previousAction(lastPreviousPressAtMs, now)
        lastPreviousPressAtMs = now
        val previousId = previousInQueue(latestQueue.map { it.episode.id }, _uiState.value.currentEpisodeId)
        val previous = previousId?.let { id -> latestQueue.firstOrNull { it.episode.id == id } }
        if (action == PreviousAction.PlayPrevious && previous != null) {
            lastPreviousPressAtMs = null
            play(previous.episode.copy(lastPositionMs = 0L), previous.podcastTitle, previous.podcastImageUrl)
        } else {
            seekTo(0L)
        }
    }

    fun cycleSpeed() {
        val speed = nextSpeed(_uiState.value.speed)
        prefs.edit().putFloat("speed", speed).apply()
        controller?.setPlaybackSpeed(speed)
        _uiState.update { it.copy(speed = speed) }
    }

    fun setSleepTimer(minutes: Int) {
        val timer = SleepTimer.At(System.currentTimeMillis() + minutes * 60_000L)
        sleepJob?.cancel()
        _uiState.update { it.copy(sleepTimer = timer) }
        sleepJob = scope.launch {
            while (isActive) {
                val remaining = sleepRemainingMs(timer, System.currentTimeMillis())
                _uiState.update { it.copy(sleepRemainingMs = remaining) }
                if (remaining == 0L) break
                delay(1_000)
            }
            fadeOutAndPause()
            clearSleepTimer()
        }
    }

    fun setSleepAtEndOfEpisode() {
        sleepJob?.cancel()
        _uiState.update { it.copy(sleepTimer = SleepTimer.EndOfEpisode, sleepRemainingMs = null) }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        clearSleepTimer()
    }

    private fun clearSleepTimer() {
        _uiState.update { it.copy(sleepTimer = null, sleepRemainingMs = null) }
    }

    private suspend fun fadeOutAndPause() {
        val c = controller ?: return
        val start = System.currentTimeMillis()
        try {
            while (true) {
                val elapsed = System.currentTimeMillis() - start
                c.volume = fadeVolume(elapsed)
                if (elapsed >= 5_000) break
                delay(100)
            }
            c.pause()
        } finally {
            c.volume = 1f
        }
    }
```

(Imports: `onEach`, `launchIn`, `QueueDao`, `QueueItemWithEpisode`, `Context`.)

- [ ] **Step 4: Ended episode** — in `onPlaybackStateChanged` add:

```kotlin
            if (playbackState == Player.STATE_ENDED) onEpisodeEnded()
```

and

```kotlin
    private fun onEpisodeEnded() {
        val endedId = _uiState.value.currentEpisodeId ?: return
        val order = latestQueue.map { it.episode.id }
        val next = nextInQueue(order, endedId)?.let { id -> latestQueue.firstOrNull { it.episode.id == id } }
        val entry = latestQueue.firstOrNull { it.episode.id == endedId }
        scope.launch {
            runCatching { episodeDao.updatePosition(endedId, 0L) }
            if (entry != null) runCatching { queueDao.removeEntry(entry.queueEntryId) }
        }
        if (_uiState.value.sleepTimer == SleepTimer.EndOfEpisode) {
            clearSleepTimer()
            return
        }
        if (next != null) play(next.episode, next.podcastTitle, next.podcastImageUrl)
    }
```

Note: `onIsPlayingChanged(false)` fires at the end and persists the end position; `onEpisodeEnded` then overwrites it with 0 — order matters: STATE_ENDED is delivered after isPlaying=false in Media3, so 0 wins. Verify manually.

- [ ] **Step 5: ViewModel** — `PlayerViewModel` adds `skipBack() = playbackRepository.skipBy(-15_000)`, `skipForward() = playbackRepository.skipBy(30_000)`, `playNext()`, `playPrevious()`, `cycleSpeed()`, `setSleepTimer(minutes)`, `setSleepAtEndOfEpisode()`, `cancelSleepTimer()` (one-line forwards).

- [ ] **Step 6:** Tests + build → PASS. Commit `"Drive previous/next, auto-advance, skip, speed and sleep timer from the playback repository"`.

---

### Task 4: Player UI and queue shortcut

**Files:** Modify `ui/dot/DotIcons.kt`, `ui/player/PlayerScreen.kt`, `ui/podcasts/PodcastsHubScreen.kt`, `ui/navigation/BerpodApp.kt`, `res/values/strings.xml`

- [ ] **Step 1: Icons** (from artboard 04; 24-unit grid):

```kotlin
    val Previous = DotIconSpec(24f, 24f, listOf(IconPart("M6 5v14", IconRole.Muted, 2.4f), IconPart("M19 5.5L10 12l9 6.5z", IconRole.Primary, 2.4f), px(14.6f, 11f)))
    val Next = DotIconSpec(24f, 24f, listOf(IconPart("M18 5v14", IconRole.Muted, 2.4f), IconPart("M5 5.5L14 12l-9 6.5z", IconRole.Primary, 2.4f), px(7.4f, 11f)))
    val Back15 = DotIconSpec(24f, 24f, listOf(IconPart("M4.5 12a7.5 7.5 0 1 0 2.2-5.3", IconRole.Primary, 2.2f), IconPart("M6.7 3v3.7H3", IconRole.Muted, 2.2f), px(18.3f, 10.8f, 2.4f)))
    val Forward30 = DotIconSpec(24f, 24f, listOf(IconPart("M19.5 12a7.5 7.5 0 1 1-2.2-5.3", IconRole.Primary, 2.2f), IconPart("M17.3 3v3.7H21", IconRole.Muted, 2.2f), px(3.3f, 10.8f, 2.4f)))
    val Speed = DotIconSpec(24f, 24f, listOf(IconPart("M4 17a8 8 0 1 1 16 0", IconRole.Primary, 2.4f), IconPart("M12 7v1.5M6.3 9.3l1 1M17.7 9.3l-1 1", IconRole.Muted, 2.4f), IconPart("M12 17l3.5-4.5", IconRole.Primary, 2.4f), px(11f, 16f)))
    val Sleep = DotIconSpec(24f, 24f, listOf(IconPart("M19 14.5A7.5 7.5 0 1 1 9.5 5a6 6 0 0 0 9.5 9.5z", IconRole.Primary, 2.4f), px(16f, 3.5f), px(19.5f, 7f, 1.8f, IconRole.Muted)))
```

- [ ] **Step 2: Strings** — add `action_back_15` "Zpět o 15 sekund", `action_forward_30` "Vpřed o 30 sekund", `action_previous` "Předchozí", `action_next` "Další", `speed` "Rychlost", `sleep` "Spánek", `sleep_end_of_episode` "Do konce epizody", `sleep_cancel` "Zrušit časovač", `sleep_minutes` "%1$d min", `queue` "Fronta".

- [ ] **Step 3: Player** — `PlayerScreen(viewModel, onBack, onOpenQueue: () -> Unit)`. Replace the single play button with:

```kotlin
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkipButton(DotIcons.Back15, "15", stringResource(R.string.action_back_15), viewModel::skipBack)
            TransportButton(DotIcons.Previous, stringResource(R.string.action_previous), enabled = true, onClick = viewModel::playPrevious)
            DotIconButton(
                if (state.isPlaying) DotIcons.PauseLarge else DotIcons.PlayLarge,
                stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                viewModel::togglePlayPause,
                size = 78.dp, radius = 24.dp, iconSize = 36.dp, accentBorder = true, filled = c.surface
            )
            TransportButton(DotIcons.Next, stringResource(R.string.action_next), enabled = state.hasNext, onClick = viewModel::playNext)
            SkipButton(DotIcons.Forward30, "30", stringResource(R.string.action_forward_30), viewModel::skipForward)
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionButton(
                icon = DotIcons.Speed,
                label = formatSpeed(state.speed),
                active = abs(state.speed - 1f) > 0.01f,
                onClick = viewModel::cycleSpeed,
                modifier = Modifier.weight(1f)
            )
            Box(Modifier.weight(1f)) {
                var menuOpen by remember { mutableStateOf(false) }
                OptionButton(
                    icon = DotIcons.Sleep,
                    label = when (val t = state.sleepTimer) {
                        null -> stringResource(R.string.sleep)
                        SleepTimer.EndOfEpisode -> stringResource(R.string.sleep_end_of_episode)
                        is SleepTimer.At -> formatPlaybackTime(state.sleepRemainingMs ?: 0L)
                    },
                    active = state.sleepTimer != null,
                    onClick = { menuOpen = true },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    listOf(15, 30, 45, 60).forEach { min ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.sleep_minutes, min)) }, onClick = { menuOpen = false; viewModel.setSleepTimer(min) })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.sleep_end_of_episode)) }, onClick = { menuOpen = false; viewModel.setSleepAtEndOfEpisode() })
                    if (state.sleepTimer != null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.sleep_cancel)) }, onClick = { menuOpen = false; viewModel.cancelSleepTimer() })
                    }
                }
            }
            OptionButton(DotIcons.Queue, stringResource(R.string.queue), active = false, onClick = onOpenQueue, modifier = Modifier.weight(1f))
        }
```

with private composables in the same file:

```kotlin
@Composable
private fun SkipButton(icon: DotIconSpec, seconds: String, description: String, onClick: () -> Unit) {
    DotIconButton(icon, description, onClick, size = 52.dp, radius = 16.dp) {
        Box(contentAlignment = Alignment.Center) {
            DotIcon(icon, 30.dp)
            Text(seconds, style = DotType.chip.copy(fontSize = 9.sp, fontWeight = FontWeight.W700), color = DotTheme.colors.text)
        }
    }
}

@Composable
private fun TransportButton(icon: DotIconSpec, description: String, enabled: Boolean, onClick: () -> Unit) {
    val c = DotTheme.colors
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        DotIcon(icon, 28.dp, monochrome = if (enabled) null else c.divider)
    }
}

@Composable
private fun OptionButton(icon: DotIconSpec, label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = DotTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .border(1.dp, if (active) c.accent else c.outline, shape)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DotIcon(icon, 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            label.upperCs(),
            style = DotType.segment.copy(fontWeight = if (active) FontWeight.W700 else FontWeight.W600, letterSpacing = 1.sp),
            color = if (active) c.accent else c.textStrong,
            maxLines = 1
        )
    }
}

private fun formatSpeed(speed: Float): String = String.format(java.util.Locale.forLanguageTag("cs"), "%.1f×", speed)
```

- [ ] **Step 4: Queue shortcut** — hoist the hub section: `PodcastsHubScreen(..., section: PodcastsSection, onSectionChange: (PodcastsSection) -> Unit, …)` (replace its internal `rememberSaveable`). In `BerpodApp`: `var podcastsSection by rememberSaveable { mutableStateOf(PodcastsSection.Subscriptions) }`, pass it down, and `PlayerScreen(..., onOpenQueue = { podcastsSection = PodcastsSection.Queue; navController.popBackStack(Screen.Podcasts.route, inclusive = false) })`. If the Podcasts route isn't on the back stack (player opened from YouTube tab), `popBackStack` returns false → then `navController.navigate(Screen.Podcasts.route) { popUpTo(navController.graph.startDestinationId); launchSingleTop = true }`.

- [ ] **Step 5:** Tests + build; emulator: each button, speed cycling and label, sleep menu + countdown label, cancel, end-of-episode stop, FRONTA → queue, auto-advance (seek near the end with the wave), next disabled at the last entry. Commit `"Add skip, previous/next, speed, sleep and queue controls to the player"`; install on phone if connected.
