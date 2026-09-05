package com.elinacn.subtrack.reminder

/**
 * Whether a reminder posted right now would actually reach the user.
 *
 * One source of truth for the question, asked both by the notifier before it posts and by the
 * settings screen before it draws a row. An interface rather than a class so the ViewModel can be
 * tested off a device: the implementation asks the platform, a fake answers from a field.
 */
interface ReminderNotificationStatus {

    /**
     * True when a posted reminder would be shown.
     *
     * Two questions, not one: the app can be allowed to notify while this particular channel is
     * silenced, and a notification on a silenced channel is dropped without a word.
     */
    fun areRemindersVisible(): Boolean

    /** True where posting needs a runtime permission, that is from Android 13 on. */
    fun isRuntimePermissionRequired(): Boolean

    /** True when the permission is held. Always true where none is required. */
    fun isPermissionGranted(): Boolean

    companion object {
        /** Id of the channel every payment reminder is posted to. */
        const val CHANNEL_ID = "payment_reminders"
    }
}
