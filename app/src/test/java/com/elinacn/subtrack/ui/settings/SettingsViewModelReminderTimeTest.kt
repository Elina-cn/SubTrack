package com.elinacn.subtrack.ui.settings

import com.elinacn.subtrack.R
import com.elinacn.subtrack.fake.FakeDynamicColorSupport
import com.elinacn.subtrack.fake.FakeReminderDeliveryStatus
import com.elinacn.subtrack.fake.FakeReminderNotificationStatus
import com.elinacn.subtrack.fake.FakeReminderStateRepository
import com.elinacn.subtrack.fake.FakeReminderTimeChanger
import com.elinacn.subtrack.fake.FakeSettingsRepository
import com.elinacn.subtrack.fake.FakeTimeFormatSupport
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

/**
 * The reminder time row: what it shows before and after the store answers, when it can be tapped,
 * and that a saved time goes to the scheduler and comes back through the store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelReminderTimeTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSettingsRepository
    private lateinit var changer: FakeReminderTimeChanger
    private lateinit var notificationStatus: FakeReminderNotificationStatus
    private lateinit var timeFormat: FakeTimeFormatSupport

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSettingsRepository()
        changer = FakeReminderTimeChanger(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- the first state ----------------------------------------------------------------------

    @Test
    fun firstFrame_timeNotReadYet_isUnknownRatherThanNine() {
        repository.reminderTime.value = LocalTime.of(20, 30)

        val viewModel = viewModel(remindersOn = true)

        // Nothing has collected the store yet: the row has to stay empty, not say 09:00.
        assertNull(viewModel.uiState.value.reminderTime)
        assertFalse(viewModel.uiState.value.isReminderTimePickerVisible)
    }

    @Test
    fun firstFrame_platformAnswers_areAlreadyIn() {
        val viewModel = viewModel(remindersOn = true, is24Hour = false)

        assertTrue(viewModel.uiState.value.isReminderTimeEnabled)
        assertFalse(viewModel.uiState.value.is24HourFormat)
    }

    @Test
    fun afterTheRead_showsTheStoredTime() = runTest {
        repository.reminderTime.value = LocalTime.of(7, 45)
        val viewModel = viewModel(remindersOn = true)

        collect(viewModel)

        assertEquals(LocalTime.of(7, 45), viewModel.uiState.value.reminderTime)
    }

    @Test
    fun nothingStored_showsNineInTheMorning() = runTest {
        val viewModel = viewModel(remindersOn = true)

        collect(viewModel)

        assertEquals(LocalTime.of(9, 0), viewModel.uiState.value.reminderTime)
    }

    // --- choosing a time ----------------------------------------------------------------------

    @Test
    fun tap_remindersOn_opensThePicker() = runTest {
        val viewModel = viewModel(remindersOn = true)
        collect(viewModel)

        viewModel.onEvent(SettingsEvent.ReminderTimeRowTapped)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isReminderTimePickerVisible)
    }

    @Test
    fun save_goesToTheScheduler_andTheRowShowsWhatTheStoreSays() = runTest {
        val viewModel = viewModel(remindersOn = true)
        collect(viewModel)
        viewModel.onEvent(SettingsEvent.ReminderTimeRowTapped)

        viewModel.onEvent(SettingsEvent.SelectReminderTime(hour = 21, minute = 15))
        advanceUntilIdle()

        assertEquals(listOf(LocalTime.of(21, 15)), changer.changes)
        assertEquals(listOf(LocalTime.of(21, 15)), repository.reminderTimeWrites)
        assertEquals(LocalTime.of(21, 15), viewModel.uiState.value.reminderTime)
        assertFalse(viewModel.uiState.value.isReminderTimePickerVisible)
    }

    @Test
    fun save_backToAnEarlierTime_isStoredToo() = runTest {
        repository.reminderTime.value = LocalTime.of(18, 0)
        val viewModel = viewModel(remindersOn = true)
        collect(viewModel)
        viewModel.onEvent(SettingsEvent.ReminderTimeRowTapped)

        viewModel.onEvent(SettingsEvent.SelectReminderTime(hour = 6, minute = 5))
        advanceUntilIdle()

        assertEquals(LocalTime.of(6, 5), viewModel.uiState.value.reminderTime)
    }

    @Test
    fun cancel_writesNothing_andClosesThePicker() = runTest {
        val viewModel = viewModel(remindersOn = true)
        collect(viewModel)
        viewModel.onEvent(SettingsEvent.ReminderTimeRowTapped)

        viewModel.onEvent(SettingsEvent.ReminderTimeDialogDismissed)
        advanceUntilIdle()

        assertTrue(changer.changes.isEmpty())
        assertEquals(LocalTime.of(9, 0), viewModel.uiState.value.reminderTime)
        assertFalse(viewModel.uiState.value.isReminderTimePickerVisible)
    }

    @Test
    fun save_theTimeAlreadyStored_writesNothing() = runTest {
        // Saving the default as it stands must not turn it into a stored key.
        val viewModel = viewModel(remindersOn = true)
        collect(viewModel)
        viewModel.onEvent(SettingsEvent.ReminderTimeRowTapped)

        viewModel.onEvent(SettingsEvent.SelectReminderTime(hour = 9, minute = 0))
        advanceUntilIdle()

        assertTrue(changer.changes.isEmpty())
        assertFalse(viewModel.uiState.value.isReminderTimePickerVisible)
    }

    @Test
    fun save_failed_keepsTheOldTime_andSaysSo() = runTest {
        val viewModel = viewModel(remindersOn = true)
        collect(viewModel)
        repository.failOnWrite = IllegalStateException("disk full")

        viewModel.onEvent(SettingsEvent.SelectReminderTime(hour = 22, minute = 0))
        advanceUntilIdle()

        assertEquals(LocalTime.of(9, 0), viewModel.uiState.value.reminderTime)
        assertEquals(
            UiText.Resource(R.string.error_setting_save_failed),
            viewModel.uiState.value.errorMessage
        )
    }

    // --- reminders off ------------------------------------------------------------------------

    @Test
    fun remindersOff_rowIsOff_butStillShowsTheStoredTime() = runTest {
        repository.reminderTime.value = LocalTime.of(19, 0)
        val viewModel = viewModel(remindersOn = false, permissionRequested = true)
        collect(viewModel)
        refresh(viewModel)

        assertEquals(ReminderPermissionState.SETTINGS_ONLY, viewModel.uiState.value.reminderPermission)
        assertFalse(viewModel.uiState.value.isReminderTimeEnabled)
        assertEquals(LocalTime.of(19, 0), viewModel.uiState.value.reminderTime)
    }

    @Test
    fun remindersOff_tap_opensNothing() = runTest {
        val viewModel = viewModel(remindersOn = false)
        collect(viewModel)
        refresh(viewModel)

        viewModel.onEvent(SettingsEvent.ReminderTimeRowTapped)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isReminderTimePickerVisible)
        assertTrue(changer.changes.isEmpty())
    }

    @Test
    fun remindersOff_firstFrame_isAlreadyOff() {
        // The permission itself is still unknown here (the stored flag is unread), but every
        // state that can come out of that is an off one, so the faded row is not a guess.
        val viewModel = viewModel(remindersOn = false)

        assertNull(viewModel.uiState.value.reminderPermission)
        assertFalse(viewModel.uiState.value.isReminderTimeEnabled)
    }

    @Test
    fun remindersTurnedOffWhileAway_rowTurnsOffOnReturn() = runTest {
        val viewModel = viewModel(remindersOn = true)
        collect(viewModel)
        assertTrue(viewModel.uiState.value.isReminderTimeEnabled)

        notificationStatus.remindersVisible = false
        notificationStatus.permissionGranted = true
        refresh(viewModel)

        assertFalse(viewModel.uiState.value.isReminderTimeEnabled)
    }

    // --- the phone's clock --------------------------------------------------------------------

    @Test
    fun clockSwitchedWhileAway_isPickedUpOnReturn() = runTest {
        val viewModel = viewModel(remindersOn = true, is24Hour = true)
        collect(viewModel)

        timeFormat.is24Hour = false
        refresh(viewModel)

        assertFalse(viewModel.uiState.value.is24HourFormat)
    }

    private fun viewModel(
        remindersOn: Boolean,
        is24Hour: Boolean = true,
        permissionRequested: Boolean = false
    ): SettingsViewModel {
        notificationStatus = FakeReminderNotificationStatus(
            remindersVisible = remindersOn,
            permissionGranted = remindersOn
        )
        timeFormat = FakeTimeFormatSupport(is24Hour)
        return SettingsViewModel(
            repository,
            FakeReminderStateRepository(permissionRequested = permissionRequested),
            notificationStatus,
            FakeDynamicColorSupport(),
            FakeReminderDeliveryStatus(),
            timeFormat,
            changer
        )
    }

    /** WhileSubscribed keeps the state cold until something collects it. */
    private fun TestScope.collect(viewModel: SettingsViewModel) {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
    }

    private fun TestScope.refresh(viewModel: SettingsViewModel) {
        viewModel.onEvent(SettingsEvent.RefreshReminderPermission(canShowRationale = false))
        advanceUntilIdle()
    }
}
