package com.elinacn.subtrack.ui.statistics

import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.usecase.CategoryShare
import com.elinacn.subtrack.domain.usecase.MonthlyChange
import com.elinacn.subtrack.domain.usecase.MonthlyTrend
import com.elinacn.subtrack.domain.usecase.SubscriptionCost
import com.elinacn.subtrack.domain.usecase.TrendPoint

/**
 * Everything the statistics screen draws.
 *
 * **Still no event type.** ARCHITECTURE §5 asks for one UiState and one `onEvent` per screen, and
 * the reason it gives is that a screen should not be handed six separate lambdas. This screen
 * offers nothing to do - it reads, it does not act - and phase 13b adds a chart and a sentence,
 * neither of which the user can press. An empty `sealed interface` and an `onEvent` with no
 * branches would be ceremony rather than architecture. Navigating back arrives as its own lambda,
 * the way it does on every other screen.
 *
 * Every figure here covers **all** subscriptions. The home screen's category filter is state that
 * belongs to the home screen, and this one never sees it (§19 records the same trap on the
 * snapshot recorder).
 */
data class StatisticsUiState(
    /** One row per category that has money in it, biggest first. */
    val categoryShares: List<CategoryShare> = emptyList(),
    /** The dearest subscriptions by monthly cost, dearest first. */
    val mostExpensive: List<SubscriptionCost> = emptyList(),
    /**
     * Consecutive months ending at this one, oldest first, read from the recorded totals.
     *
     * A point with no total is a month with no row; see [TrendPoint].
     */
    val trend: List<TrendPoint> = emptyList(),
    /** The top of the chart's scale, or null when no month in the window has a figure. */
    val trendPeak: Money? = null,
    /**
     * Months in range that were recorded in another currency and are therefore not on the chart.
     *
     * Shown to the reader rather than swallowed: a chart that is quietly shorter than the data
     * behind it is a chart that misleads without saying anything.
     */
    val monthsInOtherCurrency: Int = 0,
    /** How this month compares with last month, or null when there is no last month to compare. */
    val monthlyChange: MonthlyChange? = null,
    /** What every figure on the screen is denominated in. */
    val currency: Currency = Currency.Base,
    /**
     * Whether there is anything at all to chart.
     *
     * Not the same as an empty [categoryShares]: a list whose every subscription rounds to nothing
     * has subscriptions in it, and only having none of them is an empty state.
     */
    val hasAnySubscriptions: Boolean = false,
    val isLoading: Boolean = true
) {

    /** Whether there are enough months behind the app to draw a direction rather than a dot. */
    val canDrawTrend: Boolean
        get() = trend.size >= MonthlyTrend.MIN_POINTS_TO_DRAW

    /**
     * Whether the screen has nothing whatsoever to say.
     *
     * Both halves have to be empty. Deleting every subscription does not erase the months that
     * were already recorded, and a user who is looking at a year of history should not be told
     * there is nothing to chart yet - so the empty state stands down as soon as there is a trend
     * to draw. Kept here rather than in the composable so the rule can be tested from the
     * ViewModel's public surface.
     */
    val hasNothingToShow: Boolean
        get() = !hasAnySubscriptions && !canDrawTrend
}
