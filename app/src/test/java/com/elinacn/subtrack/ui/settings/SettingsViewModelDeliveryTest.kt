package com.elinacn.subtrack.ui.settings

import com.elinacn.subtrack.fake.FakeDynamicColorSupport
import com.elinacn.subtrack.fake.FakeReminderDeliveryStatus
import com.elinacn.subtrack.fake.FakeReminderNotificationStatus
import com.elinacn.subtrack.fake.FakeReminderStateRepository
import com.elinacn.subtrack.fake.FakeReminderTimeChanger
import com.elinacn.subtrack.fake.FakeSettingsRepository
import com.elinacn.subtrack.fake.FakeTimeFormatSupport
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
import org.junit.Before
import org.junit.Test

/**
 * Whether reminders that are on will come on time: what the row says and where a tap leads.
 *
 * Both answers come from the platform on the spot, so the first state the screen can draw already
 * carries them - there is no "not read yet" for these (ARCHITECTURE section 29).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelDeliveryTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var delivery: FakeReminderDeliveryStatus

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- the first state, before anything has been collected ----------------------------------

    @Test
    fun initialState_nothingHoldingRemindersBack_isOnTime() {
        val viewModel = viewModel()

        assertEquals(ReminderDelivery.ON_TIME, viewModel.uiState.value.reminderDelivery)
        assertEquals(ReminderPermissionState.ENABLED, viewModel.uiState.value.reminderPermission)
    }

    @Test
    fun initialState_backgroundRestricted_saysUntilOpenedFromTheFirstFrame() {
        val viewModel = viewModel(backgroundRestricted = true)

        assertEquals(ReminderDelivery.UNTIL_OPENED, viewModel.uiState.value.reminderDelivery)
    }

    @Test
    fun initialState_batterySaverOn_saysMayBeDelayedFromTheFirstFrame() {
        val viewModel = viewModel(powerSaveMode = true)

        assertEquals(ReminderDelivery.MAY_BE_DELAYED, viewModel.uiState.value.reminderDelivery)
    }

    @Test
    fun bothOn_theRestrictionOutranksBatterySaver() {
        val viewModel = viewModel(backgroundRestricted = true, powerSaveMode = true)

        assertEquals(ReminderDelivery.UNTIL_OPENED, viewModel.uiState.value.reminderDelivery)
    }

    // --- the two warnings, collected ---------------------------------------------------------

    @Test
    fun backgroundRestricted_rowTapOpensTheAppsSystemPage() = runTest {
        val viewModel = viewModel(backgroundRestricted = true)
        collect(viewModel)

        viewModel.onEvent(SettingsEvent.ReminderRowTapped)
        advanceUntilIdle()

        assertEquals(ReminderDelivery.UNTIL_OPENED, viewModel.uiState.value.reminderDelivery)
        assertEquals(
            ReminderPermissionAction.OPEN_APP_DETAILS,
            viewModel.uiState.value.pendingReminderAction
        )
    }

    @Test
    fun batterySaverOn_rowTapOpensTheBatterySaverSettings() = runTest {
        val viewModel = viewModel(powerSaveMode = true)
        collect(viewModel)

        viewModel.onEvent(SettingsEvent.ReminderRowTapped)
        advanceUntilIdle()

        assertEquals(ReminderDelivery.MAY_BE_DELAYED, viewModel.uiState.value.reminderDelivery)
        assertEquals(
            ReminderPermissionAction.OPEN_BATTERY_SAVER_SETTINGS,
            viewModel.uiState.value.pendingReminderAction
        )
    }

    @Test
    fun onTime_rowTapStillOpensTheNotificationSettings() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.onEvent(SettingsEvent.ReminderRowTapped)
        advanceUntilIdle()

        assertEquals(
            ReminderPermissionAction.OPEN_SYSTEM_SETTINGS,
            viewModel.uiState.value.pendingReminderAction
        )
    }

    @Test
    fun noteTap_opensTheAppsSystemPage() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.onEvent(SettingsEvent.ReminderNoteTapped)
        advanceUntilIdle()

        assertEquals(
            ReminderPermissionAction.OPEN_APP_DETAILS,
            viewModel.uiState.value.pendingReminderAction
        )
    }

    // --- coming back to the screen ---------------------------------------------------------------

    @Test
    fun comingBack_afterTheRestrictionWasLifted_theWarningGoes() = runTest {
        val viewModel = viewModel(backgroundRestricted = true)
        collect(viewModel)

        // The user followed the row to the system page and allowed background activity there.
        delivery.backgroundRestricted = false
        viewModel.onEvent(SettingsEvent.RefreshReminderPermission(canShowRationale = false))
        advanceUntilIdle()

        assertEquals(ReminderDelivery.ON_TIME, viewModel.uiState.value.reminderDelivery)
    }

    @Test
    fun comingBack_afterBatterySaverWasTurnedOn_theWarningAppears() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        delivery.powerSaveMode = true
        viewModel.onEvent(SettingsEvent.RefreshReminderPermission(canShowRationale = false))
        advanceUntilIdle()

        assertEquals(ReminderDelivery.MAY_BE_DELAYED, viewModel.uiState.value.reminderDelivery)
    }

    private fun viewModel(
        backgroundRestricted: Boolean = false,
        powerSaveMode: Boolean = false
    ): SettingsViewModel {
        delivery = FakeReminderDeliveryStatus(backgroundRestricted, powerSaveMode)
        val settings = FakeSettingsRepository()
        return SettingsViewModel(
            settings,
            FakeReminderStateRepository(permissionRequested = true),
            FakeReminderNotificationStatus(remindersVisible = true, permissionGranted = true),
            FakeDynamicColorSupport(),
            delivery,
            FakeTimeFormatSupport(),
            FakeReminderTimeChanger(settings)
        )
    }

    /** WhileSubscribed keeps the state cold until something collects it. */
    private fun TestScope.collect(viewModel: SettingsViewModel) {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
    }
}
