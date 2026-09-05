package com.elinacn.subtrack.domain.repository

/**
 * Remembers when a reminder was last shown, so at most one goes out per day.
 *
 * Deliberately separate from [SettingsRepository]: nothing here is a user preference. It is
 * bookkeeping the reminder job writes about itself, and it has no screen. See ARCHITECTURE §18.
 */
interface ReminderStateRepository {

    /** The day a reminder was last shown, as an epoch day, or null when none ever was. */
    suspend fun lastNotifiedDay(): Long?

    /** Records that a reminder went out on [epochDay]. */
    suspend fun setLastNotifiedDay(epochDay: Long)

    /**
     * Whether the notification permission has ever been asked for.
     *
     * The system cannot answer this: it gives the same "no rationale needed" for a permission
     * never requested and one denied for good, so only our own record tells the two apart. See
     * ARCHITECTURE §18.
     */
    suspend fun wasPermissionRequested(): Boolean

    /** Records that the permission request was put to the user, whatever they answered. */
    suspend fun setPermissionRequested()
}
