package com.elinacn.subtrack.ui.settings

import com.elinacn.subtrack.reminder.ReminderDeliveryStatus

/**
 * Which of the three delivery states the reminder row is in.
 *
 * A background restriction outranks battery saver when both are on: it stops reminders until the
 * app is opened for as long as it stays set, while battery saver usually ends when the phone is
 * charged. Phase 16w EK measured both holding the job back the same way while they last.
 */
internal fun ReminderDeliveryStatus.resolveReminderDelivery(): ReminderDelivery = when {
    isBackgroundRestricted() -> ReminderDelivery.UNTIL_OPENED
    isPowerSaveMode() -> ReminderDelivery.MAY_BE_DELAYED
    else -> ReminderDelivery.ON_TIME
}

/**
 * Where tapping a reminder row that is on leads: to whatever is holding reminders back, or to the
 * notification settings, where they can be switched off again, when nothing is.
 */
internal fun ReminderDelivery.enabledRowAction(): ReminderPermissionAction = when (this) {
    ReminderDelivery.UNTIL_OPENED -> ReminderPermissionAction.OPEN_APP_DETAILS
    ReminderDelivery.MAY_BE_DELAYED -> ReminderPermissionAction.OPEN_BATTERY_SAVER_SETTINGS
    ReminderDelivery.ON_TIME -> ReminderPermissionAction.OPEN_SYSTEM_SETTINGS
}
