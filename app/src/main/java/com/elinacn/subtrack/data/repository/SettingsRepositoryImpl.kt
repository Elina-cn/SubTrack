package com.elinacn.subtrack.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.elinacn.subtrack.domain.model.Currency
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
 * that wrote it.
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
    override fun observeMainCurrency(): Flow<Currency> = dataStore.data
        .catch { failure ->
            if (failure is IOException) emit(emptyPreferences()) else throw failure
        }
        .map { preferences ->
            // fromCode falls back to Currency.Base, so a hand-edited or newer-app value cannot
            // leave the app without a currency to total in.
            preferences[MAIN_CURRENCY]?.let(Currency::fromCode) ?: Currency.Base
        }

    override suspend fun setMainCurrency(currency: Currency) {
        dataStore.edit { preferences -> preferences[MAIN_CURRENCY] = currency.name }
    }

    private companion object {
        val MAIN_CURRENCY = stringPreferencesKey("main_currency")
    }
}
