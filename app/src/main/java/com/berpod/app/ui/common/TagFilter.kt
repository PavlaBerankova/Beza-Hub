package com.berpod.app.ui.common

import com.berpod.app.data.local.TagEntity

/** Keeps [selected] if that tag still exists, otherwise falls back to "Všechny" (null). */
fun resolveSelectedTagId(selected: Long?, tags: List<TagEntity>): Long? =
    selected?.takeIf { id -> tags.any { it.id == id } }

/** Items whose podcast id (via [podcastIdOf]) is in [podcastIds], in their original order; null means no filter. */
fun <T> filterByPodcastIds(items: List<T>, podcastIds: Set<Long>?, podcastIdOf: (T) -> Long): List<T> =
    if (podcastIds == null) items else items.filter { podcastIdOf(it) in podcastIds }
