package com.elinacn.subtrack.ui.settings.rates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.ExchangeRateTable
import com.elinacn.subtrack.domain.repository.SettingsRepository
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
import javax.inject.Inject

/** Holds the exchange rate screen's state and turns its events into preference writes. */
@HiltViewModel
class ExchangeRatesViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    private val screenState = MutableStateFlow(ScreenState())

    /**
     * Drafts start as null and are filled from the stored table on the first emission. Saving and
     * resetting both set them back to null, so the boxes re-seed from whatever is actually stored
     * instead of keeping the text that produced it.
     */
    val uiState: StateFlow<ExchangeRatesUiState> = combine(
        repository.observeRates(),
        repository.observeRatesUpdatedAt(),
        screenState
    ) { rates, updatedAt, screen ->
        ExchangeRatesUiState(
            drafts = screen.drafts ?: editableCurrencies.associateWith { rates.rateOf(it).asText() },
            fieldErrors = screen.fieldErrors,
            updatedAt = updatedAt,
            isResetConfirmationVisible = screen.isResetConfirmationVisible,
            errorMessage = screen.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ExchangeRatesUiState()
    )

    /** Single entry point for everything the screen can ask for. */
    fun onEvent(event: ExchangeRatesEvent) {
        when (event) {
            is ExchangeRatesEvent.RateEdited -> screenState.update { state ->
                val drafts = state.drafts ?: uiState.value.drafts
                state.copy(
                    drafts = drafts + (event.currency to event.rawRate),
                    // Drop the error as soon as the field changes; leaving it up would keep
                    // contradicting what is now on screen.
                    fieldErrors = state.fieldErrors - event.currency
                )
            }

            ExchangeRatesEvent.Save -> save()

            ExchangeRatesEvent.ShowResetConfirmation -> screenState.update {
                it.copy(isResetConfirmationVisible = true)
            }

            ExchangeRatesEvent.DismissResetConfirmation -> screenState.update {
                it.copy(isResetConfirmationVisible = false)
            }

            ExchangeRatesEvent.ConfirmReset -> resetRates()

            ExchangeRatesEvent.DismissError -> screenState.update { it.copy(errorMessage = null) }
        }
    }

    /**
     * Every field is validated before anything is written. A partial save would leave the user
     * looking at three boxes with no way to tell which of them reached the store.
     */
    private fun save() {
        // screenState first: a Save arriving in the same frame as the last keystroke must see that
        // keystroke, and uiState only catches up once the combine re-emits.
        val drafts = screenState.value.drafts ?: uiState.value.drafts
        val errors = mutableMapOf<Currency, UiText>()
        val parsed = mutableMapOf<Currency, Long>()

        editableCurrencies.forEach { currency ->
            when (val result = parseRate(drafts[currency].orEmpty())) {
                is RateResult.Valid -> parsed[currency] = result.scaledRate
                is RateResult.Invalid -> errors[currency] = result.reason
            }
        }

        if (errors.isNotEmpty()) {
            screenState.update { it.copy(fieldErrors = errors) }
            return
        }

        viewModelScope.launch {
            try {
                parsed.forEach { (currency, scaledRate) -> repository.setRate(currency, scaledRate) }
                // Clearing the drafts re-seeds the boxes from the store, so what is on screen after
                // a save is the value that was actually written.
                screenState.update { it.copy(drafts = null, fieldErrors = emptyMap()) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                screenState.update { it.copy(errorMessage = UiText.Resource(R.string.error_rates_save_failed)) }
            }
        }
    }

    private fun resetRates() {
        screenState.update { it.copy(isResetConfirmationVisible = false) }
        viewModelScope.launch {
            try {
                repository.resetRates()
                screenState.update { it.copy(drafts = null, fieldErrors = emptyMap()) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                screenState.update { it.copy(errorMessage = UiText.Resource(R.string.error_rates_save_failed)) }
            }
        }
    }

    /**
     * Reads what the user typed into a scaled rate, with the same care as the price field.
     *
     * BigDecimal rather than Double: a rate multiplies every amount that passes through it, so an
     * inexact reading spreads a proportional error across the whole total rather than losing a
     * kuruş once.
     */
    private fun parseRate(rawRate: String): RateResult {
        val normalized = rawRate.trim().replace(',', '.')
        if (normalized.isEmpty()) {
            return RateResult.Invalid(UiText.Resource(R.string.error_rate_empty))
        }
        val amount = normalized.toBigDecimalOrNull()
            ?: return RateResult.Invalid(UiText.Resource(R.string.error_rate_invalid))
        if (amount.signum() <= 0) {
            // The hard one: CurrencyConverter divides by the target rate, so a zero here is a
            // division by zero rather than a merely odd number.
            return RateResult.Invalid(UiText.Resource(R.string.error_rate_not_positive))
        }
        // Trailing zeros do not count: "42.8500" is two decimals written long.
        if (amount.stripTrailingZeros().scale() > RATE_DECIMAL_DIGITS) {
            return RateResult.Invalid(UiText.Resource(R.string.error_rate_too_many_decimals))
        }
        if (amount > MAX_RATE_UNITS) {
            // The limit is handed to the message instead of being written into it, so the two
            // cannot drift apart when the ceiling changes.
            return RateResult.Invalid(
                UiText.Resource(R.string.error_rate_too_large, listOf(MAX_RATE_UNITS.toPlainString()))
            )
        }
        val scaled = amount.movePointRight(RATE_DECIMAL_DIGITS).toLong()
        if (scaled < ExchangeRateTable.MIN_RATE) {
            // Unreachable given the two checks above - a positive number with at most four
            // decimals is at least one scale unit. Kept because what it protects is a correctness
            // requirement, not a nicety: nothing below this line may hand a zero divisor onward.
            return RateResult.Invalid(UiText.Resource(R.string.error_rate_not_positive))
        }
        return RateResult.Valid(scaled)
    }

    /** Renders a stored rate the way the field should show it: "42.85", not "42.8500". */
    private fun Long.asText(): String =
        BigDecimal.valueOf(this, RATE_DECIMAL_DIGITS).stripTrailingZeros().toPlainString()

    private data class ScreenState(
        val drafts: Map<Currency, String>? = null,
        val fieldErrors: Map<Currency, UiText> = emptyMap(),
        val isResetConfirmationVisible: Boolean = false,
        val errorMessage: UiText? = null
    )

    private sealed interface RateResult {
        data class Valid(val scaledRate: Long) : RateResult
        data class Invalid(val reason: UiText) : RateResult
    }

    companion object {
        /** Everything except the anchor, which is fixed at one and not editable. */
        val editableCurrencies: List<Currency> = Currency.entries.filter { it != Currency.Base }

        /** Four, because that is the scale the rates are stored at; a fifth cannot be kept. */
        const val RATE_DECIMAL_DIGITS = 4

        private const val STOP_TIMEOUT_MS = 5_000L

        /** [ExchangeRateTable.MAX_RATE] expressed in whole units, for comparing against input. */
        private val MAX_RATE_UNITS: BigDecimal =
            BigDecimal.valueOf(ExchangeRateTable.MAX_RATE, RATE_DECIMAL_DIGITS).stripTrailingZeros()
    }
}
