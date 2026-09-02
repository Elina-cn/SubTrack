package com.elinacn.subtrack.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.ExchangeRateTable
import com.elinacn.subtrack.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

/**
 * Preference-backed settings, one key per setting.
 *
 * The currency is stored as its ISO 4217 code rather than an ordinal: an ordinal would silently
 * change meaning the day a constant is inserted into [Currency], and the file outlives the build
 * that wrote it. Rates are keyed by that same code for the same reason.
 */
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    /**
     * A failed read falls back to the defaults instead of killing the collector.
     *
     * Only [IOException] is caught - that is the file being unreadable, which the user can do
     * nothing about and which the defaults cover. Anything else is a bug and is rethrown, so it
     * is not hidden the way ARCHITECTURE section 9 forbids.
     */
    override fun observeMainCurrency(): Flow<Currency> = readPreferences()
        .map { preferences ->
            // fromCode falls back to Currency.Base, so a hand-edited or newer-app value cannot
            // leave the app without a currency to total in.
            preferences[MAIN_CURRENCY]?.let(Currency::fromCode) ?: Currency.Base
        }

    override suspend fun setMainCurrency(currency: Currency) {
        dataStore.edit { preferences -> preferences[MAIN_CURRENCY] = currency.name }
    }

    /**
     * Only the currencies the user has actually edited are read out; the rest are filled in by
     * [ExchangeRateTable.of] from the defaults. A half-written file therefore still yields a
     * complete table rather than an unpriced currency.
     */
    override fun observeRates(): Flow<ExchangeRateTable> = readPreferences()
        .map { preferences ->
            ExchangeRateTable.of(
                Currency.entries
                    .mapNotNull { currency ->
                        preferences[rateKey(currency)]?.let { currency to it }
                    }
                    .toMap()
            )
        }

    override suspend fun setRate(currency: Currency, scaledRate: Long) {
        dataStore.edit { preferences ->
            preferences[rateKey(currency)] = scaledRate
            // Written in the same edit as the rate itself, so the two can never disagree about
            // whether anything has been edited.
            preferences[RATES_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    /**
     * Removes the keys rather than writing [ExchangeRateTable.Default] into them.
     *
     * The difference shows up later: if a future release ships better default rates, a user who
     * reset gets those, whereas one whose reset had written today's numbers would be stuck with
     * them forever. See ARCHITECTURE section 15.
     */
    override suspend fun resetRates() {
        dataStore.edit { preferences ->
            Currency.entries.forEach { currency -> preferences.remove(rateKey(currency)) }
            preferences.remove(RATES_UPDATED_AT)
        }
    }

    override fun observeRatesUpdatedAt(): Flow<Long?> = readPreferences()
        .map { preferences -> preferences[RATES_UPDATED_AT] }

    private fun readPreferences(): Flow<Preferences> = dataStore.data
        .catch { failure ->
            if (failure is IOException) emit(emptyPreferences()) else throw failure
        }

    private companion object {
        val MAIN_CURRENCY = stringPreferencesKey("main_currency")

        val RATES_UPDATED_AT = longPreferencesKey("rates_updated_at")

        /** One key per currency, named by ISO code so the file stays readable. */
        fun rateKey(currency: Currency) = longPreferencesKey("rate_${currency.name}")
    }
}
