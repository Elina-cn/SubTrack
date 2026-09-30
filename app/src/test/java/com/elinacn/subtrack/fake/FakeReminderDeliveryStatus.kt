package com.elinacn.subtrack.fake

import com.elinacn.subtrack.reminder.ReminderDeliveryStatus

/**
 * Answers the delivery questions from fields instead of the platform.
 *
 * The fields are vars so a test can change the device "while the user was away" and check that
 * coming back to the screen picks it up.
 */
class FakeReminderDeliveryStatus(
    var backgroundRestricted: Boolean = false,
    var powerSaveMode: Boolean = false
) : ReminderDeliveryStatus {

    override fun isBackgroundRestricted(): Boolean = backgroundRestricted

    override fun isPowerSaveMode(): Boolean = powerSaveMode
}
