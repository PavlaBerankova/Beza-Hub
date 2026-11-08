package com.berpod.app.ui.podcastlist

import com.berpod.app.data.local.EpisodeEntity
import java.text.Normalizer

private val combiningMarks = Regex("\\p{M}+")
private val htmlTags = Regex("<[^>]*>")
private val entities = mapOf("&nbsp;" to " ", "&amp;" to "&", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&lt;" to "<", "&gt;" to ">")

/** Lower case, without diacritics and without HTML markup, so "Česko" is found by "cesko" and a tag never matches. */
internal fun searchable(text: String?): String {
    if (text.isNullOrBlank()) return ""
    var plain = htmlTags.replace(text, " ")
    entities.forEach { (entity, char) -> plain = plain.replace(entity, char) }
    return combiningMarks.replace(Normalizer.normalize(plain, Normalizer.Form.NFD), "").lowercase()
}

/** Whether every word of [query] appears in the episode's title or description; a blank query matches everything. */
fun episodeMatches(episode: EpisodeEntity, query: String): Boolean {
    val words = searchable(query).split(' ', '\n', '\t').filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val haystack = searchable(episode.title) + " " + searchable(episode.description)
    return words.all { it in haystack }
}
