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

/** Something only an Activity can do, decided here and carried out by the screen. */
enum class ReminderPermissionAction {

    /** Show the system permission dialog. */
    REQUEST_PERMISSION,

    /** Open this app's notification settings. */
    OPEN_SYSTEM_SETTINGS
}

/**
 * Everything the settings screen draws, in one value.
 */
data class SettingsUiState(
    /** The currency the home total is shown in. */
    val mainCurrency: Currency = Currency.Base,
    /** The colour scheme the user asked for. */
    val themeMode: ThemeMode = ThemeMode.Default,
    /** Set while the three-way theme chooser is showing. */
    val isThemeDialogVisible: Boolean = false,
    /** Whether the wallpaper supplies the palette. */
    val isDynamicColorEnabled: Boolean = false,
    /** False below Android 12, where there is nothing to read the wallpaper with. */
    val isDynamicColorSupported: Boolean = false,
    /** What the reminder row says and what tapping it does. */
    val reminderPermission: ReminderPermissionState = ReminderPermissionState.SETTINGS_ONLY,
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
     * Re-read the reminder state. Sent when the screen resumes and after a permission answer,
     * carrying the one fact only an Activity can supply.
     */
    data class RefreshReminderPermission(val canShowRationale: Boolean) : SettingsEvent

    /** The user tapped the reminder row. */
    data object ReminderRowTapped : SettingsEvent

    /** The user accepted the explanation and wants to be asked. */
    data object ReminderRationaleConfirmed : SettingsEvent

    /** The user dismissed the explanation. */
    data object ReminderRationaleDismissed : SettingsEvent

    /** The screen carried out [SettingsUiState.pendingReminderAction]. */
    data object ReminderActionHandled : SettingsEvent

    data object DismissError : SettingsEvent
}
