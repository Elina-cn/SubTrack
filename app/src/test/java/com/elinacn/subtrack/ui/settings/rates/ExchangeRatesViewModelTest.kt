package com.elinacn.subtrack.ui.settings.rates

import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.ExchangeRateTable
import com.elinacn.subtrack.fake.FakeSettingsRepository
import com.elinacn.subtrack.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

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

    // --- seeding --------------------------------------------------------------------------

    @Test
    fun uiState_nothingStored_seedsTheFieldsFromTheDefaults() = runTest {
        collectState()

        // 428500 scaled reads back as "42.85", not "42.8500".
        assertEquals("42.85", viewModel.uiState.value.drafts[Currency.USD])
        assertEquals("46.2", viewModel.uiState.value.drafts[Currency.EUR])
        assertEquals("53.9", viewModel.uiState.value.drafts[Currency.GBP])
    }

    @Test
    fun uiState_theAnchorIsNotEditable() = runTest {
        collectState()

        assertTrue(Currency.TRY !in viewModel.uiState.value.drafts)
        assertTrue(Currency.TRY !in ExchangeRatesViewModel.editableCurrencies)
    }

    @Test
    fun uiState_neverEdited_reportsNoTimestamp() = runTest {
        collectState()

        assertNull(viewModel.uiState.value.updatedAt)
    }

    // --- saving ---------------------------------------------------------------------------

    @Test
    fun save_validRates_writesEveryFieldAndStampsTheTime() = runTest {
        collectState()
        edit(Currency.USD, "50")

        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        assertEquals(500_000L, repository.rateWrites.single { it.first == Currency.USD }.second)
        assertEquals(1_000L, viewModel.uiState.value.updatedAt)
    }

    @Test
    fun save_thenReseed_showsWhatWasActuallyStored() = runTest {
        collectState()
        edit(Currency.USD, "50,0000")

        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        // The draft is dropped after a save, so the box shows the canonical rendering.
        assertEquals("50", viewModel.uiState.value.drafts[Currency.USD])
    }

    @Test
    fun save_commaAndDotSeparator_produceTheSameRate() = runTest {
        collectState()
        edit(Currency.USD, "50,25")
        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()
        val comma = repository.rateWrites.last { it.first == Currency.USD }.second

        edit(Currency.USD, "50.25")
        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()
        val dot = repository.rateWrites.last { it.first == Currency.USD }.second

        assertEquals(comma, dot)
        assertEquals(502_500L, dot)
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
        assertEquals("50", viewModel.uiState.value.drafts[Currency.USD])
    }

    // --- validation -----------------------------------------------------------------------

    @Test
    fun save_zeroRate_isRejectedAndNothingIsWritten() =
        assertRejected(rawRate = "0", expected = R.string.error_rate_not_positive)

    @Test
    fun save_negativeRate_isRejected() =
        assertRejected(rawRate = "-5", expected = R.string.error_rate_not_positive)

    @Test
    fun save_emptyRate_isRejected() =
        assertRejected(rawRate = "", expected = R.string.error_rate_empty)

    @Test
    fun save_nonNumericRate_isRejected() =
        assertRejected(rawRate = "abc", expected = R.string.error_rate_invalid)

    @Test
    fun save_fiveDecimals_isRejected() =
        assertRejected(rawRate = "1,23456", expected = R.string.error_rate_too_many_decimals)

    @Test
    fun save_fourDecimals_isAccepted() = runTest {
        collectState()
        edit(Currency.USD, "1.2345")

        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        assertEquals(12_345L, repository.rateWrites.single { it.first == Currency.USD }.second)
    }

    @Test
    fun save_trailingZerosBeyondFourDecimals_areAccepted() = runTest {
        collectState()
        // Five written decimals but only four that mean anything.
        edit(Currency.USD, "42.85000")

        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        assertEquals(428_500L, repository.rateWrites.single { it.first == Currency.USD }.second)
    }

    @Test
    fun save_theSmallestRepresentableRate_isAccepted() = runTest {
        collectState()
        edit(Currency.USD, "0.0001")

        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        assertEquals(
            ExchangeRateTable.MIN_RATE,
            repository.rateWrites.single { it.first == Currency.USD }.second
        )
    }

    @Test
    fun save_rateAtTheCeiling_isAccepted() = runTest {
        collectState()
        edit(Currency.USD, "1000")

        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        assertEquals(
            ExchangeRateTable.MAX_RATE,
            repository.rateWrites.single { it.first == Currency.USD }.second
        )
    }

    @Test
    fun save_aboveTheCeiling_isRejectedAndCarriesTheLimit() = runTest {
        collectState()
        edit(Currency.USD, "1000.0001")

        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        val error = viewModel.uiState.value.fieldErrors[Currency.USD] as UiText.Resource
        assertEquals(R.string.error_rate_too_large, error.id)
        assertEquals(listOf("1000"), error.args)
        assertTrue(repository.rateWrites.isEmpty())
    }

    @Test
    fun save_oneBadFieldAmongThree_writesNothingAtAll() = runTest {
        collectState()
        edit(Currency.USD, "50")
        edit(Currency.EUR, "0")

        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        // A partial save would leave the user unable to tell which box reached the store.
        assertTrue(repository.rateWrites.isEmpty())
        assertTrue(Currency.EUR in viewModel.uiState.value.fieldErrors)
        assertTrue(Currency.USD !in viewModel.uiState.value.fieldErrors)
    }

    @Test
    fun rateEdited_afterRejection_removesThatFieldsMessage() = runTest {
        collectState()
        edit(Currency.USD, "0")
        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        edit(Currency.USD, "50")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.fieldErrors[Currency.USD])
    }

    // --- resetting ------------------------------------------------------------------------

    @Test
    fun confirmReset_returnsTheTableToTheDefaults() = runTest {
        collectState()
        edit(Currency.USD, "50")
        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        viewModel.onEvent(ExchangeRatesEvent.ConfirmReset)
        advanceUntilIdle()

        assertEquals(1, repository.resetCount)
        assertEquals("42.85", viewModel.uiState.value.drafts[Currency.USD])
        assertNull(viewModel.uiState.value.updatedAt)
    }

    @Test
    fun resetConfirmation_canBeDismissedWithoutResetting() = runTest {
        collectState()

        viewModel.onEvent(ExchangeRatesEvent.ShowResetConfirmation)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isResetConfirmationVisible)

        viewModel.onEvent(ExchangeRatesEvent.DismissResetConfirmation)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isResetConfirmationVisible)
        assertEquals(0, repository.resetCount)
    }

    // --- helpers --------------------------------------------------------------------------

    private fun assertRejected(rawRate: String, expected: Int) = runTest {
        collectState()
        edit(Currency.USD, rawRate)

        viewModel.onEvent(ExchangeRatesEvent.Save)
        advanceUntilIdle()

        assertEquals(UiText.Resource(expected), viewModel.uiState.value.fieldErrors[Currency.USD])
        assertTrue(repository.rateWrites.isEmpty())
    }

    private fun edit(currency: Currency, rawRate: String) {
        viewModel.onEvent(ExchangeRatesEvent.RateEdited(currency, rawRate))
    }

    /** uiState is WhileSubscribed, so it stays cold until something collects it. */
    private fun TestScope.collectState() {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
    }
}
