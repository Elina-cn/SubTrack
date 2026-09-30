package com.elinacn.subtrack.ui.settings

import com.elinacn.subtrack.ui.common.UiText

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
