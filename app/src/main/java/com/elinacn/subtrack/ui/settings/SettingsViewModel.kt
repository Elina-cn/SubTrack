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
import com.elinacn.subtrack.ui.common.startingUnknown
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

    /**
     * Everything that is not stored in the settings file.
     *
     * The reminder row starts from what the platform can answer on the spot, so a device where
     * reminders are on shows "On" in the very first frame instead of a placeholder. Phase 16t
     * measured the old start: "off - turn on in system settings" for the whole 700 ms enter
     * transition, because the state was only worked out on ON_RESUME and the navigation graph holds
     * a destination at STARTED until its transition ends.
     */
    private val screenState = MutableStateFlow(
        ReminderScreenState(
            permission = notificationStatus.resolveReminderPermission(
                wasRequested = null,
                canShowRationale = null
            )
        )
    )

    /**
     * The stored preference is the single source of truth: a tap writes and the screen updates
     * because the store emits again, not because the ViewModel guessed. A write that fails
     * therefore leaves the chips where they were, which is the truth.
     *
     * Each stored value starts as null, "not read yet", and the screen draws nothing for it until
     * the store answers (ARCHITECTURE section 29). Starting each one on its own rather than waiting
     * for all of them lets the reminder row, which does not depend on the store at all, be right
     * from the first emission.
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        repository.observeMainCurrency().startingUnknown(),
        repository.observeThemeMode().startingUnknown(),
        repository.observeDynamicColor().startingUnknown(),
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
        // What the screen draws before anything has been collected. The two platform answers are
        // filled in here too - an initial value that left them out would be the one frame where
        // the screen still guessed.
        initialValue = SettingsUiState(
            isDynamicColorSupported = dynamicColorSupport.isAvailable(),
            reminderPermission = screenState.value.permission
        )
    )

    init {
        // The one stored input the reminder row needs. Read now rather than on the first resume,
        // which comes only after the enter transition.
        viewModelScope.launch {
            val wasRequested = reminderState.wasPermissionRequested()
            updateReminder { it.copy(wasRequested = wasRequested) }
        }
    }

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

            is SettingsEvent.ReminderPermissionAnswered ->
                refreshReminders(event.canShowRationale, requestAnswered = true)

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
     * Works out which of the three states the row is in, again.
     *
     * [canShowRationale] is passed in because only an Activity can answer it. Everything else is
     * read here, so the screen never decides anything. The platform half is applied at once - on
     * the way back from the system settings that is the half that changed - and the stored flag
     * follows when the read returns.
     */
    private fun refreshReminders(canShowRationale: Boolean, requestAnswered: Boolean = false) {
        updateReminder {
            it.copy(
                canShowRationale = canShowRationale,
                isRequestInFlight = it.isRequestInFlight && !requestAnswered
            )
        }
        viewModelScope.launch {
            val wasRequested = reminderState.wasPermissionRequested()
            updateReminder { it.copy(wasRequested = wasRequested) }
        }
    }

    /**
     * Applies a change to the row's inputs and re-derives the state from them.
     *
     * The one place the state is worked out, so no path can leave it out of step with its inputs.
     * A tap that arrived while the state was still unknown is carried out here, the moment it
     * becomes known.
     *
     * While a permission request is out the state is held where it was. The request marks the app
     * as having asked, and the system's rationale answer only changes once the user has replied, so
     * working the table out in between gives "asked, no rationale" - "turn on in system settings" -
     * behind a dialog that is in fact asking. Measured in 16u before this hold existed.
     */
    private fun updateReminder(change: (ReminderScreenState) -> ReminderScreenState) {
        screenState.update { state ->
            val changed = change(state)
            val permission = if (changed.isRequestInFlight) {
                state.permission
            } else {
                notificationStatus.resolveReminderPermission(
                    changed.wasRequested,
                    changed.canShowRationale
                )
            }
            changed.copy(permission = permission)
        }
        val state = screenState.value
        if (state.isRowTapPending && state.permission != null) {
            screenState.update { it.copy(isRowTapPending = false) }
            onReminderRowTapped()
        }
    }

    private fun onReminderRowTapped() {
        val state = screenState.value
        when (state.permission) {
            // Held rather than dropped or guessed. Phase 16t tapped the row while it still showed
            // its placeholder and was sent to the system settings instead of the permission
            // dialog; now the tap waits a few frames for the answer and then does the right thing.
            null -> screenState.update { it.copy(isRowTapPending = true) }

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
                if (state.wasRequested == true) {
                    screenState.update { it.copy(isRationaleVisible = true) }
                } else {
                    requestPermission()
                }
        }
    }

    private fun requestPermission() {
        updateReminder {
            it.copy(
                isRationaleVisible = false,
                pendingAction = ReminderPermissionAction.REQUEST_PERMISSION,
                // Recorded as the request goes out, not when it comes back: the answer does not
                // change the fact that the user has now been asked once.
                wasRequested = true,
                isRequestInFlight = true
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
        /** Derived from the inputs below by [updateReminder]; never set on its own. */
        val permission: ReminderPermissionState? = null,
        val isRationaleVisible: Boolean = false,
        /** The theme chooser is screen state too: nothing about it is stored. */
        val isThemeDialogVisible: Boolean = false,
        val pendingAction: ReminderPermissionAction? = null,
        /** Mirrors the stored flag so a tap does not have to wait on a read; null until read. */
        val wasRequested: Boolean? = null,
        /** The Activity's answer, from the last refresh; null until the screen has sent one. */
        val canShowRationale: Boolean? = null,
        /** A row tap that came in while [permission] was still unknown. */
        val isRowTapPending: Boolean = false,
        /** Between a permission request going out and its answer coming back. */
        val isRequestInFlight: Boolean = false,
        val errorMessage: UiText? = null
    )

    private companion object {
        /** Outlives a configuration change, expires on a real departure. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
