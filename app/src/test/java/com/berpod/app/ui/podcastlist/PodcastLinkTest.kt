package com.berpod.app.ui.podcastlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastLinkTest {

    @Test
    fun `valid web addresses are kept, bare www gets https`() {
        assertEquals("https://forendors.cz/x", normalizeLinkUrl("https://forendors.cz/x"))
        assertEquals("http://a.cz", normalizeLinkUrl("http://a.cz"))
        assertEquals("https://www.forendors.cz", normalizeLinkUrl(" www.forendors.cz "))
    }

    @Test
    fun `anything that is not a web address is rejected`() {
        for (bad in listOf("", "   ", "javascript:alert(1)", "ftp://x.cz", "https://", "foo bar", "nodots")) {
            assertNull("'$bad' should be rejected", normalizeLinkUrl(bad))
        }
    }

    @Test
    fun `a title is shown as written`() =
        assertEquals("Forendors – předplatné", linkLabel("Forendors – předplatné", "https://www.forendors.cz/x"))

    @Test
    fun `without a title the domain is shown`() {
        assertEquals("forendors.cz", linkLabel(null, "https://www.forendors.cz/x"))
        assertEquals("forendors.cz", linkLabel("  ", "https://www.forendors.cz/x"))
    }
}
