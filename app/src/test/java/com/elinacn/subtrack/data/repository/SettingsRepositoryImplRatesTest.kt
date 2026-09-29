package com.elinacn.subtrack.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.ExchangeRateTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The rate half of the real store, on a temporary file.
 *
 * Phase 16t found this was the one path no test ran: every ViewModel test goes through the fake,
 * and the fake builds its own [ExchangeRateTable]. These pin what the fake assumes - no file means
 * the defaults, a write reads back, and a save is one write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryImplRatesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun observeRates_noFileAtAll_isTheDefaultTable() = runTest {
        val file = File(temporaryFolder.root, "settings.preferences_pb")
        val repository = SettingsRepositoryImpl(dataStore(file))

        val rates = repository.observeRates().first()

        // A fresh install has no preferences file; 16t found none on disk until something is set.
        assertFalse(file.exists())
        Currency.entries.forEach { currency ->
            assertEquals(ExchangeRateTable.Default.rateOf(currency), rates.rateOf(currency))
        }
        assertNull(repository.observeRatesUpdatedAt().first())
    }

    @Test
    fun setRates_thenObserve_readsBackWhatWasStoredAndStampsTheTime() = runTest {
        val repository = SettingsRepositoryImpl(dataStore())

        repository.setRates(mapOf(Currency.USD to 415_000L, Currency.EUR to 470_000L))

        val rates = repository.observeRates().first()
        assertEquals(415_000L, rates.rateOf(Currency.USD))
        assertEquals(470_000L, rates.rateOf(Currency.EUR))
        // Left out of the write, so still the default.
        assertEquals(ExchangeRateTable.Default.rateOf(Currency.GBP), rates.rateOf(Currency.GBP))
        assertNotNull(repository.observeRatesUpdatedAt().first())
    }

    @Test
    fun setRates_threeRates_areStoredInOneWrite() = runTest {
        val store = CountingDataStore(dataStore())
        val repository = SettingsRepositoryImpl(store)

        repository.setRates(
            mapOf(Currency.USD to 415_000L, Currency.EUR to 470_000L, Currency.GBP to 550_000L)
        )

        // One edit, so no reader can ever see some of them written and the rest not.
        assertEquals(1, store.updates)
        val rates = repository.observeRates().first()
        assertEquals(415_000L, rates.rateOf(Currency.USD))
        assertEquals(470_000L, rates.rateOf(Currency.EUR))
        assertEquals(550_000L, rates.rateOf(Currency.GBP))
    }

    @Test
    fun setRates_twiceInARow_theSecondWins() = runTest {
        val repository = SettingsRepositoryImpl(dataStore())

        repository.setRates(mapOf(Currency.USD to 400_000L))
        repository.setRates(mapOf(Currency.USD to 410_000L))

        assertEquals(410_000L, repository.observeRates().first().rateOf(Currency.USD))
    }

    @Test
    fun resetRates_afterAnEdit_isOneWriteBackToTheDefaultsWithNoTime() = runTest {
        val store = CountingDataStore(dataStore())
        val repository = SettingsRepositoryImpl(store)
        repository.setRates(mapOf(Currency.USD to 415_000L, Currency.GBP to 550_000L))

        repository.resetRates()

        assertEquals(2, store.updates)
        val rates = repository.observeRates().first()
        Currency.entries.forEach { currency ->
            assertEquals(ExchangeRateTable.Default.rateOf(currency), rates.rateOf(currency))
        }
        assertNull(repository.observeRatesUpdatedAt().first())
    }

    @Test
    fun setRates_readBackAfterTheStoreIsClosed_survivesOnDisk() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        SettingsRepositoryImpl(dataStore(file, firstScope)).setRates(mapOf(Currency.USD to 415_000L))
        firstScope.cancel()

        val reopened = SettingsRepositoryImpl(dataStore(file))

        assertEquals(415_000L, reopened.observeRates().first().rateOf(Currency.USD))
    }

    /** Counts transactions, so a test can tell one write from three. */
    private class CountingDataStore(
        private val delegate: DataStore<Preferences>
    ) : DataStore<Preferences> {

        var updates = 0
            private set

        override val data: Flow<Preferences> = delegate.data

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = delegate.updateData(transform).also { updates++ }
    }

    private fun TestScope.dataStore(
        file: File = temporaryFolder.newFile("settings.preferences_pb"),
        scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { file }
    )
}
