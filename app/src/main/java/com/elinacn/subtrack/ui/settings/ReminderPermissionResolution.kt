package com.elinacn.subtrack.ui.settings

import com.elinacn.subtrack.reminder.ReminderNotificationStatus

/**
 * The reminder row's decision table (ARCHITECTURE section 18), with "not known yet" as an answer.
 *
 * The first three rows need only the platform, which answers on the spot, so they are settled
 * before the screen's first frame. The last three need the stored "asked before" flag, and the last
 * two also need the Activity's rationale answer; until those have arrived the result is null
 * rather than a guess (ARCHITECTURE section 29).
 */
internal fun ReminderNotificationStatus.resolveReminderPermission(
    wasRequested: Boolean?,
    canShowRationale: Boolean?
): ReminderPermissionState? = when {
    // Covers every build: on older ones there is no permission to hold, only the switch.
    areRemindersVisible() -> ReminderPermissionState.ENABLED

    // No runtime permission on this build, so nothing to request - the switch or the channel is
    // off and only the system screen can undo that.
    !isRuntimePermissionRequired() -> ReminderPermissionState.SETTINGS_ONLY

    // Permission held but reminders still invisible: the app switch or this channel is off.
    isPermissionGranted() -> ReminderPermissionState.SETTINGS_ONLY

    wasRequested == null -> null

    // Never asked. The system would say "no rationale needed" here too, which is exactly why the
    // flag exists rather than trusting the system's answer alone.
    !wasRequested -> ReminderPermissionState.CAN_REQUEST

    canShowRationale == null -> null

    // Asked before and the system still lets us explain: one more request is allowed.
    canShowRationale -> ReminderPermissionState.CAN_REQUEST

    // Asked, no rationale offered: denied for good, the system screen is the only way back.
    else -> ReminderPermissionState.SETTINGS_ONLY
}
