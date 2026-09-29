package com.elinacn.subtrack.ui.settings.rates

import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.ui.common.UiText

/**
 * Everything the exchange rate screen draws, in one value.
 *
 * The text being typed lives in the ViewModel rather than in the composable, which is the opposite
 * of what [com.elinacn.subtrack.ui.home.components.AddSubscriptionSheet] does. The reason is that
 * these fields are an edit of stored state, not a blank form: resetting to defaults and saving both
 * have to put canonical values back into the boxes, and that only works if one owner decides what
 * they say. It also means a rotation keeps what was typed without a saver.
 *
 * **[rateTexts] is the one place a box's text comes from, and saving reads the same place**, so
 * the rate that is stored is always the rate that was on screen (ARCHITECTURE section 29).
 */
data class ExchangeRatesUiState(
    /**
     * The text in each box: what the user typed if they touched it, otherwise the stored rate
     * written out by [RateText.format]. Null until the store has answered.
     *
     * Until then nothing on the screen is true - empty boxes and "never edited" are what a user
     * with saved rates would see first - so the screen draws neither (ARCHITECTURE section 29).
     */
    val rateTexts: Map<Currency, String>? = null,
    /** Errors under the field they belong to, never a single banner at the top. */
    val fieldErrors: Map<Currency, UiText> = emptyMap(),
    /** Epoch millis of the last edit, or null when the rates have never been touched. */
    val updatedAt: Long? = null,
    /**
     * Whether Save does anything: only when a box says something the store does not, and never
     * while a write is still running. A button that stays live after saving invites the repeated
     * presses phase 16t saw.
     */
    val isSaveEnabled: Boolean = false,
    /** Whether "reset to defaults" can be asked for: not before the store answers or mid-write. */
    val isResetEnabled: Boolean = false,
    /** True while the "reset to defaults" question is on screen. */
    val isResetConfirmationVisible: Boolean = false,
    /** For failures with no field to point at, such as the store refusing a write. */
    val errorMessage: UiText? = null,
    /**
     * Set when a write has gone through, until the screen has acknowledged it.
     *
     * The screen closes the keyboard, lets go of the focus and says what happened - phase 16t
     * watched a tester press Save again and again because nothing on screen changed.
     */
    val notice: RatesNotice? = null,
    /**
     * The box a rejected save points at: the first one with an error, in screen order. The screen
     * scrolls it into view and focuses it, then hands it back.
     */
    val fieldToFocus: Currency? = null
) {

    /** Whether the store has answered, and the boxes have something true to show. */
    val isLoaded: Boolean get() = rateTexts != null
}

/** What a finished write tells the user. */
enum class RatesNotice {
    /** The boxes were stored. */
    SAVED,

    /** The edited rates were forgotten and the defaults apply again. */
    RESTORED
}

/** Everything the exchange rate screen can ask for. */
sealed interface ExchangeRatesEvent {

    /** Sent on every keystroke; the ViewModel owns the text. */
    data class RateEdited(val currency: Currency, val rawRate: String) : ExchangeRatesEvent

    /** Validate every field and store them all, or store none and report the bad ones. */
    data object Save : ExchangeRatesEvent

    /** The screen has shown [ExchangeRatesUiState.notice]; it can go. */
    data object NoticeShown : ExchangeRatesEvent

    /** The screen has focused [ExchangeRatesUiState.fieldToFocus]. */
    data object FieldFocused : ExchangeRatesEvent

    data object ShowResetConfirmation : ExchangeRatesEvent

    data object DismissResetConfirmation : ExchangeRatesEvent

    /** The user said yes to the reset question; the defaults are stored at once. */
    data object ConfirmReset : ExchangeRatesEvent

    data object DismissError : ExchangeRatesEvent
}
