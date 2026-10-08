package com.berpod.app.data.repository

import com.berpod.app.testutil.episode
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

    @Test
    fun `the episode played last is restored while it is unfinished`() {
        assertTrue(shouldRestoreEpisode(episode(id = 1, feedId = 1)))
        assertTrue(shouldRestoreEpisode(episode(id = 1, feedId = 1).copy(lastPositionMs = 600_000)))
    }

    @Test
    fun `a finished or vanished episode is not restored`() {
        assertFalse(shouldRestoreEpisode(episode(id = 1, feedId = 1).copy(playedAtMs = 1_000L)))
        assertFalse(shouldRestoreEpisode(null))
    }
}
