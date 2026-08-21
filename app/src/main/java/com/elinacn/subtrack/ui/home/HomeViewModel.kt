package com.elinacn.subtrack.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.domain.model.sum
import com.elinacn.subtrack.domain.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.RoundingMode
import javax.inject.Inject

/** Holds the home screen's state and turns its events into repository calls. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SubscriptionRepository
) : ViewModel() {

    /**
     * The single source of truth for the screen.
     *
     * stateIn turns the repository's cold Flow into a hot StateFlow that remembers its last value,
     * so a recomposing screen reads it without re-running the query. WhileSubscribed(5_000) keeps
     * the query alive for five seconds after the last collector leaves: a rotation tears the
     * activity down and rebuilds it well inside that window, so the database observer survives and
     * the list does not blink. Leave the app for good and it is released.
     */
    val uiState: StateFlow<HomeUiState> = repository.observeAll()
        .map { subscriptions ->
            HomeUiState(
                subscriptions = subscriptions,
                monthlyTotal = subscriptions.map { it.price }.sum(),
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HomeUiState()
        )

    /** Single entry point for everything the screen can ask for. */
    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.Delete -> viewModelScope.launch { repository.deleteById(event.id) }
            is HomeEvent.Save -> viewModelScope.launch { save(event.name, event.rawPrice) }
        }
    }

    /**
     * Bad input is dropped without a word for now. Phase 6 adds validation that can explain
     * itself; saying nothing is the honest placeholder until then, not the intended behaviour.
     */
    private suspend fun save(name: String, rawPrice: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        val price = parsePriceOrNull(rawPrice) ?: return

        repository.insert(
            Subscription(
                id = 0,
                name = trimmedName,
                price = price,
                currencyCode = DEFAULT_CURRENCY,
                billingPeriod = BillingPeriod.MONTHLY,
                nextPaymentDate = null,
                category = SubscriptionCategory.OTHER,
                iconKey = null,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Reads what the user typed into whole minor units.
     *
     * BigDecimal, never Double: "159,99" has to come back out as exactly 15999 kuruş, and binary
     * floating point cannot promise that.
     */
    private fun parsePriceOrNull(rawPrice: String): Money? {
        val normalized = rawPrice.trim().replace(',', '.')
        val amount = normalized.toBigDecimalOrNull() ?: return null
        if (amount.signum() < 0) return null
        val cents = amount
            .movePointRight(MINOR_UNIT_DIGITS)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
        return Money(cents)
    }

    private companion object {
        /** Outlives a configuration change, expires on a real departure. */
        const val STOP_TIMEOUT_MS = 5_000L

        /** Phase 9 replaces this with a per-subscription choice. */
        const val DEFAULT_CURRENCY = "TRY"

        const val MINOR_UNIT_DIGITS = 2
    }
}
