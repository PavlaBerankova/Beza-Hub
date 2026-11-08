package com.berpod.app.ui.podcastlist

import com.berpod.app.data.local.PodcastFeedEntity
import java.text.Collator
import java.util.Locale

/** How the podcast list is ordered. */
enum class PodcastSort { DateAdded, Alphabetical }

private val czechCollator: Collator = Collator.getInstance(Locale("cs", "CZ"))

/** [feeds] in the given order: the most recently added first, or by title in Czech alphabetical order. */
fun sortedPodcasts(feeds: List<PodcastFeedEntity>, sort: PodcastSort): List<PodcastFeedEntity> = when (sort) {
    PodcastSort.DateAdded -> feeds.sortedByDescending { it.dateAdded }
    PodcastSort.Alphabetical -> feeds.sortedWith { a, b -> czechCollator.compare(a.title.trim(), b.title.trim()) }
}
