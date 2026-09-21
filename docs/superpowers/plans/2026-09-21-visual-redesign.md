# Berpod Visual Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Berpod a consistent purple-accent, minimalist visual identity: new color palette, a redesigned episode row (no artwork, persisted playback progress bar, inline download/queue/playlist actions), and a polished bottom navigation + mini-player.

**Architecture:** Pure formatting/progress-calculation logic lives in small top-level functions so it can be unit-tested without Android framework dependencies (this project has no Robolectric/instrumented test setup — only plain JUnit). Data changes (persisted playback position) flow through a new Room column and DAO method; `PlaybackRepository` writes to it on a coarse timer plus on pause/track-switch events. UI changes are compile-verified and manually verified against the approved mockups (no Compose UI test infra exists in this project).

**Tech Stack:** Kotlin, Jetpack Compose (Material 3, `composeBom = 2026.09.00`), Room (KSP), Media3 ExoPlayer/Session, JUnit4 + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-21-visual-redesign-design.md`

## Global Constraints

- Light-only color scheme for now; no dark theme work in this plan.
- `dynamicColor` (Material You) must be off — the app always uses the fixed purple palette, never the wallpaper-derived one.
- Accent color `#5B2D8E`, background/surface `#FFFFFF`, primary text `#161616`, secondary text `#767676` (palette "P3" from the spec) — use these exact hex values.
- Bottom nav tab count and routes are unchanged (`Podcasts`, `Queue`, `NewEpisodes`, `Playlists`) — this plan only restyles them.
- The new "download" action is visual-only (a no-op callback) — the app has no download feature; do not build one.
- `EpisodeListItem`'s three inline action icons (download, add-to-queue, add-to-playlist) render only on screens that already wire at least one of them today (New Episodes, Podcast Episodes). Queue and Playlist Detail keep their existing `trailingContent` (reorder/remove) untouched and do not gain these three icons.
- Podcast title renders above the meta line only when the caller passes a non-null `podcastTitle` (Queue, New Episodes, Playlist Detail); Podcast Episodes keeps passing `null`.
- Run `./gradlew :app:compileDebugKotlin` after every task; it must succeed before moving on.

---

### Task 1: Persist playback position — schema

**Files:**
- Modify: `app/src/main/java/com/berpod/app/data/local/EpisodeEntity.kt`
- Modify: `app/src/main/java/com/berpod/app/data/local/EpisodeDao.kt`
- Modify: `app/src/main/java/com/berpod/app/data/local/Migrations.kt`
- Modify: `app/src/main/java/com/berpod/app/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/berpod/app/di/AppContainer.kt:26-30`

**Interfaces:**
- Produces: `EpisodeEntity.lastPositionMs: Long` (default `0L`); `EpisodeDao.updatePosition(episodeId: Long, positionMs: Long): Unit` (suspend); `MIGRATION_2_3: Migration`

This task has no meaningful unit test (Room migrations need an instrumented environment this project doesn't have, and adding one is out of scope for a visual redesign) — verify it by compiling and by a manual app launch, per Step 5.

- [ ] **Step 1: Add the new column to `EpisodeEntity`**

In `app/src/main/java/com/berpod/app/data/local/EpisodeEntity.kt`, add the field so the full data class reads:

```kotlin
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    val guid: String,
    val title: String,
    val audioUrl: String,
    val pubDateMillis: Long?,
    val durationSeconds: Int?,
    val description: String?,
    val imageUrl: String?,
    val dateFetched: Long,
    val lastPositionMs: Long = 0L
)
```

- [ ] **Step 2: Add the update query to `EpisodeDao`**

In `app/src/main/java/com/berpod/app/data/local/EpisodeDao.kt`, add inside the `interface EpisodeDao`:

```kotlin
@Query("UPDATE episodes SET lastPositionMs = :positionMs WHERE id = :episodeId")
suspend fun updatePosition(episodeId: Long, positionMs: Long)
```

- [ ] **Step 3: Add `MIGRATION_2_3` and bump the database version**

In `app/src/main/java/com/berpod/app/data/local/Migrations.kt`, add below `MIGRATION_1_2`:

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN lastPositionMs INTEGER NOT NULL DEFAULT 0")
    }
}
```

In `app/src/main/java/com/berpod/app/data/local/AppDatabase.kt`, change `version = 2` to `version = 3`.

- [ ] **Step 4: Register the migration in `AppContainer`**

In `app/src/main/java/com/berpod/app/di/AppContainer.kt`, change:

```kotlin
Room.databaseBuilder(appContext, AppDatabase::class.java, "berpod.db")
    .addMigrations(MIGRATION_1_2)
    .build()
