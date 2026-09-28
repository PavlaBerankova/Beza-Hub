package com.berpod.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class FeedLookupMessageTest {

    @Test
    fun `network failures read as Czech sentences, not exception text`() {
        assertEquals(
            "Adresa neexistuje nebo není dostupná",
            feedLookupMessage(UnknownHostException("Unable to resolve host \"x.cz\": No address associated with hostname"))
        )
        assertEquals("Server neodpovídá", feedLookupMessage(SocketTimeoutException("timeout")))
        assertEquals("Nepodařilo se připojit k serveru", feedLookupMessage(IOException("broken pipe")))
    }

    @Test
    fun `our own messages are kept, anything else falls back`() {
        assertEquals("Na této adrese není RSS zdroj", feedLookupMessage(IllegalArgumentException("Na této adrese není RSS zdroj")))
        assertEquals("Nepodařilo se načíst", feedLookupMessage(RuntimeException()))
    }
}
