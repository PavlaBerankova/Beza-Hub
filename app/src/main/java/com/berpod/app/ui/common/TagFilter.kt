package com.berpod.app.ui.common

import com.berpod.app.data.local.TagEntity

/** Keeps [selected] if that tag still exists, otherwise falls back to "Všechny" (null). */
fun resolveSelectedTagId(selected: Long?, tags: List<TagEntity>): Long? =
    selected?.takeIf { id -> tags.any { it.id == id } }