```

to:

```kotlin
Room.databaseBuilder(appContext, AppDatabase::class.java, "berpod.db")
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
    .build()
```

Add `import com.berpod.app.data.local.MIGRATION_2_3` next to the existing `MIGRATION_1_2` import.

- [ ] **Step 5: Verify it compiles and the app still launches**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL, no errors.

Run: `./gradlew installDebug` and launch the app on a device/emulator that already has the app installed from before this change (so the migration path actually runs). Confirm it opens to the Podcasts screen with no crash. Check `adb logcat` for any Room `IllegalStateException` if anything looks wrong.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/berpod/app/data/local/EpisodeEntity.kt app/src/main/java/com/berpod/app/data/local/EpisodeDao.kt app/src/main/java/com/berpod/app/data/local/Migrations.kt app/src/main/java/com/berpod/app/data/local/AppDatabase.kt app/src/main/java/com/berpod/app/di/AppContainer.kt
git commit -m "Add persisted playback position column to episodes table"
```

---

### Task 2: Pure formatting and progress helpers (TDD)

**Files:**
- Modify: `app/src/main/java/com/berpod/app/ui/common/EpisodeFormatting.kt`
- Create: `app/src/test/java/com/berpod/app/ui/common/EpisodeFormattingTest.kt`

**Interfaces:**
- Produces: `formatDuration(seconds: Int?): String?` (changed output format — same signature, callers unaffected); `episodeProgressFraction(lastPositionMs: Long, durationSeconds: Int?): Float?`
- Consumes: nothing new

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/berpod/app/ui/common/EpisodeFormattingTest.kt`:

```kotlin
package com.berpod.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeFormattingTest {

    @Test
    fun `formatDuration returns null for null or non-positive input`() {
        assertNull(formatDuration(null))
        assertNull(formatDuration(0))
        assertNull(formatDuration(-5))
    }

    @Test
    fun `formatDuration renders sub-minute durations`() {
        assertEquals("<1 min", formatDuration(30))
    }

    @Test
    fun `formatDuration renders minutes only`() {
        assertEquals("1 min", formatDuration(60))
        assertEquals("42 min", formatDuration(2520))
        assertEquals("58 min", formatDuration(3480))
    }

    @Test
    fun `formatDuration renders hours and minutes`() {
        assertEquals("1 h 0 min", formatDuration(3600))
        assertEquals("1 h 7 min", formatDuration(4020))
    }

    @Test
    fun `episodeProgressFraction is null when nothing has been played`() {
        assertNull(episodeProgressFraction(lastPositionMs = 0L, durationSeconds = 100))
    }

    @Test
    fun `episodeProgressFraction is null when duration is unknown or zero`() {
        assertNull(episodeProgressFraction(lastPositionMs = 5_000L, durationSeconds = null))
        assertNull(episodeProgressFraction(lastPositionMs = 5_000L, durationSeconds = 0))
    }

    @Test
    fun `episodeProgressFraction computes the played fraction`() {
        assertEquals(0.5f, episodeProgressFraction(lastPositionMs = 30_000L, durationSeconds = 60)!!, 0.001f)
    }

    @Test
    fun `episodeProgressFraction is coerced to at most 1`() {
        assertEquals(1f, episodeProgressFraction(lastPositionMs = 999_999L, durationSeconds = 60)!!, 0.001f)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.berpod.app.ui.common.EpisodeFormattingTest"`
Expected: FAIL — compile error, since `episodeProgressFraction` doesn't exist yet and `formatDuration`'s current output doesn't match the new assertions.

- [ ] **Step 3: Implement the changes**

In `app/src/main/java/com/berpod/app/ui/common/EpisodeFormatting.kt`, replace the existing `formatDuration` function with:

```kotlin
fun formatDuration(seconds: Int?): String? {
    if (seconds == null || seconds <= 0) return null
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "$hours h $minutes min"
        totalMinutes > 0 -> "$minutes min"
        else -> "<1 min"
    }
}
```

Add the new function below it (leave `formatPlaybackTime` untouched — it's used by `PlayerScreen`'s scrubber and stays colon-style):

```kotlin
fun episodeProgressFraction(lastPositionMs: Long, durationSeconds: Int?): Float? {
    if (lastPositionMs <= 0L) return null
    if (durationSeconds == null || durationSeconds <= 0) return null
    val durationMs = durationSeconds.toLong() * 1000L
    return (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.berpod.app.ui.common.EpisodeFormattingTest"`
Expected: PASS, all 8 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/berpod/app/ui/common/EpisodeFormatting.kt app/src/test/java/com/berpod/app/ui/common/EpisodeFormattingTest.kt
git commit -m "Reformat episode duration and add playback progress fraction helper"
```

---

### Task 3: PlaybackRepository persists position to Room

**Files:**
- Modify: `app/src/main/java/com/berpod/app/data/repository/PlaybackRepository.kt`
- Modify: `app/src/main/java/com/berpod/app/di/AppContainer.kt:50-52`
- Create: `app/src/test/java/com/berpod/app/data/repository/PlaybackRepositoryTest.kt`

**Interfaces:**
- Consumes: `EpisodeDao.updatePosition(episodeId: Long, positionMs: Long)` from Task 1
- Produces: `PlaybackRepository(context: Context, episodeDao: EpisodeDao)` (constructor signature changes — `AppContainer` is the only caller and is updated in this task); internal `shouldPersistPosition(lastPersistedAtMs: Long, nowMs: Long, intervalMs: Long = 5_000L): Boolean`

`MediaController`/`ExoPlayer` need a real Android environment, so `PlaybackRepository` as a whole isn't unit-testable here (no Robolectric in this project). The persistence *cadence decision* is extracted into a pure function so it can be TDD'd; the wiring itself is verified manually in Step 6.

- [ ] **Step 1: Write the failing test for the cadence helper**

Create `app/src/test/java/com/berpod/app/data/repository/PlaybackRepositoryTest.kt`:

```kotlin
package com.berpod.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRepositoryTest {

    @Test
    fun `shouldPersistPosition is false before the interval elapses`() {
        assertFalse(shouldPersistPosition(lastPersistedAtMs = 0L, nowMs = 4_999L, intervalMs = 5_000L))
    }

    @Test
    fun `shouldPersistPosition is true once the interval elapses`() {
        assertTrue(shouldPersistPosition(lastPersistedAtMs = 0L, nowMs = 5_000L, intervalMs = 5_000L))
        assertTrue(shouldPersistPosition(lastPersistedAtMs = 1_000L, nowMs = 6_001L, intervalMs = 5_000L))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.berpod.app.data.repository.PlaybackRepositoryTest"`
Expected: FAIL — `shouldPersistPosition` is unresolved.

- [ ] **Step 3: Add the cadence helper and inject `EpisodeDao`**

In `app/src/main/java/com/berpod/app/data/repository/PlaybackRepository.kt`, add this top-level function above the `PlaybackRepository` class:

```kotlin
internal fun shouldPersistPosition(lastPersistedAtMs: Long, nowMs: Long, intervalMs: Long = 5_000L): Boolean =
    nowMs - lastPersistedAtMs >= intervalMs
```

Change the class declaration and add the import:

```kotlin
import com.berpod.app.data.local.EpisodeDao
```

```kotlin
class PlaybackRepository(context: Context, private val episodeDao: EpisodeDao) {
```

Add a tracking field next to the other `private var` fields:

```kotlin
private var lastPersistedAtMs = 0L
```

Add a persistence helper method inside the class:

```kotlin
private fun persistPosition(episodeId: Long, positionMs: Long) {
    lastPersistedAtMs = System.currentTimeMillis()
    scope.launch { episodeDao.updatePosition(episodeId, positionMs) }
}
```

- [ ] **Step 4: Wire persistence into the polling loop, pause, and track transitions**

Replace `restartPositionPolling` with:

```kotlin
private fun restartPositionPolling(isPlaying: Boolean) {
    positionPollJob?.cancel()
    if (!isPlaying) return
    positionPollJob = scope.launch {
        while (isActive) {
            controller?.let { c ->
                _uiState.update { it.copy(positionMs = c.currentPosition) }
                val episodeId = _uiState.value.currentEpisodeId
                if (episodeId != null && shouldPersistPosition(lastPersistedAtMs, System.currentTimeMillis())) {
                    persistPosition(episodeId, c.currentPosition)
                }
            }
            delay(500)
        }
    }
}
```

Replace `onIsPlayingChanged` inside `playerListener` with:

```kotlin
override fun onIsPlayingChanged(isPlaying: Boolean) {
    _uiState.update { it.copy(isPlaying = isPlaying) }
    restartPositionPolling(isPlaying)
    if (!isPlaying) {
        val episodeId = _uiState.value.currentEpisodeId
        val position = controller?.currentPosition ?: _uiState.value.positionMs
        if (episodeId != null) persistPosition(episodeId, position)
    }
}
```

Replace `onMediaItemTransition` inside `playerListener` with:

```kotlin
override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    val previousEpisodeId = _uiState.value.currentEpisodeId
    val previousPosition = _uiState.value.positionMs
    if (previousEpisodeId != null) {
        persistPosition(previousEpisodeId, previousPosition)
    }
    val metadata = mediaItem?.mediaMetadata
    _uiState.update {
        it.copy(
            currentEpisodeId = mediaItem?.mediaId?.toLongOrNull(),
            title = metadata?.title?.toString().orEmpty(),
            podcastTitle = metadata?.artist?.toString().orEmpty(),
            artworkUrl = metadata?.artworkUri?.toString(),
            positionMs = 0L
        )
    }
}
```

- [ ] **Step 5: Update `AppContainer` to pass the DAO**

In `app/src/main/java/com/berpod/app/di/AppContainer.kt`, change:

```kotlin
override val playbackRepository: PlaybackRepository by lazy {
    PlaybackRepository(appContext)
}
```

to:

```kotlin
override val playbackRepository: PlaybackRepository by lazy {
    PlaybackRepository(appContext, database.episodeDao())
}
```

- [ ] **Step 6: Run the cadence test, compile, and manually verify persistence**

Run: `./gradlew :app:testDebugUnitTest --tests "com.berpod.app.data.repository.PlaybackRepositoryTest"`
Expected: PASS, both tests green.

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Manual check (device/emulator): play an episode for at least 6 seconds, pause it, force-stop the app (`adb shell am force-stop com.berpod.app`), relaunch, and confirm (once Task 6 wires the progress bar into the UI) the episode shows a non-zero, non-full progress bar. It's fine to defer the visible confirmation to Task 6/7's manual pass — at this step it's enough that the app doesn't crash and `compileDebugKotlin` is clean.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/berpod/app/data/repository/PlaybackRepository.kt app/src/main/java/com/berpod/app/di/AppContainer.kt app/src/test/java/com/berpod/app/data/repository/PlaybackRepositoryTest.kt
git commit -m "Persist playback position periodically and on pause/track switch"
```

---

### Task 4: Color palette and theme

**Files:**
- Modify: `app/src/main/java/com/berpod/app/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/theme/Theme.kt`

**Interfaces:**
- Produces: `AccentPurple`, `SurfaceWhite`, `TextPrimary`, `TextSecondary` (all `androidx.compose.ui.graphics.Color`); `BerpodTheme(content: @Composable () -> Unit)` — signature simplifies (drops `darkTheme`/`dynamicColor` params; `MainActivity.kt`'s call site already passes no args, so it needs no change)

No unit test — this is pure Compose theming, verified by compiling and a visual pass.

- [ ] **Step 1: Replace the color palette**

Replace the full contents of `app/src/main/java/com/berpod/app/ui/theme/Color.kt` with:

```kotlin
package com.berpod.app.ui.theme

import androidx.compose.ui.graphics.Color

val AccentPurple = Color(0xFF5B2D8E)
val SurfaceWhite = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF161616)
val TextSecondary = Color(0xFF767676)
```

- [ ] **Step 2: Replace the theme to be light-only with the fixed palette**

Replace the full contents of `app/src/main/java/com/berpod/app/ui/theme/Theme.kt` with:

```kotlin
package com.berpod.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = AccentPurple,
    onPrimary = SurfaceWhite,
    background = SurfaceWhite,
    surface = SurfaceWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

@Composable
fun BerpodTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If `MainActivity.kt` fails to compile because it passes `darkTheme`/`dynamicColor` explicitly, check `app/src/main/java/com/berpod/app/MainActivity.kt` — as of this plan it calls `BerpodTheme { BerpodApp(container = container) }` with no named arguments, so no change should be needed there.)

- [ ] **Step 4: Manual visual check**

Run the app on a device/emulator. Confirm the Podcasts screen background is white and any Material button/accent (e.g. a `+` FAB, if present) renders in the purple `#5B2D8E`, not the old demo purple.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/berpod/app/ui/theme/Color.kt app/src/main/java/com/berpod/app/ui/theme/Theme.kt
git commit -m "Replace default Compose palette with purple-accent light theme"
```

---

### Task 5: Redesign `EpisodeListItem`

**Files:**
- Modify: `app/src/main/java/com/berpod/app/ui/common/EpisodeListItem.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `formatDuration`, `episodeProgressFraction` (Task 2)
- Produces: `EpisodeListItem(episode, podcastTitle, podcastImageUrl, onAddToQueue, onAddToPlaylist, trailingContent, onPlay, isCurrentlyPlaying, onDownload: (() -> Unit)? = null, progressFraction: Float? = null)` — note `podcastImageUrl` stays as a parameter (existing call sites pass it) but is no longer rendered; `onDownload`/`onAddToQueue`/`onAddToPlaylist` render as three inline icon buttons whenever at least one is non-null

No unit test — this is a Compose layout change, verified by compiling and the manual visual pass in Task 6/7.

- [ ] **Step 1: Add the download action string**

In `app/src/main/res/values/strings.xml`, add next to `action_add_to_playlist`:

```xml
<string name="action_download">Stáhnout</string>
```

- [ ] **Step 2: Rewrite `EpisodeListItem`**

Replace the full contents of `app/src/main/java/com/berpod/app/ui/common/EpisodeListItem.kt` with:

```kotlin
package com.berpod.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.berpod.app.R
import com.berpod.app.data.local.EpisodeEntity

@Composable
fun EpisodeListItem(
    episode: EpisodeEntity,
    podcastTitle: String?,
    podcastImageUrl: String?,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null,
    isCurrentlyPlaying: Boolean = false,
    onDownload: (() -> Unit)? = null,
    progressFraction: Float? = null
) {
    val metaParts = listOfNotNull(formatPubDate(episode.pubDateMillis), formatDuration(episode.durationSeconds))

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (podcastTitle != null) {
                Text(
                    text = podcastTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (metaParts.isNotEmpty()) {
                Text(
                    text = metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (progressFraction != null) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            }
            if (onDownload != null || onAddToQueue != null || onAddToPlaylist != null) {
                Row(modifier = Modifier.padding(top = 6.dp)) {
                    if (onDownload != null) {
                        IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = stringResource(R.string.action_download),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (onAddToQueue != null) {
                        IconButton(onClick = onAddToQueue, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = stringResource(R.string.action_add_to_queue),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (onAddToPlaylist != null) {
                        IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = stringResource(R.string.action_add_to_playlist),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (onPlay != null) {
            OutlinedIconButton(
                onClick = onPlay,
                modifier = Modifier.padding(start = 12.dp).size(38.dp)
            ) {
                Icon(
                    imageVector = if (isCurrentlyPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (isCurrentlyPlaying) R.string.action_pause else R.string.action_play
                    )
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        }
    }
}
```

This drops the `AsyncImage` artwork, the `⋮` `DropdownMenu`, `isMenuExpanded` state, and the `LocalContentColor` import that were only used by the old play-icon tinting — the play button is now always purple (`OutlinedIconButton` uses `MaterialTheme.colorScheme.primary` by default for its border/icon), matching the approved mockup where play doesn't change color based on state beyond the icon swap.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If `Icons.Filled.Download` or `Icons.AutoMirrored.Filled.PlaylistAdd` don't resolve, check the exact names in the installed `material-icons-extended` version — search `~/.gradle/caches` or use IDE autocomplete for the closest match (e.g. some versions expose `Icons.AutoMirrored.Filled.PlaylistAdd` vs a non-mirrored `Icons.Filled.PlaylistAdd`; `Icons.AutoMirrored.Filled.QueueMusic` is already used successfully elsewhere in this codebase in `BerpodApp.kt`, confirming that family is available).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/berpod/app/ui/common/EpisodeListItem.kt app/src/main/res/values/strings.xml
git commit -m "Redesign EpisodeListItem: drop artwork, add progress bar and inline actions"
```

---

### Task 6: Wire progress and download into all four episode list screens

**Files:**
- Modify: `app/src/main/java/com/berpod/app/ui/queue/QueueScreen.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/playlists/PlaylistDetailScreen.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/newepisodes/NewEpisodesScreen.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/podcastlist/PodcastEpisodesScreen.kt`

**Interfaces:**
- Consumes: `episodeProgressFraction(lastPositionMs: Long, durationSeconds: Int?): Float?` (Task 2), `EpisodeListItem`'s new `onDownload`/`progressFraction` params (Task 5)

No new unit tests — this is inline UI wiring using an already-tested pure function. Verified by compiling and the manual visual pass in Step 5.

- [ ] **Step 1: Wire `QueueScreen`**

In `app/src/main/java/com/berpod/app/ui/queue/QueueScreen.kt`, add the import:

```kotlin
import com.berpod.app.ui.common.episodeProgressFraction
```

In the `EpisodeListItem(...)` call inside `itemsIndexed`, add a line after `isCurrentlyPlaying = ...`:

```kotlin
progressFraction = episodeProgressFraction(item.episode.lastPositionMs, item.episode.durationSeconds),
```

- [ ] **Step 2: Wire `PlaylistDetailScreen`**

In `app/src/main/java/com/berpod/app/ui/playlists/PlaylistDetailScreen.kt`, add the import:

```kotlin
import com.berpod.app.ui.common.episodeProgressFraction
```

In the `EpisodeListItem(...)` call inside `itemsIndexed`, add a line after `isCurrentlyPlaying = ...`:

```kotlin
progressFraction = episodeProgressFraction(item.episode.lastPositionMs, item.episode.durationSeconds),
```

- [ ] **Step 3: Wire `NewEpisodesScreen`**

In `app/src/main/java/com/berpod/app/ui/newepisodes/NewEpisodesScreen.kt`, add the import:

```kotlin
import com.berpod.app.ui.common.episodeProgressFraction
```

In the `EpisodeListItem(...)` call inside `items`, add two lines after `isCurrentlyPlaying = ...`:

```kotlin
onDownload = {},
progressFraction = episodeProgressFraction(item.episode.lastPositionMs, item.episode.durationSeconds),
```

- [ ] **Step 4: Wire `PodcastEpisodesScreen`**

In `app/src/main/java/com/berpod/app/ui/podcastlist/PodcastEpisodesScreen.kt`, add the import:

```kotlin
import com.berpod.app.ui.common.episodeProgressFraction
```

In the `EpisodeListItem(...)` call inside `items`, add two lines after `isCurrentlyPlaying = ...` (note this screen iterates `EpisodeEntity` directly as `episode`, not a wrapper with `.episode`):

```kotlin
onDownload = {},
progressFraction = episodeProgressFraction(episode.lastPositionMs, episode.durationSeconds),
```

- [ ] **Step 5: Verify compilation and do the full manual pass**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Manual check (device/emulator):
1. Open Queue, New Episodes, Podcast Episodes, Playlist Detail — confirm each row shows date · duration, title, and (except Podcast Episodes) the podcast name above the meta line.
2. Confirm New Episodes and Podcast Episodes show the download/queue/playlist icon row; Queue and Playlist Detail show their existing reorder/remove controls instead.
3. Play an episode partway, pause, background the app, relaunch, and confirm the progress bar appears with roughly the right fraction on that episode's row in every screen it appears on.
4. Confirm an episode that's never been played shows no progress bar.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/berpod/app/ui/queue/QueueScreen.kt app/src/main/java/com/berpod/app/ui/playlists/PlaylistDetailScreen.kt app/src/main/java/com/berpod/app/ui/newepisodes/NewEpisodesScreen.kt app/src/main/java/com/berpod/app/ui/podcastlist/PodcastEpisodesScreen.kt
git commit -m "Show playback progress and download action across episode list screens"
```

---

### Task 7: Bottom navigation and mini-player visual polish

**Files:**
- Modify: `app/src/main/java/com/berpod/app/ui/navigation/BerpodApp.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/player/MiniPlayerBar.kt`

**Interfaces:**
- Consumes: `AccentPurple` is not referenced directly — use `MaterialTheme.colorScheme.primary`/theme-derived colors so the nav stays in sync with Task 4's palette; the one new literal is the pill background tint, which per the approved mockup is a fixed light tint independent of the theme's surface color

No unit test — Compose layout/styling only. Verified by compiling and a manual visual pass.

- [ ] **Step 1: Give the active bottom-nav tab a pill background**

In `app/src/main/java/com/berpod/app/ui/navigation/BerpodApp.kt`, the `NavigationBarItem` call currently is:

```kotlin
NavigationBarItem(
    selected = currentRoute == screen.route,
    onClick = {
        navController.navigate(screen.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    },
    icon = { Icon(iconFor(screen), contentDescription = null) },
    label = { Text(labelFor(screen)) }
)
```

Add `colors` so the active icon gets a purple pill and the label stays neutral even when selected:

```kotlin
NavigationBarItem(
    selected = currentRoute == screen.route,
    onClick = {
        navController.navigate(screen.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    },
    icon = { Icon(iconFor(screen), contentDescription = null) },
    label = { Text(labelFor(screen)) },
    colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    )
)
```

Add the imports:

```kotlin
import androidx.compose.material3.NavigationBarItemDefaults
```

(`MaterialTheme` is already imported in this file for other uses — if not, add `import androidx.compose.material3.MaterialTheme`.)

- [ ] **Step 2: Tint the mini-player background**

In `app/src/main/java/com/berpod/app/ui/player/MiniPlayerBar.kt`, the `Surface(...)` call currently is:

```kotlin
Surface(onClick = onClick, tonalElevation = 3.dp) {
```

Change it to:

```kotlin
Surface(onClick = onClick, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.03f)) {
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual visual check**

Run the app, start playing an episode so the mini-player is visible, and switch between bottom-nav tabs. Confirm: the active tab's icon sits inside a soft purple pill, its label is still gray (not purple), and the mini-player bar above the nav has a very faint purple tint rather than a flat white/elevated look.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/berpod/app/ui/navigation/BerpodApp.kt app/src/main/java/com/berpod/app/ui/player/MiniPlayerBar.kt
git commit -m "Style active bottom-nav tab and mini-player to match the purple theme"
```
