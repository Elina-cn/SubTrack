package com.elinacn.subtrack.ui.settings.rates

import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.ExchangeRateTable
import com.elinacn.subtrack.fake.FakeSettingsRepository
import com.elinacn.subtrack.ui.common.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * The order of writes, and that the boxes and the store never quietly disagree.
 *
 * Phase 16u's tester saved a rate by hand, reset to the defaults, pressed Save again - and was then
 * shown a total that did not match the rate on screen. Each test here is one way 1.0.2 could get
 * there: a second press while a write ran, a reset overtaking a save, a box refilled from a table
 * that had not caught up yet (ARCHITECTURE section 29).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeRatesViewModelWriteTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSettingsRepository
    private lateinit var viewModel: ExchangeRatesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSettingsRepository()
        viewModel = ExchangeRatesViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun twoSavesInARow_theLastOneStays() = runTest {
        collectState()

        edit(Currency.USD, "40")
        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()
        edit(Currency.USD, "41")
        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        assertEquals(410_000L, repository.observeRates().first().rateOf(Currency.USD))
        assertEquals("41", viewModel.uiState.value.rateTexts?.get(Currency.USD))
        assertScreenMatchesStore()
    }

    /** The tester's "pressed it several times": only the first press writes. */
    @Test
    fun saveThreeTimesQuickly_writesOnce() = runTest {
        collectState()
        edit(Currency.USD, "40")

        repeat(3) { viewModel.onEvent(ExchangeRatesEvent.Save) }
        advanceUntilIdle()

        assertEquals(1, repository.rateWrites.size)
        assertScreenMatchesStore()
    }

    @Test
    fun whileAWriteRuns_neitherButtonIsOfferedAndPressesAreRefused() = runTest {
        collectState()
        val gate = CompletableDeferred<Unit>()
        repository.rateWriteGate = gate
        edit(Currency.USD, "40")

        viewModel.onEvent(ExchangeRatesEvent.Save)
        runCurrent()

        assertFalse(viewModel.uiState.value.isSaveEnabled)
        assertFalse(viewModel.uiState.value.isResetEnabled)
        viewModel.onEvent(ExchangeRatesEvent.Save)
        viewModel.onEvent(ExchangeRatesEvent.ConfirmReset)
        gate.complete(Unit)
        advanceUntilIdle()

        // The reset pressed mid-save did not run after it and undo it.
        assertEquals(1, repository.rateWrites.size)
        assertEquals(0, repository.resetCount)
        assertEquals(400_000L, repository.observeRates().first().rateOf(Currency.USD))
        assertTrue(viewModel.uiState.value.isResetEnabled)
    }

    @Test
    fun saveWhileAResetRuns_isRefused() = runTest {
        collectState()
        edit(Currency.USD, "40")
        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.rateWriteGate = gate

        viewModel.onEvent(ExchangeRatesEvent.ConfirmReset)
        runCurrent()
        edit(Currency.USD, "39")
        viewModel.onEvent(ExchangeRatesEvent.Save)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.rateWrites.size)
        assertEquals(1, repository.resetCount)
        assertEquals(
            ExchangeRateTable.Default.rateOf(Currency.USD),
            repository.observeRates().first().rateOf(Currency.USD)
        )
    }

    /**
     * A box typed into while its save is still running holds a change the store has not seen, so
     * it stays - and it is what the next save writes.
     */
    @Test
    fun typedDuringAWrite_isKeptAndSavedNext() = runTest {
        collectState()
        val gate = CompletableDeferred<Unit>()
        repository.rateWriteGate = gate
        edit(Currency.USD, "40")
        viewModel.onEvent(ExchangeRatesEvent.Save)
        runCurrent()

        edit(Currency.USD, "41")
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(400_000L, repository.observeRates().first().rateOf(Currency.USD))
        assertEquals("41", viewModel.uiState.value.rateTexts?.get(Currency.USD))
        assertTrue(viewModel.uiState.value.isSaveEnabled)

        repository.rateWriteGate = null
        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        assertEquals(410_000L, repository.observeRates().first().rateOf(Currency.USD))
        assertScreenMatchesStore()
    }

    @Test
    fun confirmReset_storesTheDefaultsAtOnceAndSaysSo() = runTest {
        collectState()
        edit(Currency.USD, "40")
        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()
        edit(Currency.EUR, "50")

        viewModel.onEvent(ExchangeRatesEvent.ShowResetConfirmation)
        viewModel.onEvent(ExchangeRatesEvent.ConfirmReset)
        advanceUntilIdle()

        assertEquals(1, repository.resetCount)
        assertEquals(RatesNotice.RESTORED, viewModel.uiState.value.notice)
        assertFalse(viewModel.uiState.value.isResetConfirmationVisible)
        // The unsaved EUR text went with the reset; every box is a default again.
        assertEquals(
            mapOf(Currency.USD to "42.85", Currency.EUR to "46.2", Currency.GBP to "53.9"),
            viewModel.uiState.value.rateTexts
        )
        assertEquals(null, viewModel.uiState.value.updatedAt)
        assertFalse(viewModel.uiState.value.isSaveEnabled)
    }

    /** The tester's sequence: by hand, Save, "Reset to defaults", Save. */
    @Test
    fun saveResetSave_leavesTheDefaultsStoredAndShown() = runTest {
        collectState()

        edit(Currency.USD, "39,59")
        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()
        assertScreenMatchesStore()

        viewModel.onEvent(ExchangeRatesEvent.ConfirmReset)
        advanceUntilIdle()
        assertScreenMatchesStore()

        // Untouched after the reset, so there is nothing to save and nothing is written.
        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        assertEquals(1, repository.rateWrites.size)
        assertEquals(
            ExchangeRateTable.Default.rateOf(Currency.USD),
            repository.observeRates().first().rateOf(Currency.USD)
        )
        assertScreenMatchesStore()
    }

    /** Same sequence, but the box is typed into after the reset: what is saved is what it shows. */
    @Test
    fun saveResetTypeSave_storesWhatTheBoxShows() = runTest {
        collectState()
        edit(Currency.USD, "39,59")
        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()
        viewModel.onEvent(ExchangeRatesEvent.ConfirmReset)
        advanceUntilIdle()

        edit(Currency.USD, "42,85")
        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        assertEquals(428_500L, repository.rateWrites.last().getValue(Currency.USD))
        assertEquals("42.85", viewModel.uiState.value.rateTexts?.get(Currency.USD))
        assertScreenMatchesStore()
    }

    @Test
    fun confirmReset_writeFails_reportsItAndLeavesTheBoxesAlone() = runTest {
        collectState()
        edit(Currency.USD, "40")
        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()
        repository.failOnWrite = IOException("disk full")

        viewModel.onEvent(ExchangeRatesEvent.ConfirmReset)
        advanceUntilIdle()

        assertEquals(
            UiText.Resource(R.string.error_rates_save_failed),
            viewModel.uiState.value.errorMessage
        )
        assertEquals("40", viewModel.uiState.value.rateTexts?.get(Currency.USD))
        assertTrue(viewModel.uiState.value.isResetEnabled)
        assertScreenMatchesStore()
    }

    // --- helpers --------------------------------------------------------------------------

    private fun edit(currency: Currency, rawRate: String) {
        viewModel.onEvent(ExchangeRatesEvent.RateEdited(currency, rawRate))
    }

    /** The rule under test: every box says exactly what the store holds. */
    private suspend fun assertScreenMatchesStore() {
        val stored = repository.observeRates().first()
        ExchangeRatesViewModel.editableCurrencies.forEach { currency ->
            assertEquals(
                RateText.format(stored.rateOf(currency)),
                viewModel.uiState.value.rateTexts?.get(currency)
            )
        }
    }

    private fun TestScope.collectState() {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
    }
}
