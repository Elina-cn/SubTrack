package com.elinacn.subtrack.domain.repository

import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.ExchangeRateTable
import com.elinacn.subtrack.domain.model.ThemeMode
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

    /**
     * Emits the exchange rates in use, again whenever they change.
     *
     * A currency the user has never edited keeps its value from [ExchangeRateTable.Default], so
     * the table handed out is always complete.
     */
    fun observeRates(): Flow<ExchangeRateTable>

    /**
     * Stores one rate, as [ExchangeRateTable.RATE_SCALE]-scaled units of the anchor currency.
     *
     * The caller is responsible for validating the value; the range the arithmetic can carry is
     * [ExchangeRateTable.MIN_RATE] to [ExchangeRateTable.MAX_RATE].
     */
    suspend fun setRate(currency: Currency, scaledRate: Long)

    /** Forgets every edited rate, so the defaults apply again. */
    suspend fun resetRates()

    /** Emits when the rates were last edited, or null when they never have been. */
    fun observeRatesUpdatedAt(): Flow<Long?>

    /** Emits the colour scheme the user asked for, again whenever it changes. */
    fun observeThemeMode(): Flow<ThemeMode>

    /** Stores the colour scheme the user asked for. */
    suspend fun setThemeMode(mode: ThemeMode)

    /**
     * Emits whether the schemes are taken from the wallpaper instead of the app's own palette.
     *
     * Independent of [observeThemeMode] on purpose: the two answer different questions - which
     * hues, and light or dark - and a user who turns the wallpaper colours on may still want to
     * force dark.
     */
    fun observeDynamicColor(): Flow<Boolean>

    /** Stores whether the wallpaper supplies the colours. */
    suspend fun setDynamicColor(enabled: Boolean)
}
