package com.elinacn.subtrack.ui.statistics

import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.usecase.CategoryShare
import com.elinacn.subtrack.domain.usecase.SubscriptionCost

/**
 * Everything the statistics screen draws.
 *
 * **No event type.** ARCHITECTURE §5 asks for one UiState and one `onEvent` per screen, and the
 * reason it gives is that a screen should not be handed six separate lambdas. This screen offers
 * nothing to do - it reads, it does not act - so an empty `sealed interface` and an `onEvent` with
 * no branches would be ceremony rather than architecture. Navigating back arrives as its own
 * lambda, the way it does on every other screen.
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
)
