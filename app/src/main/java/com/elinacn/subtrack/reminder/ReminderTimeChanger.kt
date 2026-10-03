package com.elinacn.subtrack.reminder

import java.time.LocalTime

/**
 * Changes the time of day the payment reminder aims for.
 *
 * The settings screen's way in, rather than the settings store itself, because a new time is only
 * half done once stored: the queued job has to follow it. An interface for the same reason as
 * [ReminderDeliveryStatus] - the settings ViewModel is tested off a device, and the implementation
 * drives WorkManager.
 */
interface ReminderTimeChanger {

    /** Stores [time] and has the daily job follow it before returning. */
    suspend fun change(time: LocalTime)
}
