package com.elinacn.subtrack.ui.settings

import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.ThemeMode
import com.elinacn.subtrack.ui.common.UiText

/**
 * How reminders stand, and what the user can do about it from here.
 *
 * Three states rather than a Boolean, because "off" splits into two situations that need
 * different buttons. See ARCHITECTURE §18.
 */
enum class ReminderPermissionState {

    /** Reminders reach the user: the app switch is on and the channel is not silenced. */
    ENABLED,

    /** Off, and the app may still ask for the permission itself. */
    CAN_REQUEST,

    /** Off, and only the system settings can turn it back on. */
    SETTINGS_ONLY
}

/**
 * Whether reminders that are switched on will actually come on time.
 *
 * Only what the platform can answer. Phase 16w measured both of the held-back states stopping the
 * reminder job until the app was opened. Manufacturer battery rules and cleaner apps cannot be
 * seen, which is why the row always carries a general note underneath as well.
 */
enum class ReminderDelivery {

    /** Nothing the app can see is holding reminders back. */
    ON_TIME,

    /** Background activity is restricted: reminders wait until the app is opened. */
    UNTIL_OPENED,

    /** Battery saver is on: reminders may come late. */
    MAY_BE_DELAYED
}

/** Something only an Activity can do, decided here and carried out by the screen. */
enum class ReminderPermissionAction {

    /** Show the system permission dialog. */
    REQUEST_PERMISSION,

    /** Open this app's notification settings. */
    OPEN_SYSTEM_SETTINGS,

    /** Open this app's page in the system settings, where its battery settings live. */
    OPEN_APP_DETAILS,

    /** Open the system's battery saver settings. */
    OPEN_BATTERY_SAVER_SETTINGS
}

/**
 * Everything the settings screen draws, in one value.
 *
 * **Null means "not read yet", never "off" or "the default".** The stored preferences arrive from
 * DataStore a few frames after the screen is first drawn, and a default in their place is a wrong
 * value on screen for as long as the read takes - phase 16t measured the theme row saying "follow
 * the system" to a user who had chosen dark. What can be answered on the spot (the Android version,
 * the notification switch) is filled in before the first frame instead. See ARCHITECTURE
 * section 29.
 */
data class SettingsUiState(
    /** The currency the home total is shown in; null until the store has answered. */
    val mainCurrency: Currency? = null,
    /** The colour scheme the user asked for; null until the store has answered. */
    val themeMode: ThemeMode? = null,
    /** Set while the three-way theme chooser is showing. */
    val isThemeDialogVisible: Boolean = false,
    /** Whether the wallpaper supplies the palette; null until the store has answered. */
    val isDynamicColorEnabled: Boolean? = null,
    /** False below Android 12, where there is nothing to read the wallpaper with. */
    val isDynamicColorSupported: Boolean = false,
    /**
     * What the reminder row says and what tapping it does.
     *
     * Null only while it hangs on something not yet read: whether the app has asked before (a
     * stored flag) and, if it has, whether the system still allows a rationale (the Activity's
     * answer). Every other case is settled from the platform before the first frame.
     */
    val reminderPermission: ReminderPermissionState? = null,
    /**
     * Whether reminders will come on time, read from the platform before the first frame and again
     * each time the screen comes back. Only shown while [reminderPermission] is ENABLED.
     */
    val reminderDelivery: ReminderDelivery = ReminderDelivery.ON_TIME,
    /** Set while the short explanation before a repeat permission request is showing. */
    val isReminderRationaleVisible: Boolean = false,
    /** Set for one frame when the screen should carry out an Activity-only action. */
    val pendingReminderAction: ReminderPermissionAction? = null,
    /** Set when a preference could not be written; drives a snackbar. */
    val errorMessage: UiText? = null
)

/** Everything the settings screen can ask for. */
sealed interface SettingsEvent {

    /** The user picked a currency for totals. */
    data class SelectMainCurrency(val currency: Currency) : SettingsEvent

    /** The user tapped the theme row and wants to see the three choices. */
    data object ThemeRowTapped : SettingsEvent

    /** The user picked one of the three. */
    data class SelectThemeMode(val mode: ThemeMode) : SettingsEvent

    /** The user closed the chooser without picking. */
    data object ThemeDialogDismissed : SettingsEvent

    /** The user flipped the wallpaper-colours switch. */
    data class SetDynamicColor(val enabled: Boolean) : SettingsEvent

    /**
     * Re-read the reminder state. Sent when the screen starts and resumes and after a permission
     * answer, carrying the one fact only an Activity can supply.
     */
    data class RefreshReminderPermission(val canShowRationale: Boolean) : SettingsEvent

    /**
     * The system permission dialog has returned. Ends the wait that holds the row where it was
     * while the question was on screen, then re-reads like [RefreshReminderPermission].
     */
    data class ReminderPermissionAnswered(val canShowRationale: Boolean) : SettingsEvent

    /** The user tapped the reminder row. */
    data object ReminderRowTapped : SettingsEvent

    /** The user tapped the note under the reminder row about phones that delay reminders. */
    data object ReminderNoteTapped : SettingsEvent

    /** The user accepted the explanation and wants to be asked. */
    data object ReminderRationaleConfirmed : SettingsEvent

    /** The user dismissed the explanation. */
    data object ReminderRationaleDismissed : SettingsEvent

    /** The screen carried out [SettingsUiState.pendingReminderAction]. */
    data object ReminderActionHandled : SettingsEvent

    data object DismissError : SettingsEvent
}
