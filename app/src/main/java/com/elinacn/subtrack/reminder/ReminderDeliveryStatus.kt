package com.elinacn.subtrack.reminder

/**
 * Whether the system will hold reminders back even though they are switched on.
 *
 * The two things phase 16w measured stopping the reminder job until the app was opened, and the
 * only two the app can see: a manufacturer's own battery rules and third-party cleaners give no
 * answer to ask. An interface for the same reason as [ReminderNotificationStatus] - the settings
 * ViewModel is tested off a device.
 */
interface ReminderDeliveryStatus {

    /**
     * True when the user has restricted this app's background activity. Reminders then wait for
     * the app to be opened. Always false below Android 9, which has no such switch to ask about.
     */
    fun isBackgroundRestricted(): Boolean

    /** True while battery saver is on. Reminders are held back until the app is opened, too. */
    fun isPowerSaveMode(): Boolean
}
