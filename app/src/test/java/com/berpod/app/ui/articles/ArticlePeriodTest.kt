package com.berpod.app.ui.articles

import com.berpod.app.ui.newepisodes.NewPeriod
import com.berpod.app.ui.newepisodes.filterByPeriod
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticlePeriodTest {

    @Test
    fun `cycle skips two weeks`() {
        val steps = generateSequence(NewPeriod.All) { nextArticlePeriod(it) }.drop(1).take(4).toList()
        assertEquals(listOf(NewPeriod.Today, NewPeriod.Week, NewPeriod.Month, NewPeriod.All), steps)
    }

    @Test
    fun `period filter drops undated articles`() {
        val now = 1_000_000_000L
        val items = listOf(1L to null, 2L to now - 1000)
        assertEquals(listOf(2L), filterByPeriod(items, NewPeriod.Week, now) { it.second }.map { it.first })
        assertEquals(listOf(1L, 2L), filterByPeriod(items, NewPeriod.All, now) { it.second }.map { it.first })
    }
}
