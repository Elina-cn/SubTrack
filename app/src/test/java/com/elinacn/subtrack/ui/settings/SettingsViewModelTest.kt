package com.elinacn.subtrack.ui.settings

import app.cash.turbine.test
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.fake.FakeDynamicColorSupport
import com.elinacn.subtrack.fake.FakeReminderDeliveryStatus
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
    private lateinit var dynamicColorSupport: FakeDynamicColorSupport
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSettingsRepository()
        reminderState = FakeReminderStateRepository()
        notificationStatus = FakeReminderNotificationStatus()
        dynamicColorSupport = FakeDynamicColorSupport()
        viewModel = buildViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- the first frame (ARCHITECTURE section 29) ----------------------------------------

    /**
     * What the screen draws before anything has been collected. The platform answers are known
     * synchronously and must already be right; the stored ones are not read yet and must say so
     * rather than stand in with a default.
     */
    @Test
    fun uiState_firstValue_hasThePlatformAnswersAndNoStoredOnes() = runTest {
        repository = FakeSettingsRepository(initial = Currency.USD)
        notificationStatus.remindersVisible = true
        dynamicColorSupport.available = true
        viewModel = buildViewModel()

        val first = viewModel.uiState.value

        assertEquals(ReminderPermissionState.ENABLED, first.reminderPermission)
        assertEquals(true, first.isDynamicColorSupported)
        assertNull(first.mainCurrency)
        assertNull(first.themeMode)
        assertNull(first.isDynamicColorEnabled)
    }

    @Test
    fun uiState_firstValue_withoutWallpaperColours_saysSoAtOnce() = runTest {
        dynamicColorSupport.available = false
        notificationStatus.runtimePermissionRequired = false
        viewModel = buildViewModel()

        val first = viewModel.uiState.value

        assertEquals(false, first.isDynamicColorSupported)
        // Below Android 13 there is no dialog to ask with, so the answer needs nothing stored.
        assertEquals(ReminderPermissionState.SETTINGS_ONLY, first.reminderPermission)
    }

    /** Whether the dialog may still be shown depends on a stored flag, so it waits for it. */
    @Test
    fun uiState_firstValue_permissionThatDependsOnTheStore_isUnknownUntilRead() = runTest {
        notificationStatus.remindersVisible = false
        notificationStatus.runtimePermissionRequired = true
        notificationStatus.permissionGranted = false
        viewModel = buildViewModel()

        assertNull(viewModel.uiState.value.reminderPermission)

        collectState()

        assertEquals(ReminderPermissionState.CAN_REQUEST, viewModel.uiState.value.reminderPermission)
        assertEquals(Currency.TRY, viewModel.uiState.value.mainCurrency)
        assertEquals(false, viewModel.uiState.value.isDynamicColorEnabled)
    }

    // --- main currency --------------------------------------------------------------------

    @Test
    fun uiState_nothingStored_startsOnTheDefaultCurrency() = runTest {
        viewModel.uiState.test {
            // Not read yet comes first, and is not the default (ARCHITECTURE section 29).
            assertNull(awaitItem().mainCurrency)
            this@runTest.advanceUntilIdle()
            assertEquals(Currency.TRY, expectMostRecentItem().mainCurrency)

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
    private fun buildViewModel() = SettingsViewModel(
        repository,
        reminderState,
        notificationStatus,
        dynamicColorSupport,
        FakeReminderDeliveryStatus()
    )

    private fun TestScope.collectState() {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
    }
}
