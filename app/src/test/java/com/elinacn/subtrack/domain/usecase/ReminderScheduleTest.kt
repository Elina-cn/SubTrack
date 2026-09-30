package com.elinacn.subtrack.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Where the next reminder run lands, and when a queued job has to be moved.
 *
 * Istanbul for the ordinary cases - no daylight saving since 2016, so every day is 24 hours - and
 * New York for the clock changes, the same zone as the API 29 emulator.
 */
class ReminderScheduleTest {

    private val istanbul: ZoneId = ZoneId.of("Europe/Istanbul")
    private val newYork: ZoneId = ZoneId.of("America/New_York")
    private val nine: LocalTime = ReminderSchedule.DEFAULT_TIME

    // --- the next occurrence ----------------------------------------------------------------

    @Test
    fun defaultTime_isNineInTheMorning() {
        assertEquals(LocalTime.of(9, 0), ReminderSchedule.DEFAULT_TIME)
    }

    @Test
    fun beforeTheTarget_isTodaysTarget() {
        val next = ReminderSchedule.nextRunAfter(at(2026, 10, 1, 8, 30), istanbul, nine)

        assertEquals(at(2026, 10, 1, 9, 0), next)
    }

    @Test
    fun afterTheTarget_isTomorrowsTarget() {
        val next = ReminderSchedule.nextRunAfter(at(2026, 10, 1, 9, 30), istanbul, nine)

        assertEquals(at(2026, 10, 2, 9, 0), next)
    }

    @Test
    fun exactlyOnTheTarget_isTomorrow() {
        // A run that ends on its own target must not be aimed at itself.
        val next = ReminderSchedule.nextRunAfter(at(2026, 10, 1, 9, 0), istanbul, nine)

        assertEquals(at(2026, 10, 2, 9, 0), next)
    }

    @Test
    fun midnightTarget_justBeforeMidnight_isTheComingMidnight() {
        val next = ReminderSchedule.nextRunAfter(at(2026, 10, 1, 23, 59), istanbul, LocalTime.MIDNIGHT)

        assertEquals(at(2026, 10, 2, 0, 0), next)
    }

    @Test
    fun atMidnight_theMorningTargetIsTheSameDate() {
        val next = ReminderSchedule.nextRunAfter(at(2026, 10, 2, 0, 0), istanbul, nine)

        assertEquals(at(2026, 10, 2, 9, 0), next)
    }

    // --- a late run does not move the following days ------------------------------------------

    @Test
    fun lateRun_theNextRunIsStillTomorrowAtNine() {
        // The job was due at 09:00 and only ran at 14:05, when the user opened the app.
        val next = ReminderSchedule.nextRunAfter(at(2026, 10, 1, 14, 5), istanbul, nine)

        assertEquals(at(2026, 10, 2, 9, 0), next)
    }

    @Test
    fun lateRunEarlyNextMorning_isFollowedByThatMorningsNine() {
        // Yesterday's run was held back overnight and ran at 08:00. Today's target is still 09:00;
        // the day guard, not the schedule, keeps that run from notifying twice (DailyReminderTest).
        val next = ReminderSchedule.nextRunAfter(at(2026, 10, 2, 8, 0), istanbul, nine)

        assertEquals(at(2026, 10, 2, 9, 0), next)
    }

    @Test
    fun aWeekOfLateRuns_neverMovesTheTarget() {
        // Phase 16w measured the old 24-hour repeat drifting from 09:00 to 10:02 in a week. Here
        // every run is an hour late and every next target is still 09:00.
        var target = at(2026, 10, 1, 9, 0)
        repeat(7) {
            val lateRun = target.plus(Duration.ofMinutes(62))
            val next = ReminderSchedule.nextRunAfter(lateRun, istanbul, nine)

            assertEquals(LocalTime.of(9, 0), next.atZone(istanbul).toLocalTime())
            assertEquals(target.plus(Duration.ofDays(1)), next)
            target = next
        }
    }

    // --- daylight saving -----------------------------------------------------------------------

    @Test
    fun springForward_nineIsNineAgain_theDayIs23Hours() {
        // New York moves from 02:00 to 03:00 on the 8th of March 2026.
        val saturday = at(2026, 3, 7, 9, 0, newYork)

        val next = ReminderSchedule.nextRunAfter(at(2026, 3, 7, 9, 30, newYork), newYork, nine)

        assertEquals(at(2026, 3, 8, 9, 0, newYork), next)
        assertEquals(LocalTime.of(9, 0), next.atZone(newYork).toLocalTime())
        assertEquals(Duration.ofHours(23), Duration.between(saturday, next))
    }

