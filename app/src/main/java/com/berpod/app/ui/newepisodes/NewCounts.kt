package com.berpod.app.ui.newepisodes

import com.berpod.app.data.local.EpisodeWithFeed

/**
 * How many new episodes each podcast has, as Nové counts them: an episode in the queue has been picked and is
 * no longer new. Podcasts without any are left out.
 */
fun newEpisodeCounts(newEpisodes: List<EpisodeWithFeed>, queuedEpisodeIds: Set<Long>): Map<Long, Int> =
    newEpisodes.filter { it.episode.id !in queuedEpisodeIds }.groupingBy { it.episode.feedId }.eachCount()
