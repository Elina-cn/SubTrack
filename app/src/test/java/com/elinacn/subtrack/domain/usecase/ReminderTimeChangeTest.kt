package com.elinacn.subtrack.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Where the job goes right after the user chooses a new reminder time.
 *
 * Istanbul throughout - no daylight saving, so every day is 24 hours. "Today" is the 2nd of
 * October 2026 unless a test says otherwise.
 */
class ReminderTimeChangeTest {

    private val istanbul: ZoneId = ZoneId.of("Europe/Istanbul")
    private val today: Long = LocalDate.of(2026, 10, 2).toEpochDay()
    private val yesterday: Long = today - 1

    // --- moved later --------------------------------------------------------------------------

    @Test
    fun movedLater_stillAheadToday_isTodayAtTheNewTime() {
        val target = change(now = at(2, 12, 0), time = LocalTime.of(18, 0), lastNotifiedDay = yesterday)

        assertEquals(at(2, 18, 0), target)
    }

    @Test
    fun movedLater_afterTodaysReminderWasShown_isStillToday() {
        // The run at 18:00 stays silent: the day guard decides that, not the schedule
        // (DailyReminderTest). Sending it to tomorrow here would be a second rule for one question.
        val target = change(now = at(2, 12, 0), time = LocalTime.of(18, 0), lastNotifiedDay = today)

        assertEquals(at(2, 18, 0), target)
    }

    // --- moved earlier ------------------------------------------------------------------------

    @Test
    fun movedEarlier_alreadyPassed_nothingShownToday_runsNow() {
        val now = at(2, 12, 0)

        val target = change(now = now, time = LocalTime.of(8, 0), lastNotifiedDay = yesterday)

        assertEquals(now, target)
    }

    @Test
    fun movedEarlier_alreadyPassed_neverShownAtAll_runsNow() {
        val now = at(2, 12, 0)

        val target = change(now = now, time = LocalTime.of(8, 0), lastNotifiedDay = null)

        assertEquals(now, target)
    }

    @Test
    fun movedEarlier_alreadyPassed_todaysReminderShown_isTomorrowAtTheNewTime() {
        val target = change(now = at(2, 12, 0), time = LocalTime.of(8, 0), lastNotifiedDay = today)

        assertEquals(at(3, 8, 0), target)
    }

    @Test
    fun movedEarlier_butStillAheadToday_isTodayAtTheNewTime() {
        // 07:00, the job waits for 09:00, the user picks 08:00: nothing is owed yet.
        val target = change(now = at(2, 7, 0), time = LocalTime.of(8, 0), lastNotifiedDay = yesterday)

        assertEquals(at(2, 8, 0), target)
    }

    @Test
    fun theMinuteItIsNow_nothingShownToday_runsNow() {
        val now = at(2, 12, 0)

        val target = change(now = now, time = LocalTime.of(12, 0), lastNotifiedDay = yesterday)

        assertEquals(now, target)
    }

    @Test
    fun theMinuteItIsNow_todaysReminderShown_isTomorrow() {
        val target = change(now = at(2, 12, 0), time = LocalTime.of(12, 0), lastNotifiedDay = today)

        assertEquals(at(3, 12, 0), target)
    }

    // --- close to midnight --------------------------------------------------------------------

    @Test
    fun lateEvening_aFewMinutesLater_isTonight() {
        val target = change(now = at(2, 23, 50), time = LocalTime.of(23, 55), lastNotifiedDay = yesterday)

        assertEquals(at(2, 23, 55), target)
    }

    @Test
    fun lateEvening_justAfterMidnight_nothingShownToday_runsNow() {
        // 00:05 today was hours ago, so today's reminder is still owed - it is not tomorrow's.
        val now = at(2, 23, 50)

        val target = change(now = now, time = LocalTime.of(0, 5), lastNotifiedDay = yesterday)

        assertEquals(now, target)
    }

    @Test
    fun lateEvening_justAfterMidnight_todaysReminderShown_isFifteenMinutesAway() {
        val now = at(2, 23, 50)

        val target = change(now = now, time = LocalTime.of(0, 5), lastNotifiedDay = today)

        assertEquals(at(3, 0, 5), target)
        assertEquals(Duration.ofMinutes(15), Duration.between(now, target))
    }

    @Test
    fun justAfterMidnight_lateEveningTime_isTonightNotTomorrow() {
        val target = change(now = at(2, 0, 5), time = LocalTime.of(23, 55), lastNotifiedDay = yesterday)

        assertEquals(at(2, 23, 55), target)
    }

    @Test
    fun justAfterMidnight_yesterdaysReminderDoesNotCountForToday() {
        // Shown at 23:58 last night; a minute after midnight is a new day with its own reminder.
        val now = at(2, 0, 1)

        val target = change(now = now, time = LocalTime.MIDNIGHT, lastNotifiedDay = yesterday)

        assertEquals(now, target)
    }

    @Test
    fun today_isTheCalendarDayInTheZone() {
        // 22:30 UTC on the 2nd is already 01:30 on the 3rd in Istanbul. The reminder shown on the
        // 2nd is yesterday's there, so a 01:00 that has just passed is owed.
        val now = Instant.parse("2026-10-02T22:30:00Z")

        val target = change(now = now, time = LocalTime.of(1, 0), lastNotifiedDay = today)

        assertEquals(now, target)
    }

    // --- what happens to the queued job -------------------------------------------------------

    @Test
    fun owedToday_aJobWaitingForTheOldTime_isMovedToNow() {
        // The job waits for tomorrow 09:00; the user picks 08:00 at noon with nothing shown today.
        val now = at(2, 12, 0)
        val target = change(now = now, time = LocalTime.of(8, 0), lastNotifiedDay = yesterday)

        assertTrue(ReminderSchedule.needsReschedule(QueuedReminder.Waiting(at(3, 9, 0)), now, target))
    }

    @Test
    fun owedToday_aJobAlreadyDue_isLeftToRun() {
        val now = at(2, 12, 0)
        val target = change(now = now, time = LocalTime.of(8, 0), lastNotifiedDay = yesterday)

        assertFalse(ReminderSchedule.needsReschedule(QueuedReminder.Waiting(at(2, 9, 0)), now, target))
    }

    @Test
    fun aRunningJob_isLeftAlone_itPinsTheNewTimeItself() {
        val now = at(2, 12, 0)
        val target = change(now = now, time = LocalTime.of(18, 0), lastNotifiedDay = yesterday)

        assertFalse(ReminderSchedule.needsReschedule(QueuedReminder.Running, now, target))
    }

    @Test
    fun movedLater_aJobWaitingForTomorrow_isBroughtBackToToday() {
        val now = at(2, 12, 0)
        val target = change(now = now, time = LocalTime.of(18, 0), lastNotifiedDay = today)

        assertTrue(ReminderSchedule.needsReschedule(QueuedReminder.Waiting(at(3, 9, 0)), now, target))
        assertEquals(at(2, 18, 0), target)
    }

    private fun change(now: Instant, time: LocalTime, lastNotifiedDay: Long?): Instant =
        ReminderSchedule.runAfterTimeChange(now, istanbul, time, lastNotifiedDay)

    /** A moment in October 2026, Istanbul time. */
    private fun at(day: Int, hour: Int, minute: Int): Instant =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(istanbul).toInstant()
}
