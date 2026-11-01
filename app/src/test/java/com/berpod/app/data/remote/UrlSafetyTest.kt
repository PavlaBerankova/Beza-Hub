package com.berpod.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSafetyTest {

    @Test
    fun `only http and https addresses with a host are web urls`() {
        assertTrue(isWebUrl("https://example.cz/a?b=1"))
        assertTrue(isWebUrl("HTTP://example.cz"))
        listOf(
            null, "", "  ", "intent://scan/#Intent;scheme=zxing;end", "tel:+420123456789", "sms:123",
            "file:///data/data/com.berpod.app/databases/berpod.db", "content://media/external/file/1",
            "javascript:alert(1)", "ftp://example.cz/x", "https:///nohost", "/relative/path", "example.cz",
            "https://exa mple.cz", "https://example.cz/\nx"
        ).forEach { assertFalse("$it", isWebUrl(it)) }
    }

    @Test
    fun `webUrlOrNull trims good addresses and drops the rest`() {
        assertEquals("https://example.cz", webUrlOrNull("  https://example.cz "))
        assertNull(webUrlOrNull("tel:123"))
        assertNull(webUrlOrNull(null))
    }

    @Test
    fun `redirects stay on the web and never leave https`() {
        assertEquals("https://example.cz/new", redirectTarget("https://example.cz/old", "/new"))
        assertEquals("https://other.cz/feed", redirectTarget("http://example.cz/old", "https://other.cz/feed"))
        assertEquals("http://other.cz/feed", redirectTarget("http://example.cz/old", "http://other.cz/feed"))
        assertThrows(IllegalArgumentException::class.java) { redirectTarget("https://example.cz", "file:///etc/hosts") }
        assertThrows(IllegalArgumentException::class.java) { redirectTarget("https://example.cz", "ftp://example.cz/x") }
        assertThrows(IllegalArgumentException::class.java) { redirectTarget("https://example.cz", "http://example.cz/feed") }
    }
}
