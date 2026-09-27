package com.berpod.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeStartTest {

    @Test
    fun `resumes an episode in progress`() {
        assertEquals(470_000L, resumeStartMs(lastPositionMs = 470_000, durationSeconds = 939))
    }

    @Test
    fun `restarts a finished episode from the beginning`() {
        assertEquals(0L, resumeStartMs(lastPositionMs = 935_000, durationSeconds = 939))
        assertEquals(0L, resumeStartMs(lastPositionMs = 939_000, durationSeconds = 939))
    }

    @Test
    fun `unknown duration or no position starts where stored or at zero`() {
        assertEquals(12_000L, resumeStartMs(lastPositionMs = 12_000, durationSeconds = null))
        assertEquals(0L, resumeStartMs(lastPositionMs = -5, durationSeconds = 600))
    }
}
