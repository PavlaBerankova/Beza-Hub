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
