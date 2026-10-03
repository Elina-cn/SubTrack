package com.elinacn.subtrack.fake

import com.elinacn.subtrack.reminder.ReminderTimeChanger
import java.time.LocalTime

/**
 * Stores the time in a [FakeSettingsRepository] the way the scheduler stores it in the real one,
 * and leaves the job out: moving it is WorkManager's business, covered by ReminderScheduleTest and
 * on the emulator.
 *
 * Writing through the fake store rather than only recording the call is what lets a test check
 * that the screen shows the new time because the store said so.
 */
class FakeReminderTimeChanger(private val settings: FakeSettingsRepository) : ReminderTimeChanger {

    /** Every time handed over, in order. */
    val changes = mutableListOf<LocalTime>()

    override suspend fun change(time: LocalTime) {
        changes += time
        settings.setReminderTime(time)
    }
}
