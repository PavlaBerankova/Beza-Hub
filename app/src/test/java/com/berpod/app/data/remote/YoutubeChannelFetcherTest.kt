package com.berpod.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YoutubeChannelFetcherTest {

    @Test
    fun `a bare handle is turned into a youtube channel address`() {
        assertEquals("https://www.youtube.com/@kosmoscz", normalizeChannelUrl("@kosmoscz"))
        assertEquals("https://www.youtube.com/@kosmoscz", normalizeChannelUrl("  @kosmoscz  "))
    }

    @Test
    fun `an address that already names a host is left to the normal source normaliser`() {
        assertEquals("https://youtube.com/channel/UC123", normalizeChannelUrl("youtube.com/channel/UC123"))
        assertEquals("https://example.com/feeds/videos.xml?channel_id=UC1", normalizeChannelUrl("https://example.com/feeds/videos.xml?channel_id=UC1"))
        assertNull(normalizeChannelUrl("   "))
        assertNull(normalizeChannelUrl("@"))
    }
}
