package com.elinacn.subtrack.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.domain.model.sum
import com.elinacn.subtrack.domain.repository.SubscriptionRepository
import com.elinacn.subtrack.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/** Holds the home screen's state and turns its events into repository calls. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SubscriptionRepository
) : ViewModel() {

    /** Everything that is not stored: sheet visibility, validation errors, the pending undo. */
    private val screenState = MutableStateFlow(ScreenState())

    /**
     * The single source of truth for the screen.
     *
     * combine merges the stored list with the state above, and stateIn turns the result into a hot
     * StateFlow that remembers its last value, so a recomposing screen reads it without re-running
     * the query. WhileSubscribed(5_000) keeps the query alive for five seconds after the last
     * collector leaves: a rotation tears the activity down and rebuilds it well inside that window,
     * so the database observer survives and the list does not blink.
     */
    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeAll(),
        screenState
    ) { subscriptions, screen ->
        HomeUiState(
            subscriptions = subscriptions,
            monthlyTotal = subscriptions.map { it.price }.sum(),
            isLoading = false,
            isAddSheetOpen = screen.isAddSheetOpen,
            nameError = screen.nameError,
            priceError = screen.priceError,
            errorMessage = screen.errorMessage,
            pendingUndo = screen.pendingUndo
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HomeUiState()
    )

    /** Single entry point for everything the screen can ask for. */
    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.OpenAddSheet -> screenState.update {
                it.copy(isAddSheetOpen = true, nameError = null, priceError = null)
            }

            HomeEvent.DismissAddSheet -> screenState.update {
                it.copy(isAddSheetOpen = false, nameError = null, priceError = null)
            }

            is HomeEvent.Save -> save(event.name, event.rawPrice)

            HomeEvent.ClearNameError -> screenState.update { it.copy(nameError = null) }

            HomeEvent.ClearPriceError -> screenState.update { it.copy(priceError = null) }

            is HomeEvent.Delete -> delete(event.id)

            HomeEvent.UndoDelete -> undoDelete()

            HomeEvent.DismissUndo -> screenState.update { it.copy(pendingUndo = null) }

            HomeEvent.DismissError -> screenState.update { it.copy(errorMessage = null) }
        }
    }

    /**
     * Validates first and only writes if everything checks out. The sheet stays open on a bad
     * entry - closing it would throw away what the user typed along with the explanation.
     */
    private fun save(name: String, rawPrice: String) {
        val trimmedName = name.trim()
        val nameError = if (trimmedName.isEmpty()) UiText.Resource(R.string.error_name_empty) else null
        val priceResult = parsePrice(rawPrice)

        if (nameError != null || priceResult is PriceResult.Invalid) {
            screenState.update {
                it.copy(
                    nameError = nameError,
                    priceError = (priceResult as? PriceResult.Invalid)?.reason
                )
            }
            return
        }

        val price = (priceResult as PriceResult.Valid).money
        viewModelScope.launch {
            try {
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
                screenState.update {
                    it.copy(isAddSheetOpen = false, nameError = null, priceError = null)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                // Never swallowed: the sheet stays open and the reason reaches the screen.
                screenState.update { it.copy(errorMessage = failure.asMessage(R.string.error_save_failed)) }
            }
        }
    }

    /** Reads the row before removing it, so undo has something to put back. */
    private fun delete(id: Long) {
        viewModelScope.launch {
            try {
                val removed = repository.getById(id) ?: return@launch
                repository.deleteById(id)
                screenState.update { it.copy(pendingUndo = removed) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                screenState.update { it.copy(errorMessage = failure.asMessage(R.string.error_delete_failed)) }
            }
        }
    }

    /**
     * Puts the row back with its original id, so it lands in the same place in the list rather
     * than jumping to the end.
     */
    private fun undoDelete() {
        val restored = screenState.value.pendingUndo ?: return
        screenState.update { it.copy(pendingUndo = null) }
        viewModelScope.launch {
            try {
                repository.insert(restored)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                screenState.update { it.copy(errorMessage = failure.asMessage(R.string.error_save_failed)) }
            }
        }
    }

    /**
     * Reads what the user typed into whole minor units.
     *
     * BigDecimal, never Double: "159,99" has to come back out as exactly 15999 kuruş, and binary
     * floating point cannot promise that.
     *
     * Both the ceiling and the decimal count are checked before converting, so nothing is ever
     * quietly reshaped on the way to storage.
     */
    private fun parsePrice(rawPrice: String): PriceResult {
        val normalized = rawPrice.trim().replace(',', '.')
        if (normalized.isEmpty()) {
            return PriceResult.Invalid(UiText.Resource(R.string.error_price_empty))
        }
        val amount = normalized.toBigDecimalOrNull()
            ?: return PriceResult.Invalid(UiText.Resource(R.string.error_price_invalid))
        if (amount.signum() <= 0) {
            return PriceResult.Invalid(UiText.Resource(R.string.error_price_not_positive))
        }
        if (amount > MAX_PRICE) {
            // The limit is handed to the message instead of being written into it, so the two
            // cannot drift apart when the ceiling changes.
            return PriceResult.Invalid(
                UiText.Resource(R.string.error_price_too_large, listOf(MAX_PRICE.toLong()))
            )
        }
        // Trailing zeros do not count: "159.990" is two decimals written long, "159.999" is three.
        if (amount.stripTrailingZeros().scale() > MINOR_UNIT_DIGITS) {
            return PriceResult.Invalid(UiText.Resource(R.string.error_price_too_many_decimals))
        }
        val cents = amount
            .movePointRight(MINOR_UNIT_DIGITS)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
        return PriceResult.Valid(Money(cents))
    }

    /** Falls back to a generic line when the exception has nothing readable to say. */
    private fun Exception.asMessage(fallback: Int): UiText =
        message?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) } ?: UiText.Resource(fallback)

    private data class ScreenState(
        val isAddSheetOpen: Boolean = false,
        val nameError: UiText? = null,
        val priceError: UiText? = null,
        val errorMessage: UiText? = null,
        val pendingUndo: Subscription? = null
    )

    private sealed interface PriceResult {
        data class Valid(val money: Money) : PriceResult
        data class Invalid(val reason: UiText) : PriceResult
    }

    private companion object {
        /** Outlives a configuration change, expires on a real departure. */
        const val STOP_TIMEOUT_MS = 5_000L

        /** Phase 9 replaces this with a per-subscription choice. */
        const val DEFAULT_CURRENCY = "TRY"

        const val MINOR_UNIT_DIGITS = 2

        /**
         * A product ceiling, not a Long limit.
         *
         * Long would not complain until roughly 92 quadrillion kuruş, so guarding against overflow
         * catches nothing a person could plausibly type. One million per billing period is already
         * three orders of magnitude above the priciest real subscription, and leaves room for
         * weaker currencies when phase 9 adds the choice - while still rejecting a slipped keypress
         * that adds digits.
         */
        val MAX_PRICE: BigDecimal = BigDecimal("1000000")
    }
}
