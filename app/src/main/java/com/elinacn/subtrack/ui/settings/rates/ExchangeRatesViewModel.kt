package com.elinacn.subtrack.ui.settings.rates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.repository.SettingsRepository
import com.elinacn.subtrack.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the exchange rate screen's state and turns its events into preference writes.
 *
 * **The store is read, not watched** - once when the screen opens and again after each of its own
 * writes - the same choice the edit screen makes for its row (ARCHITECTURE section 22). This screen
 * is the only writer of the rates, so nothing else can change them underneath it, and reading back
 * after a write means the boxes show what the store answered rather than what was sent.
 *
 * Watching is what 1.0.2 did, and it let the boxes lag the store: a save or reset dropped the
 * typed text at once, and until the store's next emission reached the screen the boxes were filled
 * from the table as it was *before* the write. A Save pressed in that gap stored the old numbers
 * again (ARCHITECTURE section 29).
 *
 * **One write at a time.** A write sets [ScreenState.isWriting] before it starts and every other
 * write is refused until it has finished and been read back; events arrive on the main thread, so
 * the check and the set cannot interleave.
 */
@HiltViewModel
class ExchangeRatesViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    private val screenState = MutableStateFlow(ScreenState())

    /** What the screen draws, derived from the one state the save path reads too. */
    val uiState: StateFlow<ExchangeRatesUiState> = screenState
        .map { it.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ExchangeRatesUiState()
        )

    init {
        viewModelScope.launch {
            val stored = readStore()
            screenState.update { it.copy(stored = stored) }
        }
    }

    /** Single entry point for everything the screen can ask for. */
    fun onEvent(event: ExchangeRatesEvent) {
        when (event) {
            is ExchangeRatesEvent.RateEdited -> screenState.update { state ->
                state.copy(
                    drafts = state.drafts + (event.currency to event.rawRate),
                    // Drop the error as soon as the field changes; leaving it up would keep
                    // contradicting what is now on screen.
                    fieldErrors = state.fieldErrors - event.currency
                )
            }

            ExchangeRatesEvent.Save -> save()

            ExchangeRatesEvent.NoticeShown -> screenState.update { it.copy(notice = null) }

            ExchangeRatesEvent.FieldFocused -> screenState.update { it.copy(fieldToFocus = null) }

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
     * Every box is read before anything is written, and then all of them are written in one go. A
     * partial save would leave the user looking at three boxes with no way to tell which of them
     * reached the store.
     *
     * Every box is parsed from its text, touched or not. An untouched box holds the stored rate
     * written out by [RateText.format], which reads back exactly, so this costs nothing - and it
     * means there is no second source a saved value could come from.
     */
    private fun save() {
        val state = screenState.value
        if (!state.canSave()) return
        val texts = state.rateTexts() ?: return
        val errors = mutableMapOf<Currency, UiText>()
        val parsed = mutableMapOf<Currency, Long>()

        texts.forEach { (currency, text) ->
            when (val result = RateText.parse(text)) {
                is RateResult.Valid -> parsed[currency] = result.scaledRate
                is RateResult.Invalid -> errors[currency] = result.reason
            }
        }

        if (errors.isNotEmpty()) {
            screenState.update {
                it.copy(
                    fieldErrors = errors,
                    // Screen order, so the one pointed at is the first the user would reach.
                    fieldToFocus = editableCurrencies.firstOrNull { currency -> currency in errors }
                )
            }
            return
        }

        write(draftsAtStart = state.drafts, notice = RatesNotice.SAVED) {
            repository.setRates(parsed)
        }
    }

    /** Stores the defaults straight away; there is no second step for the user to forget. */
    private fun resetRates() {
        screenState.update { it.copy(isResetConfirmationVisible = false) }
        val state = screenState.value
        if (state.stored == null || state.isWriting) return

        write(draftsAtStart = state.drafts, notice = RatesNotice.RESTORED) {
            repository.resetRates()
        }
    }

    /**
     * Runs one write and reads the store back afterwards.
     *
     * The drafts that were in the boxes when the write started are dropped, so those boxes show the
     * stored value again. A box typed into while the write was running keeps its text - it is a
     * change the store has not seen, and throwing it away would be the silent loss this screen
     * exists to prevent.
     */
    private fun write(
        draftsAtStart: Map<Currency, String>,
        notice: RatesNotice,
        block: suspend () -> Unit
    ) {
        screenState.update { it.copy(isWriting = true) }
        viewModelScope.launch {
            try {
                block()
                val stored = readStore()
                screenState.update { state ->
                    state.copy(
                        stored = stored,
                        drafts = state.drafts.filter { (currency, text) ->
                            draftsAtStart[currency] != text
                        },
                        fieldErrors = emptyMap(),
                        notice = notice,
                        isWriting = false
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                screenState.update {
                    it.copy(
                        errorMessage = UiText.Resource(R.string.error_rates_save_failed),
                        isWriting = false
                    )
                }
            }
        }
    }

    /** One read of the rates and their edit time, as the store holds them now. */
    private suspend fun readStore(): StoredRates {
        val table = repository.observeRates().first()
        return StoredRates(
            rates = editableCurrencies.associateWith { table.rateOf(it) },
            updatedAt = repository.observeRatesUpdatedAt().first()
        )
    }

    private data class StoredRates(val rates: Map<Currency, Long>, val updatedAt: Long?)

    private data class ScreenState(
        /** What the store held at the last read; null until the first read has answered. */
        val stored: StoredRates? = null,
        /** What the user has typed, only for the boxes they touched. */
        val drafts: Map<Currency, String> = emptyMap(),
        val fieldErrors: Map<Currency, UiText> = emptyMap(),
        val isWriting: Boolean = false,
        val isResetConfirmationVisible: Boolean = false,
        val errorMessage: UiText? = null,
        val notice: RatesNotice? = null,
        val fieldToFocus: Currency? = null
    ) {

        /** The text in every box - the one source for both the screen and [save]. */
        fun rateTexts(): Map<Currency, String>? = stored?.let { store ->
            editableCurrencies.associateWith { currency ->
                drafts[currency] ?: RateText.format(store.rates.getValue(currency))
            }
        }

        /** A box counts as changed when its text is not how the stored rate is written. */
        fun canSave(): Boolean {
            val store = stored ?: return false
            return !isWriting && drafts.any { (currency, text) ->
                text != RateText.format(store.rates.getValue(currency))
            }
        }

        fun toUiState() = ExchangeRatesUiState(
            rateTexts = rateTexts(),
            fieldErrors = fieldErrors,
            updatedAt = stored?.updatedAt,
            isSaveEnabled = canSave(),
            isResetEnabled = stored != null && !isWriting,
            isResetConfirmationVisible = isResetConfirmationVisible,
            errorMessage = errorMessage,
            notice = notice,
            fieldToFocus = fieldToFocus
        )
    }

    companion object {
        /** Everything except the anchor, which is fixed at one and not editable. */
        val editableCurrencies: List<Currency> = Currency.entries.filter { it != Currency.Base }

        /** Four, because that is the scale the rates are stored at; a fifth cannot be kept. */
        const val RATE_DECIMAL_DIGITS = 4

        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
