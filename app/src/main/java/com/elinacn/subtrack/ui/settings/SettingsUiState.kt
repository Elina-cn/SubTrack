package com.elinacn.subtrack.ui.settings

import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.ui.common.UiText

/**
 * Everything the settings screen draws, in one value.
 *
 * One setting for now. Editable exchange rates land here in phase 9b-2.
 */
data class SettingsUiState(
    /** The currency the home total is shown in. */
    val mainCurrency: Currency = Currency.Base,
    /** Set when a preference could not be written; drives a snackbar. */
    val errorMessage: UiText? = null
)

/** Everything the settings screen can ask for. */
sealed interface SettingsEvent {

    /** The user picked a currency for totals. */
    data class SelectMainCurrency(val currency: Currency) : SettingsEvent

    data object DismissError : SettingsEvent
}
