package com.berpod.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadLogicTest {

    @Test
    fun `206 continues the partial file`() {
        assertEquals(ResponseAction.Append, classifyResponse(206))
    }

    @Test
    fun `200 means the whole file comes`() {
        assertEquals(ResponseAction.StartOver, classifyResponse(200))
    }

    @Test
    fun `416 discards the partial file`() {
        assertEquals(ResponseAction.DiscardAndRetry, classifyResponse(416))
    }

    @Test
    fun `server trouble is retried`() {
        listOf(408, 429, 500, 503).forEach { assertEquals("$it", ResponseAction.Retry, classifyResponse(it)) }
    }

    @Test
    fun `missing or forbidden files fail`() {
        listOf(401, 403, 404, 410).forEach { assertEquals("$it", ResponseAction.Fail, classifyResponse(it)) }
    }

    @Test
    fun `range header only when there is something to continue`() {
        assertNull(rangeHeader(0))
        assertEquals("bytes=1500-", rangeHeader(1500))
    }

    @Test
    fun `total size comes from the content range`() {
        assertEquals(1000L, totalFromContentRange("bytes 100-999/1000"))
        assertNull(totalFromContentRange("bytes 100-999/*"))
        assertNull(totalFromContentRange(null))
    }

    @Test
    fun `file name keeps the audio extension`() {
        assertEquals("7.mp3", downloadFileName(7, "https://x.cz/a/ep.mp3?token=1"))
        assertEquals("7.m4a", downloadFileName(7, "https://x.cz/a/ep.M4A"))
    }

    @Test
    fun `file name falls back to mp3`() {
        assertEquals("7.mp3", downloadFileName(7, "https://x.cz/stream/12345"))
        assertEquals("7.mp3", downloadFileName(7, "https://x.cz/a/file.toolongext"))
    }

    @Test
    fun `progress is a clamped percentage`() {
        assertEquals(42, progressPercent(420, 1000))
        assertEquals(100, progressPercent(2000, 1000))
        assertNull(progressPercent(10, null))
        assertNull(progressPercent(10, 0))
    }
}
