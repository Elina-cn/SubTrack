package com.elinacn.subtrack.ui.settings

import com.elinacn.subtrack.fake.FakeDynamicColorSupport
import com.elinacn.subtrack.fake.FakeReminderNotificationStatus
import com.elinacn.subtrack.fake.FakeReminderStateRepository
import com.elinacn.subtrack.fake.FakeSettingsRepository
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

/**
 * The reminder row's decision table, driven through the ViewModel's public surface.
 *
 * The state machine has three outcomes and six ways in, and only one of the inputs - whether the
 * system will still show a rationale - comes from the screen. Everything else is read here, which
 * is what makes the table testable without a device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelReminderTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSettingsRepository
    private lateinit var reminderState: FakeReminderStateRepository
    private lateinit var notificationStatus: FakeReminderNotificationStatus
    private val dynamicColorSupport = FakeDynamicColorSupport()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSettingsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refresh_permissionGrantedAndNotificationsOn_isEnabled() = runTest {
        val viewModel = viewModel(remindersVisible = true, permissionGranted = true)
        collect(viewModel)

        refresh(viewModel)

        assertEquals(ReminderPermissionState.ENABLED, viewModel.uiState.value.reminderPermission)
    }

    @Test
    fun refresh_permissionGrantedButNotificationsOff_isSettingsOnly() = runTest {
        val viewModel = viewModel(remindersVisible = false, permissionGranted = true)
        collect(viewModel)

        refresh(viewModel)

        // Nothing left to request: the permission is held and the switch is the user's own.
        assertEquals(
            ReminderPermissionState.SETTINGS_ONLY,
            viewModel.uiState.value.reminderPermission
        )
    }

    @Test
    fun refresh_permissionGrantedButChannelSilenced_isSettingsOnly() = runTest {
        // A silenced channel reaches the ViewModel as "not visible" exactly like an app switch
        // that is off; telling the two apart is the platform-backed status object's job, and the
        // answer here has to be the same either way.
        val viewModel = viewModel(remindersVisible = false, permissionGranted = true)
        collect(viewModel)

        refresh(viewModel)

        assertEquals(
            ReminderPermissionState.SETTINGS_ONLY,
            viewModel.uiState.value.reminderPermission
        )
    }

    @Test
    fun refresh_permissionMissingAndNeverAsked_canRequest() = runTest {
        val viewModel = viewModel(remindersVisible = false, permissionGranted = false)
        collect(viewModel)

        refresh(viewModel, canShowRationale = false)

        // The system says "no rationale" here just as it does after a permanent refusal. Only the
        // stored flag separates them, and it says the user has never been asked.
        assertEquals(
            ReminderPermissionState.CAN_REQUEST,
            viewModel.uiState.value.reminderPermission
        )
    }

    @Test
    fun refresh_permissionMissingAskedAndRationaleAllowed_canRequest() = runTest {
        val viewModel = viewModel(
            remindersVisible = false,
            permissionGranted = false,
            permissionRequested = true
        )
        collect(viewModel)

        refresh(viewModel, canShowRationale = true)

        assertEquals(
            ReminderPermissionState.CAN_REQUEST,
            viewModel.uiState.value.reminderPermission
        )
    }

    @Test
    fun refresh_permissionMissingAskedAndNoRationale_isSettingsOnly() = runTest {
        val viewModel = viewModel(
            remindersVisible = false,
            permissionGranted = false,
            permissionRequested = true
        )
        collect(viewModel)

        refresh(viewModel, canShowRationale = false)

        // Asked once, and the system will not let us ask again: denied for good.
        assertEquals(
            ReminderPermissionState.SETTINGS_ONLY,
            viewModel.uiState.value.reminderPermission
        )
    }

    @Test
    fun refresh_belowApi33AndNotificationsOff_isSettingsOnly() = runTest {
        val viewModel = viewModel(
            remindersVisible = false,
            runtimePermissionRequired = false
        )
        collect(viewModel)

        refresh(viewModel)

        // There is no permission to request on this build, whatever the flag says.
        assertEquals(
            ReminderPermissionState.SETTINGS_ONLY,
            viewModel.uiState.value.reminderPermission
        )
    }

    @Test
    fun refresh_belowApi33AndNotificationsOn_isEnabled() = runTest {
        val viewModel = viewModel(remindersVisible = true, runtimePermissionRequired = false)
        collect(viewModel)

        refresh(viewModel)

        assertEquals(ReminderPermissionState.ENABLED, viewModel.uiState.value.reminderPermission)
    }

    @Test
    fun rowTapped_neverAsked_requestsWithoutAnExplanationFirst() = runTest {
        val viewModel = viewModel(remindersVisible = false, permissionGranted = false)
        collect(viewModel)
        refresh(viewModel, canShowRationale = false)

        viewModel.onEvent(SettingsEvent.ReminderRowTapped)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("a dialog was shown on the first ask", state.isReminderRationaleVisible)
        assertEquals(ReminderPermissionAction.REQUEST_PERMISSION, state.pendingReminderAction)
        // Written as the request goes out, not when it comes back.
        assertEquals(listOf(true), reminderState.permissionRequestWrites)
    }

    @Test
    fun rowTapped_askedBefore_explainsBeforeRequesting() = runTest {
        val viewModel = viewModel(
            remindersVisible = false,
            permissionGranted = false,
            permissionRequested = true
        )
        collect(viewModel)
        refresh(viewModel, canShowRationale = true)

        viewModel.onEvent(SettingsEvent.ReminderRowTapped)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("the repeat ask went out with no explanation", state.isReminderRationaleVisible)
        assertNull("the system dialog jumped the explanation", state.pendingReminderAction)
    }

    @Test
    fun rationaleConfirmed_requestsAndClosesTheDialog() = runTest {
        val viewModel = viewModel(
            remindersVisible = false,
            permissionGranted = false,
            permissionRequested = true
        )
        collect(viewModel)
        refresh(viewModel, canShowRationale = true)
        viewModel.onEvent(SettingsEvent.ReminderRowTapped)
        advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.ReminderRationaleConfirmed)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isReminderRationaleVisible)
        assertEquals(ReminderPermissionAction.REQUEST_PERMISSION, state.pendingReminderAction)
    }

    @Test
    fun rationaleDismissed_asksForNothing() = runTest {
        val viewModel = viewModel(
            remindersVisible = false,
            permissionGranted = false,
            permissionRequested = true
        )
        collect(viewModel)
        refresh(viewModel, canShowRationale = true)
        viewModel.onEvent(SettingsEvent.ReminderRowTapped)
        advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.ReminderRationaleDismissed)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isReminderRationaleVisible)
        assertNull(state.pendingReminderAction)
    }

    @Test
    fun rowTapped_settingsOnly_opensTheSystemSettings() = runTest {
        val viewModel = viewModel(
            remindersVisible = false,
            permissionGranted = false,
            permissionRequested = true
        )
        collect(viewModel)
        refresh(viewModel, canShowRationale = false)

        viewModel.onEvent(SettingsEvent.ReminderRowTapped)
        advanceUntilIdle()

        assertEquals(
            ReminderPermissionAction.OPEN_SYSTEM_SETTINGS,
            viewModel.uiState.value.pendingReminderAction
        )
        assertTrue(
            "a permission request went out with no way to grant it",
            reminderState.permissionRequestWrites.isEmpty()
        )
    }

    @Test
    fun rowTapped_alreadyEnabled_opensTheSystemSettings() = runTest {
        val viewModel = viewModel(remindersVisible = true, permissionGranted = true)
        collect(viewModel)
        refresh(viewModel)

        viewModel.onEvent(SettingsEvent.ReminderRowTapped)
        advanceUntilIdle()

        // Turning reminders back off is where turning them on was.
        assertEquals(
            ReminderPermissionAction.OPEN_SYSTEM_SETTINGS,
            viewModel.uiState.value.pendingReminderAction
        )
    }

    @Test
    fun actionHandled_clearsThePendingAction() = runTest {
        val viewModel = viewModel(remindersVisible = true, permissionGranted = true)
        collect(viewModel)
        refresh(viewModel)
        viewModel.onEvent(SettingsEvent.ReminderRowTapped)
        advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.ReminderActionHandled)
        advanceUntilIdle()

        // Otherwise the screen would open the settings again on the next recomposition.
        assertNull(viewModel.uiState.value.pendingReminderAction)
    }

    private fun viewModel(
        remindersVisible: Boolean,
        runtimePermissionRequired: Boolean = true,
        permissionGranted: Boolean = false,
        permissionRequested: Boolean = false
    ): SettingsViewModel {
        reminderState = FakeReminderStateRepository(permissionRequested = permissionRequested)
        notificationStatus = FakeReminderNotificationStatus(
            remindersVisible = remindersVisible,
            runtimePermissionRequired = runtimePermissionRequired,
            permissionGranted = permissionGranted
        )
        return SettingsViewModel(repository, reminderState, notificationStatus, dynamicColorSupport)
    }

    /** WhileSubscribed keeps the state cold until something collects it. */
    private fun TestScope.collect(viewModel: SettingsViewModel) {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
    }

    private fun TestScope.refresh(viewModel: SettingsViewModel, canShowRationale: Boolean = false) {
        viewModel.onEvent(SettingsEvent.RefreshReminderPermission(canShowRationale))
        advanceUntilIdle()
    }
}
