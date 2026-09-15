package com.elinacn.subtrack.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Runs against a real store on a temporary file rather than a fake, because the parts worth
 * testing here are the ones the fake cannot have: that a written value survives being read back
 * off disk, and that an unrecognised code does not take the app down with it.
 *
 * No Android framework is involved - the file is produced directly instead of through Context,
 * which is the only thing the production module adds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun observeMainCurrency_nothingStored_isTheDefault() = runTest {
        val repository = SettingsRepositoryImpl(dataStore())

        assertEquals(Currency.TRY, repository.observeMainCurrency().first())
    }

    @Test
    fun setMainCurrency_thenObserve_returnsWhatWasStored() = runTest {
        val repository = SettingsRepositoryImpl(dataStore())

        repository.setMainCurrency(Currency.EUR)

        assertEquals(Currency.EUR, repository.observeMainCurrency().first())
    }

    /**
     * The first store's scope has to be cancelled before the second opens: DataStore refuses two
     * live instances over one file, which is the runtime error ARCHITECTURE section 14 makes
     * Hilt's @Singleton responsible for avoiding in the app.
     */
    @Test
    fun setMainCurrency_readBackAfterTheStoreIsClosed_survivesOnDisk() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        SettingsRepositoryImpl(dataStore(file, firstScope)).setMainCurrency(Currency.GBP)
        firstScope.cancel()

        // A second store over the same file stands in for the next launch of the app.
        val reopened = SettingsRepositoryImpl(dataStore(file))

        assertEquals(Currency.GBP, reopened.observeMainCurrency().first())
    }

    @Test
    fun observeMainCurrency_unknownCode_fallsBackToTheDefault() = runTest {
        val store = dataStore()
        // What a hand-edited file, or one written by a newer build, could contain.
        store.edit { it[stringPreferencesKey("main_currency")] = "XYZ" }

        assertEquals(Currency.TRY, SettingsRepositoryImpl(store).observeMainCurrency().first())
    }

    @Test
    fun observeThemeMode_nothingStored_followsTheSystem() = runTest {
        val repository = SettingsRepositoryImpl(dataStore())

        assertEquals(ThemeMode.SYSTEM, repository.observeThemeMode().first())
    }

    @Test
    fun setThemeMode_thenObserve_returnsWhatWasStored() = runTest {
        val repository = SettingsRepositoryImpl(dataStore())

        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repository.observeThemeMode().first())
    }

    /** Same reopening trick as the currency: this is the setting surviving a relaunch. */
    @Test
    fun setThemeMode_readBackAfterTheStoreIsClosed_survivesOnDisk() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        SettingsRepositoryImpl(dataStore(file, firstScope)).setThemeMode(ThemeMode.LIGHT)
        firstScope.cancel()

        val reopened = SettingsRepositoryImpl(dataStore(file))

        assertEquals(ThemeMode.LIGHT, reopened.observeThemeMode().first())
    }

    @Test
    fun observeThemeMode_unknownName_fallsBackToTheDefault() = runTest {
        val store = dataStore()
        // What a hand-edited file, or one written by a newer build, could contain.
        store.edit { it[stringPreferencesKey("theme_mode")] = "MIDNIGHT" }

        assertEquals(ThemeMode.SYSTEM, SettingsRepositoryImpl(store).observeThemeMode().first())
    }

    /**
     * Absent means off, which is the product decision rather than a convenience: phase 14a's
     * palette and its measured contrasts are what an untouched install gets. See ARCHITECTURE
     * section 23.
     */
    @Test
    fun observeDynamicColor_nothingStored_isOff() = runTest {
        val repository = SettingsRepositoryImpl(dataStore())

        assertFalse(repository.observeDynamicColor().first())
    }

    @Test
    fun setDynamicColor_thenObserve_returnsWhatWasStored() = runTest {
        val repository = SettingsRepositoryImpl(dataStore())

        repository.setDynamicColor(true)

        assertTrue(repository.observeDynamicColor().first())
    }

    @Test
    fun setDynamicColor_readBackAfterTheStoreIsClosed_survivesOnDisk() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        SettingsRepositoryImpl(dataStore(file, firstScope)).setDynamicColor(true)
        firstScope.cancel()

        val reopened = SettingsRepositoryImpl(dataStore(file))

        assertTrue(reopened.observeDynamicColor().first())
    }

    /** One file, four settings, and none of them may write over another's key. */
    @Test
    fun everySetting_writtenTogether_readsBackIndependently() = runTest {
        val repository = SettingsRepositoryImpl(dataStore())

        repository.setMainCurrency(Currency.USD)
        repository.setThemeMode(ThemeMode.DARK)
        repository.setDynamicColor(true)

        assertEquals(Currency.USD, repository.observeMainCurrency().first())
        assertEquals(ThemeMode.DARK, repository.observeThemeMode().first())
        assertTrue(repository.observeDynamicColor().first())
    }

    private fun TestScope.dataStore(
        file: java.io.File = temporaryFolder.newFile("settings.preferences_pb"),
        scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { file }
    )
}
