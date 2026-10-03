package com.berpod.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleFeedDiscoveryTest {

    @Test
    fun `source url gets https when the scheme is missing`() {
        assertEquals("https://example.cz", normalizeSourceUrl("  example.cz "))
        assertEquals("http://example.cz/feed", normalizeSourceUrl("http://example.cz/feed"))
        assertNull(normalizeSourceUrl("   "))
        assertNull(normalizeSourceUrl("ne platná adresa"))
    }

    @Test
    fun `feed links are found in the page head and resolved against the page`() {
        val html = """
            <html><head>
            <link rel="stylesheet" href="/style.css">
            <link rel="alternate" type="application/rss+xml" title="RSS" href="/feed/">
            <link href="https://example.cz/atom.xml" type="application/atom+xml" rel="alternate">
            <link rel="alternate" type="text/html" href="/en/">
            </head></html>
        """.trimIndent()
        assertEquals(
            listOf("https://example.cz/feed/", "https://example.cz/atom.xml"),
            findFeedLinks(html, "https://example.cz/blog/post")
        )
        assertEquals(emptyList<String>(), findFeedLinks("<html><head></head></html>", "https://example.cz"))
    }

    @Test
    fun `a document is a feed when it opens with rss, atom or rdf`() {
        assertEquals(true, looksLikeFeed("<?xml version=\"1.0\"?>\n<rss version=\"2.0\"><channel>"))
        assertEquals(
            true,
            looksLikeFeed("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<?xml-stylesheet type=\"text/xsl\" href=\"style.xsl\"?>\n<rss version=\"2.0\">")
        )
        assertEquals(true, looksLikeFeed("<feed xmlns=\"http://www.w3.org/2005/Atom\">"))
        assertEquals(true, looksLikeFeed("<rdf:RDF xmlns:rdf=\"…\">"))
        assertEquals(false, looksLikeFeed("<!doctype html><html><head>"))
    }
}
