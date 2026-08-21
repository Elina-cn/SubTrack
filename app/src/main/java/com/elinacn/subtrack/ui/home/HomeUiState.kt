package com.elinacn.subtrack.ui.home

import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription

/**
 * Everything the home screen draws, in one value.
 *
 * The screen renders this and nothing else - no second source of truth, no computation of its own.
 * An error field arrives in phase 6, together with input validation that can produce one.
 */
data class HomeUiState(
    val subscriptions: List<Subscription> = emptyList(),
    val monthlyTotal: Money = Money.ZERO,
    val isLoading: Boolean = true
)

/** Everything the home screen can ask for. One channel instead of a lambda per action. */
sealed interface HomeEvent {

    /** Remove the subscription with this id. */
    data class Delete(val id: Long) : HomeEvent

    /** Store a new subscription. The price arrives as typed; parsing belongs to the ViewModel. */
    data class Save(val name: String, val rawPrice: String) : HomeEvent
}
