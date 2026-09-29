package com.elinacn.subtrack.ui.settings.rates

import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.fake.FakeSettingsRepository
import com.elinacn.subtrack.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Loading, saving and refusing. The ordering of writes - two saves, a reset, a press during a
 * write - is in [ExchangeRatesViewModelWriteTest]; the text rules themselves are in [RateTextTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeRatesViewModelTest {

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

    // --- loading --------------------------------------------------------------------------

    @Test
    fun uiState_beforeTheStoreAnswers_showsNoRatesAndOffersNothing() = runTest {
        // Built here, not in setUp: a read queued before the test body would already have run.
        viewModel = ExchangeRatesViewModel(repository)
        val first = viewModel.uiState.value

        // Nothing true to show yet, so nothing is shown (ARCHITECTURE section 29).
        assertNull(first.rateTexts)
        assertFalse(first.isLoaded)
        assertFalse(first.isSaveEnabled)
        assertFalse(first.isResetEnabled)
    }

    @Test
    fun save_beforeTheStoreAnswers_writesNothing() = runTest {
        viewModel = ExchangeRatesViewModel(repository)
        viewModel.onEvent(ExchangeRatesEvent.RateEdited(Currency.USD, "50"))
        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), repository.rateWrites)
    }

    @Test
    fun uiState_nothingStored_showsTheDefaultsWithADot() = runTest {
        collectState()

        // 428500 scaled reads back as "42.85", not "42.8500".
        assertEquals(
            mapOf(Currency.USD to "42.85", Currency.EUR to "46.2", Currency.GBP to "53.9"),
            viewModel.uiState.value.rateTexts
        )
        assertNull(viewModel.uiState.value.updatedAt)
        assertTrue(viewModel.uiState.value.isResetEnabled)
    }

    @Test
    fun uiState_storedRates_areWhatTheBoxesShow() = runTest {
        repository.setRates(mapOf(Currency.USD to 415_000L))
        viewModel = ExchangeRatesViewModel(repository)
        collectState()

        assertEquals("41.5", viewModel.uiState.value.rateTexts?.get(Currency.USD))
        assertEquals(1_000L, viewModel.uiState.value.updatedAt)
    }

    @Test
    fun uiState_theAnchorIsNotEditable() = runTest {
        collectState()

        assertEquals(false, viewModel.uiState.value.rateTexts?.containsKey(Currency.TRY))
        assertTrue(Currency.TRY !in ExchangeRatesViewModel.editableCurrencies)
    }

    // --- when Save is live ----------------------------------------------------------------

    @Test
    fun isSaveEnabled_followsWhetherABoxDiffersFromTheStore() = runTest {
        collectState()
        assertFalse(viewModel.uiState.value.isSaveEnabled)

        edit(Currency.USD, "42.8")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaveEnabled)

        // Typed back to exactly what is stored: nothing left to save.
        edit(Currency.USD, "42.85")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSaveEnabled)
    }

    @Test
    fun save_withNothingChanged_writesNothing() = runTest {
        collectState()

        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), repository.rateWrites)
    }

    // --- saving ---------------------------------------------------------------------------

    @Test
    fun save_writesEveryBoxInOneWriteAndSaysSo() = runTest {
        collectState()
        edit(Currency.USD, "50")

        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        assertEquals(
            listOf(mapOf(Currency.USD to 500_000L, Currency.EUR to 462_000L, Currency.GBP to 539_000L)),
            repository.rateWrites
        )
        assertEquals(1_000L, viewModel.uiState.value.updatedAt)
        assertEquals(RatesNotice.SAVED, viewModel.uiState.value.notice)
        assertFalse(viewModel.uiState.value.isSaveEnabled)
    }

    @Test
    fun save_afterwards_theBoxesShowExactlyWhatIsStored() = runTest {
        collectState()
        edit(Currency.USD, "50,0000")
        edit(Currency.GBP, "55,5")

        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        // The typed text is gone; each box is the stored rate written out again.
        assertEquals("50", viewModel.uiState.value.rateTexts?.get(Currency.USD))
        assertEquals("55.5", viewModel.uiState.value.rateTexts?.get(Currency.GBP))
        assertScreenMatchesStore()
    }

    @Test
    fun noticeShown_clearsTheNotice() = runTest {
        collectState()
        edit(Currency.USD, "50")
        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        viewModel.onEvent(ExchangeRatesEvent.NoticeShown)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun save_writeFails_reportsItWithoutLosingWhatWasTyped() = runTest {
        collectState()
        edit(Currency.USD, "50")
        repository.failOnWrite = IOException("disk full")

        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        assertEquals(
            UiText.Resource(R.string.error_rates_save_failed),
            viewModel.uiState.value.errorMessage
        )
        assertEquals("50", viewModel.uiState.value.rateTexts?.get(Currency.USD))
        assertNull(viewModel.uiState.value.notice)
        // Still unsaved, so still offered - and the write is over, so it can be tried again.
        assertTrue(viewModel.uiState.value.isSaveEnabled)
    }

    // --- refusing -------------------------------------------------------------------------

    @Test
    fun save_oneBadBoxAmongThree_writesNothingAtAll() = runTest {
        collectState()
        edit(Currency.USD, "50")
        edit(Currency.EUR, "0")

        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        // A partial save would leave the user unable to tell which box reached the store.
        assertEquals(emptyList<Any>(), repository.rateWrites)
        assertEquals(
            UiText.Resource(R.string.error_rate_not_positive),
            viewModel.uiState.value.fieldErrors[Currency.EUR]
        )
        assertTrue(Currency.USD !in viewModel.uiState.value.fieldErrors)
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun save_rejected_pointsAtTheFirstBadBoxInScreenOrder() = runTest {
        collectState()
        edit(Currency.GBP, "")
        edit(Currency.EUR, "abc")

        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        assertEquals(Currency.EUR, viewModel.uiState.value.fieldToFocus)

        viewModel.onEvent(ExchangeRatesEvent.FieldFocused)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.fieldToFocus)
    }

    @Test
    fun rateEdited_afterRejection_removesThatBoxsMessage() = runTest {
        collectState()
        edit(Currency.USD, "0")
        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        edit(Currency.USD, "50")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.fieldErrors[Currency.USD])
    }

    // --- helpers --------------------------------------------------------------------------

    private fun edit(currency: Currency, rawRate: String) {
        viewModel.onEvent(ExchangeRatesEvent.RateEdited(currency, rawRate))
    }

    private suspend fun assertScreenMatchesStore() {
        val stored = repository.observeRates().first()
        ExchangeRatesViewModel.editableCurrencies.forEach { currency ->
            assertEquals(
                RateText.format(stored.rateOf(currency)),
                viewModel.uiState.value.rateTexts?.get(currency)
            )
        }
    }

    /** uiState is WhileSubscribed, so it stays cold until something collects it. */
    private fun TestScope.collectState() {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
    }
}
