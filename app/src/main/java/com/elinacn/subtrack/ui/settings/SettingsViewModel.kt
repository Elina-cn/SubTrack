package com.elinacn.subtrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.ThemeMode
import com.elinacn.subtrack.domain.repository.ReminderStateRepository
import com.elinacn.subtrack.domain.repository.SettingsRepository
import com.elinacn.subtrack.reminder.ReminderNotificationStatus
import com.elinacn.subtrack.ui.common.UiText
import com.elinacn.subtrack.ui.theme.DynamicColorSupport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Holds the settings screen's state and turns its events into preference writes. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val reminderState: ReminderStateRepository,
    private val notificationStatus: ReminderNotificationStatus,
    private val dynamicColorSupport: DynamicColorSupport
) : ViewModel() {

    /** Everything that is not stored in the settings file. */
    private val screenState = MutableStateFlow(ReminderScreenState())

    /**
     * The stored preference is the single source of truth: a tap writes and the screen updates
     * because the store emits again, not because the ViewModel guessed. A write that fails
     * therefore leaves the chips where they were, which is the truth.
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        repository.observeMainCurrency(),
        repository.observeThemeMode(),
        repository.observeDynamicColor(),
        screenState
    ) { currency, themeMode, dynamicColor, reminder ->
        SettingsUiState(
            mainCurrency = currency,
            themeMode = themeMode,
            isThemeDialogVisible = reminder.isThemeDialogVisible,
            isDynamicColorEnabled = dynamicColor,
            // Not stored: it is a property of the device, so it is read rather than remembered.
            isDynamicColorSupported = dynamicColorSupport.isAvailable(),
            reminderPermission = reminder.permission,
            isReminderRationaleVisible = reminder.isRationaleVisible,
            pendingReminderAction = reminder.pendingAction,
            errorMessage = reminder.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState()
    )

    /** Single entry point for everything the screen can ask for. */
    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SelectMainCurrency -> setMainCurrency(event.currency)

            SettingsEvent.ThemeRowTapped ->
                screenState.update { it.copy(isThemeDialogVisible = true) }

            is SettingsEvent.SelectThemeMode -> setThemeMode(event.mode)

            SettingsEvent.ThemeDialogDismissed ->
                screenState.update { it.copy(isThemeDialogVisible = false) }

            is SettingsEvent.SetDynamicColor -> setDynamicColor(event.enabled)

            is SettingsEvent.RefreshReminderPermission -> refreshReminders(event.canShowRationale)

            SettingsEvent.ReminderRowTapped -> onReminderRowTapped()

            SettingsEvent.ReminderRationaleConfirmed -> requestPermission()

            SettingsEvent.ReminderRationaleDismissed ->
                screenState.update { it.copy(isRationaleVisible = false) }

            SettingsEvent.ReminderActionHandled ->
                screenState.update { it.copy(pendingAction = null) }

            SettingsEvent.DismissError -> screenState.update { it.copy(errorMessage = null) }
        }
    }

    /**
     * Works out which of the three states the row is in.
     *
     * [canShowRationale] is passed in because only an Activity can answer it. Everything else is
     * read here, so the screen never decides anything.
     */
    private fun refreshReminders(canShowRationale: Boolean) {
        viewModelScope.launch {
            val wasRequested = reminderState.wasPermissionRequested()
            screenState.update {
                it.copy(
                    permission = resolvePermission(canShowRationale, wasRequested),
                    wasRequested = wasRequested
                )
            }
        }
    }

    private fun resolvePermission(
        canShowRationale: Boolean,
        wasRequested: Boolean
    ): ReminderPermissionState = when {
        // Covers every build: on older ones there is no permission to hold, only the switch.
        notificationStatus.areRemindersVisible() -> ReminderPermissionState.ENABLED

        // No runtime permission on this build, so nothing to request - the switch or the channel
        // is off and only the system screen can undo that.
        !notificationStatus.isRuntimePermissionRequired() -> ReminderPermissionState.SETTINGS_ONLY

        // Permission held but reminders still invisible: the app switch or this channel is off.
        notificationStatus.isPermissionGranted() -> ReminderPermissionState.SETTINGS_ONLY

        // Never asked. The system would say "no rationale needed" here too, which is exactly why
        // the flag exists rather than trusting the system's answer alone.
        !wasRequested -> ReminderPermissionState.CAN_REQUEST

        // Asked before and the system still lets us explain: one more request is allowed.
        canShowRationale -> ReminderPermissionState.CAN_REQUEST

        // Asked, no rationale offered: denied for good, the system screen is the only way back.
        else -> ReminderPermissionState.SETTINGS_ONLY
    }

    private fun onReminderRowTapped() {
        val state = screenState.value
        when (state.permission) {
            // Tapping an already-on row opens the same system screen, so turning reminders back
            // off is where turning them on was.
            ReminderPermissionState.ENABLED,
            ReminderPermissionState.SETTINGS_ONLY ->
                screenState.update {
                    it.copy(pendingAction = ReminderPermissionAction.OPEN_SYSTEM_SETTINGS)
                }

            ReminderPermissionState.CAN_REQUEST ->
                // First time, no preamble: an extra dialog before the system one costs a refusal
                // more often than it earns a grant. A repeat ask gets a sentence of context.
                if (state.wasRequested) {
                    screenState.update { it.copy(isRationaleVisible = true) }
                } else {
                    requestPermission()
                }
        }
    }

    private fun requestPermission() {
        screenState.update {
            it.copy(
                isRationaleVisible = false,
                pendingAction = ReminderPermissionAction.REQUEST_PERMISSION,
                // Recorded as the request goes out, not when it comes back: the answer does not
                // change the fact that the user has now been asked once.
                wasRequested = true
            )
        }
        viewModelScope.launch {
            try {
                reminderState.setPermissionRequested()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                screenState.update { it.copy(errorMessage = UiText.Resource(R.string.error_setting_save_failed)) }
            }
        }
    }

    private fun setMainCurrency(currency: Currency) {
        write { repository.setMainCurrency(currency) }
    }

    /**
     * The chooser closes on the tap, not on the write.
     *
     * A dialog left open while the disk is written would be the only part of this screen that
     * waits on storage. The stored value still drives what is shown underneath, so a write that
     * fails leaves the row on the old theme and raises the snackbar.
     */
    private fun setThemeMode(mode: ThemeMode) {
        screenState.update { it.copy(isThemeDialogVisible = false) }
        write { repository.setThemeMode(mode) }
    }

    private fun setDynamicColor(enabled: Boolean) {
        write { repository.setDynamicColor(enabled) }
    }

    /**
     * Stores a preference and reports a failure rather than swallowing it.
     *
     * Every setting on this screen writes the same way - the store is the source of truth and a
     * write that did not stick has to say so - so the shape is written once.
     */
    private fun write(store: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                store()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                // Never swallowed: a preference that did not stick has to say so.
                screenState.update { it.copy(errorMessage = UiText.Resource(R.string.error_setting_save_failed)) }
            }
        }
    }

    /** The part of the screen's state the settings file knows nothing about. */
    private data class ReminderScreenState(
        val permission: ReminderPermissionState = ReminderPermissionState.SETTINGS_ONLY,
        val isRationaleVisible: Boolean = false,
        /** The theme chooser is screen state too: nothing about it is stored. */
        val isThemeDialogVisible: Boolean = false,
        val pendingAction: ReminderPermissionAction? = null,
        /** Mirrors the stored flag so a tap does not have to wait on a read. */
        val wasRequested: Boolean = false,
        val errorMessage: UiText? = null
    )

    private companion object {
        /** Outlives a configuration change, expires on a real departure. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
