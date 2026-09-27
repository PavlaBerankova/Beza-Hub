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
