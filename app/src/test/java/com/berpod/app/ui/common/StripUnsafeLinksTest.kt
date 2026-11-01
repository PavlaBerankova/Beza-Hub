package com.berpod.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class StripUnsafeLinksTest {

    @Test
    fun `web links are kept`() {
        val html = """<p><a href="https://example.cz/a?x=1&amp;y=2">web</a> <a class="x" href='http://example.cz'>web</a></p>"""
        assertEquals(html, stripUnsafeLinks(html))
    }

    @Test
    fun `links to other schemes lose their address but keep their text`() {
        assertEquals("""<a>call</a>""", stripUnsafeLinks("""<a href="tel:+420123">call</a>"""))
        assertEquals("""<a>x</a>""", stripUnsafeLinks("""<a href='intent://x#Intent;end'>x</a>"""))
        assertEquals("""<A>x</A>""", stripUnsafeLinks("""<A HREF=javascript:alert(1)>x</A>"""))
        assertEquals("""<a>x</a>""", stripUnsafeLinks("""<a href="jav&#97;script:alert(1)">x</a>"""))
        assertEquals("""<a>x</a>""", stripUnsafeLinks("""<a href="/relative">x</a>"""))
    }
}
