package com.elinacn.subtrack.ui.settings

import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.ThemeMode
import com.elinacn.subtrack.reminder.ReminderDeliveryStatus
import com.elinacn.subtrack.reminder.ReminderNotificationStatus
import com.elinacn.subtrack.ui.common.TimeFormatSupport
import com.elinacn.subtrack.ui.common.UiText
import java.time.LocalTime

/**
 * The part of the settings screen's state the settings file knows nothing about.
 *
 * Owned by [SettingsViewModel] and never seen outside it; a file of its own only to keep the
 * ViewModel under the size limit (CLAUDE.md §4).
 */
internal data class ReminderScreenState(
    /** Derived from the inputs below by the ViewModel; never set on its own. */
    val permission: ReminderPermissionState? = null,
    /** Read from the platform on the spot, so never unknown. */
    val delivery: ReminderDelivery = ReminderDelivery.ON_TIME,
    val isRationaleVisible: Boolean = false,
    /** The theme chooser is screen state too: nothing about it is stored. */
    val isThemeDialogVisible: Boolean = false,
    /** Set while the reminder time picker is open, or waiting for the stored time to open on. */
    val isTimePickerVisible: Boolean = false,
    /** The phone's clock setting, read from the platform on the spot, so never unknown. */
    val is24HourFormat: Boolean = false,
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
) {

    internal companion object {

        /**
         * Everything that is not stored in the settings file, as the platform answers it now.
         *
         * The reminder row starts from what the platform can answer on the spot, so a device where
         * reminders are on shows "On" in the very first frame instead of a placeholder. Phase 16t
         * measured the old start: "off - turn on in system settings" for the whole 700 ms enter
         * transition, because the state was only worked out on ON_RESUME and the navigation graph
         * holds a destination at STARTED until its transition ends. Whether reminders come on time
         * and how the phone writes the time are platform answers too, and are read here for the same
         * reason (ARCHITECTURE section 29).
         */
        fun initial(
            notificationStatus: ReminderNotificationStatus,
            deliveryStatus: ReminderDeliveryStatus,
            timeFormat: TimeFormatSupport
        ) = ReminderScreenState(
            permission = notificationStatus.resolveReminderPermission(
                wasRequested = null,
                canShowRationale = null
            ),
            delivery = deliveryStatus.resolveReminderDelivery(),
            is24HourFormat = timeFormat.is24HourFormat()
        )
    }
}

/**
 * The whole screen, from this state and the stored values, each null while still unread.
 *
 * One mapping for the first frame and every frame after it. The ViewModel's initial value is this
 * with nothing read from the store yet, so a platform answer added here cannot be left out of the
 * first frame, the one frame where the screen would otherwise still guess (ARCHITECTURE
 * section 29).
 */
internal fun ReminderScreenState.toUiState(
    isDynamicColorSupported: Boolean,
    mainCurrency: Currency? = null,
    themeMode: ThemeMode? = null,
    isDynamicColorEnabled: Boolean? = null,
    reminderTime: LocalTime? = null
) = SettingsUiState(
    mainCurrency = mainCurrency,
    themeMode = themeMode,
    isThemeDialogVisible = isThemeDialogVisible,
    isDynamicColorEnabled = isDynamicColorEnabled,
    isDynamicColorSupported = isDynamicColorSupported,
    reminderPermission = permission,
    reminderDelivery = delivery,
    reminderTime = reminderTime,
    // Off while the permission is still null as well: null is only left over once reminders are
    // known to be off, so the faded row is right from the first frame.
    isReminderTimeEnabled = permission == ReminderPermissionState.ENABLED,
    is24HourFormat = is24HourFormat,
    isReminderTimePickerVisible = isTimePickerVisible,
    isReminderRationaleVisible = isRationaleVisible,
    pendingReminderAction = pendingAction,
    errorMessage = errorMessage
)