    @Test
    fun springForward_aTargetInTheMissingHour_movesPastIt() {
        // 02:30 does not exist that morning; the clock shows 03:30 an hour after 01:30.
        val next = ReminderSchedule.nextRunAfter(at(2026, 3, 8, 0, 0, newYork), newYork, LocalTime.of(2, 30))

        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), next)
        assertEquals(LocalTime.of(3, 30), next.atZone(newYork).toLocalTime())
    }

    @Test
    fun fallBack_nineIsNineAgain_theDayIs25Hours() {
        // New York moves from 02:00 back to 01:00 on the 1st of November 2026.
        val saturday = at(2026, 10, 31, 9, 0, newYork)

        val next = ReminderSchedule.nextRunAfter(at(2026, 10, 31, 9, 30, newYork), newYork, nine)

        assertEquals(at(2026, 11, 1, 9, 0, newYork), next)
        assertEquals(Duration.ofHours(25), Duration.between(saturday, next))
    }

    @Test
    fun fallBack_aTargetInTheRepeatedHour_isTheFirstOfTheTwo() {
        val next = ReminderSchedule.nextRunAfter(at(2026, 11, 1, 0, 0, newYork), newYork, LocalTime.of(1, 30))

        // 01:30 daylight time, UTC-4 - not the second 01:30 an hour later.
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), next)
    }

    // --- when a queued job is moved ------------------------------------------------------------

    @Test
    fun needsReschedule_noJob_isYes() {
        val now = at(2026, 10, 1, 12, 0)

        assertTrue(ReminderSchedule.needsReschedule(QueuedReminder.None, now, at(2026, 10, 2, 9, 0)))
    }

    @Test
    fun needsReschedule_aRunningJob_isLeftAlone() {
        val now = at(2026, 10, 1, 9, 0)

        assertFalse(ReminderSchedule.needsReschedule(QueuedReminder.Running, now, at(2026, 10, 2, 9, 0)))
    }

    @Test
    fun needsReschedule_waitingForTheTarget_isLeftAlone() {
        // The same day, opened again: the same target, so nothing is pushed back.
        val now = at(2026, 10, 1, 12, 0)
        val target = at(2026, 10, 2, 9, 0)

        assertFalse(ReminderSchedule.needsReschedule(QueuedReminder.Waiting(target), now, target))
    }

    @Test
    fun needsReschedule_waitingForAnotherTime_isMoved() {
        // 1.0.3's 24-hour repeat after a week of drift: tomorrow at 10:02 instead of 09:00.
        val now = at(2026, 10, 1, 12, 0)

        assertTrue(
            ReminderSchedule.needsReschedule(
                QueuedReminder.Waiting(at(2026, 10, 2, 10, 2)),
                now,
                at(2026, 10, 2, 9, 0)
            )
        )
    }

    @Test
    fun needsReschedule_alreadyDue_isLeftToRun() {
        // Force-stopped at 09:00, opened at 14:00: the job is overdue and runs now. Moving it to
        // tomorrow would drop today's reminder.
        val now = at(2026, 10, 1, 14, 0)

        assertFalse(
            ReminderSchedule.needsReschedule(
                QueuedReminder.Waiting(at(2026, 10, 1, 9, 0)),
                now,
                at(2026, 10, 2, 9, 0)
            )
        )
    }

    @Test
    fun needsReschedule_dueThisVeryMoment_isLeftToRun() {
        val now = at(2026, 10, 1, 9, 0)

        assertFalse(ReminderSchedule.needsReschedule(QueuedReminder.Waiting(now), now, at(2026, 10, 2, 9, 0)))
    }

    @Test
    fun needsReschedule_afterATimeZoneChange_isMoved() {
        // Queued for 09:00 in Istanbul; the phone is now in New York, where 09:00 is 7 hours later.
        val now = at(2026, 10, 1, 20, 0)
        val queued = QueuedReminder.Waiting(at(2026, 10, 2, 9, 0))
        val target = ReminderSchedule.nextRunAfter(now, newYork, nine)

        assertEquals(at(2026, 10, 2, 9, 0, newYork), target)
        assertTrue(ReminderSchedule.needsReschedule(queued, now, target))
    }

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zone: ZoneId = istanbul
    ): Instant = LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant()
}
