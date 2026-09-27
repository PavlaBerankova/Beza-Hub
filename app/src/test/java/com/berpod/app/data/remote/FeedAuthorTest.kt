package com.berpod.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedAuthorTest {

    @Test
    fun `itunes author wins over owner name`() {
        assertEquals("Český rozhlas", pickAuthor("Český rozhlas", "Owner"))
    }

    @Test
    fun `falls back to owner name and trims`() {
        assertEquals("Filip", pickAuthor("  ", "  Filip "))
    }

    @Test
    fun `blank or missing values give null`() {
        assertNull(pickAuthor(null, null))
        assertNull(pickAuthor("", " "))
    }
}
