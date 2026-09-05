package com.elinacn.subtrack.fake

import com.elinacn.subtrack.reminder.ReminderNotificationStatus

/**
 * Answers the notification questions from fields instead of the platform.
 *
 * This is why [ReminderNotificationStatus] is an interface: the settings state machine has seven
 * branches and none of them can be reached from a unit test through a real NotificationManager.
 */
class FakeReminderNotificationStatus(
    var remindersVisible: Boolean = false,
    var runtimePermissionRequired: Boolean = true,
    var permissionGranted: Boolean = false
) : ReminderNotificationStatus {

    override fun areRemindersVisible(): Boolean = remindersVisible

    override fun isRuntimePermissionRequired(): Boolean = runtimePermissionRequired

    override fun isPermissionGranted(): Boolean =
        !runtimePermissionRequired || permissionGranted
}
