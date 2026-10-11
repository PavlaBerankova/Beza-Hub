package com.berpod.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class PlainDescriptionTest {

    @Test
    fun `tags are removed and paragraphs become spaces`() {
        assertEquals("První odstavec. Druhý odstavec.", plainDescription("<p>První odstavec.</p><p>Druhý odstavec.</p>"))
    }

    @Test
    fun `line breaks and runs of white space collapse to one space`() {
        assertEquals("a b c", plainDescription("a\n\n  b<br/>c"))
    }

    @Test
    fun `entities are decoded`() {
        assertEquals("Tom & Jerry „říkají“ ‘ok’", plainDescription("Tom &amp; Jerry &#8222;říkají&#8220; &#x2018;ok&#x2019;"))
        assertEquals("a b", plainDescription("a&nbsp;b"))
    }

    @Test
    fun `scripts and styles disappear with their content`() {
        assertEquals("text", plainDescription("<style>p{color:red}</style>text<script>alert(1)</script>"))
    }

    @Test
    fun `links keep their text`() {
        assertEquals("Více na webu", plainDescription("Více na <a href=\"https://x.cz\">webu</a>"))
    }

    @Test
    fun `nothing to show gives an empty string`() {
        assertEquals("", plainDescription(null))
        assertEquals("", plainDescription("   "))
        assertEquals("", plainDescription("<p> </p>"))
    }
}
