package com.elinacn.subtrack.ui.settings

import app.cash.turbine.test
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.fake.FakeReminderNotificationStatus
import com.elinacn.subtrack.fake.FakeReminderStateRepository
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
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSettingsRepository
    private lateinit var reminderState: FakeReminderStateRepository
    private lateinit var notificationStatus: FakeReminderNotificationStatus
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSettingsRepository()
        reminderState = FakeReminderStateRepository()
        notificationStatus = FakeReminderNotificationStatus()
        viewModel = buildViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_nothingStored_startsOnTheDefaultCurrency() = runTest {
        viewModel.uiState.test {
            assertEquals(Currency.TRY, awaitItem().mainCurrency)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_storedCurrency_isWhatTheScreenShows() = runTest {
        repository = FakeSettingsRepository(initial = Currency.GBP)
        viewModel = buildViewModel()
        collectState()

        assertEquals(Currency.GBP, viewModel.uiState.value.mainCurrency)
    }

    @Test
    fun selectMainCurrency_writesTheChoiceToTheRepository() = runTest {
        collectState()

        viewModel.onEvent(SettingsEvent.SelectMainCurrency(Currency.EUR))
        advanceUntilIdle()

        assertEquals(listOf(Currency.EUR), repository.writes)
    }

    @Test
    fun selectMainCurrency_afterWriting_theStoredValueDrivesTheState() = runTest {
        collectState()

        viewModel.onEvent(SettingsEvent.SelectMainCurrency(Currency.USD))
        advanceUntilIdle()

        // The state followed the store, not an optimistic local copy.
        assertEquals(Currency.USD, viewModel.uiState.value.mainCurrency)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun selectMainCurrency_writeFails_reportsItAndLeavesTheSelectionAlone() = runTest {
        repository.failOnWrite = IOException("disk full")
        collectState()

        viewModel.onEvent(SettingsEvent.SelectMainCurrency(Currency.USD))
        advanceUntilIdle()

        assertEquals(
            UiText.Resource(R.string.error_setting_save_failed),
            viewModel.uiState.value.errorMessage
        )
        // Nothing was stored, so the chips must still show what is actually saved.
        assertTrue(repository.writes.isEmpty())
        assertEquals(Currency.TRY, viewModel.uiState.value.mainCurrency)
    }

    @Test
    fun dismissError_afterAFailedWrite_clearsTheMessage() = runTest {
        repository.failOnWrite = IOException("disk full")
        collectState()
        viewModel.onEvent(SettingsEvent.SelectMainCurrency(Currency.USD))
        advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.DismissError)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
    }

    /** uiState is WhileSubscribed, so it stays cold until something collects it. */
    private fun buildViewModel() =
        SettingsViewModel(repository, reminderState, notificationStatus)

    private fun TestScope.collectState() {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
    }
}
