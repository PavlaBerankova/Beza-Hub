package com.berpod.app.ui.podcastlist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderBarTest {

    // Header 500px tall, bar 150px: the bar may stay transparent while at least 150px of header remain.
    @Test
    fun `transparent while enough header is left behind the bar`() {
        assertTrue(isBarOverHeader(firstVisibleIndex = 0, scrollOffsetPx = 0, headerHeightPx = 500, barHeightPx = 150))
        assertTrue(isBarOverHeader(firstVisibleIndex = 0, scrollOffsetPx = 350, headerHeightPx = 500, barHeightPx = 150))
    }

    @Test
    fun `solid once the rows would slide under the bar`() {
        assertFalse(isBarOverHeader(firstVisibleIndex = 0, scrollOffsetPx = 351, headerHeightPx = 500, barHeightPx = 150))
        assertFalse(isBarOverHeader(firstVisibleIndex = 1, scrollOffsetPx = 0, headerHeightPx = 500, barHeightPx = 150))
    }

    @Test
    fun `transparent before the header has been measured`() {
        assertTrue(isBarOverHeader(firstVisibleIndex = 0, scrollOffsetPx = 0, headerHeightPx = 0, barHeightPx = 0))
    }
}
