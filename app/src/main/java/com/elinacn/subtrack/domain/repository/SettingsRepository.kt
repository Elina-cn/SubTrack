package com.elinacn.subtrack.domain.repository

import com.elinacn.subtrack.domain.model.Currency
import kotlinx.coroutines.flow.Flow

/**
 * The only way the rest of the app reaches stored user preferences.
 *
 * Declared here, in domain, so nothing above needs to know DataStore exists - the same split the
 * app already uses for subscriptions.
 */
interface SettingsRepository {

    /** Emits the currency totals are shown in, again whenever it changes. */
    fun observeMainCurrency(): Flow<Currency>

    /** Stores the currency totals should be shown in. */
    suspend fun setMainCurrency(currency: Currency)
}
