package com.berpod.app.ui.podcastlist

import com.berpod.app.testutil.episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeSearchTest {

    private fun ep(id: Long, title: String, description: String? = null) =
        episode(id = id, feedId = 1).copy(title = title, description = description)

    @Test
    fun `a blank query matches everything`() {
        assertTrue(episodeMatches(ep(1, "Cokoli"), ""))
        assertTrue(episodeMatches(ep(1, "Cokoli"), "   "))
    }

    @Test
    fun `finds a word in the title or in the description`() {
        assertTrue(episodeMatches(ep(1, "Rozhovor s Karlem"), "karlem"))
        assertTrue(episodeMatches(ep(2, "Díl 2", "Hostem je Jan Novák"), "novák"))
        assertFalse(episodeMatches(ep(3, "Díl 3", "O něčem jiném"), "novák"))
    }

    @Test
    fun `ignores case and diacritics in both the query and the text`() {
        assertTrue(episodeMatches(ep(1, "Úžasné Česko"), "cesko"))
        assertTrue(episodeMatches(ep(2, "Ceske drahy"), "ČESKÉ"))
    }

    @Test
    fun `every word has to match, in any order`() {
        val episode = ep(1, "Rozhovor s Karlem", "O programování v Kotlinu")
        assertTrue(episodeMatches(episode, "kotlinu karlem"))
        assertFalse(episodeMatches(episode, "karlem java"))
    }

    @Test
    fun `html markup and entities in the description do not match or hide words`() {
        val episode = ep(1, "Díl", "<p>Dnes o <a href=\"https://example.com/zdroj\">Kotlinu</a>&nbsp;a Rustu</p>")
        assertTrue(episodeMatches(episode, "kotlinu"))
        assertTrue(episodeMatches(episode, "rustu"))
        assertFalse(episodeMatches(episode, "href"))
        assertFalse(episodeMatches(episode, "zdroj"))
    }

    @Test
    fun `the episode view applies the search to the filtered, sorted list`() {
        val episodes = listOf(ep(3, "Kotlin dnes"), ep(2, "Rust"), ep(1, "Kotlin včera"))
        val result = applyEpisodeView(episodes, EpisodeSort.NewestFirst, EpisodeFilter.All, "kotlin")
        assertEquals(listOf(3L, 1L), result.map { it.id })
    }
}
