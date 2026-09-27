package com.berpod.app.ui.common

import com.berpod.app.data.local.TagEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagFilterTest {

    private val tags = listOf(TagEntity(1, "Health"), TagEntity(2, "News"))

    @Test
    fun `keeps all-filter as null`() {
        assertNull(resolveSelectedTagId(null, tags))
    }

    @Test
    fun `keeps a selection that still exists`() {
        assertEquals(2L, resolveSelectedTagId(2L, tags))
    }

    @Test
    fun `falls back to all when the selected tag was deleted`() {
        assertNull(resolveSelectedTagId(3L, tags))
        assertNull(resolveSelectedTagId(1L, emptyList()))
    }
}
