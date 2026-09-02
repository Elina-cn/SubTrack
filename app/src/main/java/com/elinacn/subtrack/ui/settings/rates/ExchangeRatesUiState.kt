package com.elinacn.subtrack.ui.settings.rates

import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.ui.common.UiText

/**
 * Everything the exchange rate screen draws, in one value.
 *
 * The text being typed lives here rather than in the composable, which is the opposite of what
 * [com.elinacn.subtrack.ui.home.components.AddSubscriptionSheet] does. The reason is that these
 * fields are an edit of stored state, not a blank form: resetting to defaults and saving both
 * have to put canonical values back into the boxes, and that only works if one owner decides what
 * they say. It also means a rotation keeps what was typed without a saver.
 */
data class ExchangeRatesUiState(
    /** What each editable field currently shows, keyed by currency. */
    val drafts: Map<Currency, String> = emptyMap(),
    /** Errors under the field they belong to, never a single banner at the top. */
    val fieldErrors: Map<Currency, UiText> = emptyMap(),
    /** Epoch millis of the last edit, or null when the rates have never been touched. */
    val updatedAt: Long? = null,
    /** True while the "reset to defaults" question is on screen. */
    val isResetConfirmationVisible: Boolean = false,
    /** For failures with no field to point at, such as the store refusing a write. */
    val errorMessage: UiText? = null
)

/** Everything the exchange rate screen can ask for. */
sealed interface ExchangeRatesEvent {

    /** Sent on every keystroke; the ViewModel owns the text. */
    data class RateEdited(val currency: Currency, val rawRate: String) : ExchangeRatesEvent

    /** Validate every field and store them all, or store none and report the bad ones. */
    data object Save : ExchangeRatesEvent

    data object ShowResetConfirmation : ExchangeRatesEvent

    data object DismissResetConfirmation : ExchangeRatesEvent

    /** The user said yes to the reset question. */
    data object ConfirmReset : ExchangeRatesEvent

    data object DismissError : ExchangeRatesEvent
}
