package com.berpod.app.ui.articles

import com.berpod.app.R
import com.berpod.app.ui.newepisodes.NewPeriod

/** The periods Články steps through; "14 dní" is left out. */
private val ARTICLE_PERIODS = listOf(NewPeriod.All, NewPeriod.Today, NewPeriod.Week, NewPeriod.Month)

/** The next period when the filter button is tapped: all, today, week, month, then round again. */
fun nextArticlePeriod(period: NewPeriod): NewPeriod {
    val index = ARTICLE_PERIODS.indexOf(period)
    return ARTICLE_PERIODS[(index + 1) % ARTICLE_PERIODS.size]
}

fun NewPeriod.articleTitleRes(): Int = when (this) {
    NewPeriod.All -> R.string.articles_period_all
    NewPeriod.Today -> R.string.articles_period_today
    NewPeriod.Week, NewPeriod.TwoWeeks -> R.string.articles_period_week
    NewPeriod.Month -> R.string.articles_period_month
}
