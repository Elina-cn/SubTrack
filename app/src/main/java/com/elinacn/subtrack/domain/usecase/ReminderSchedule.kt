package com.elinacn.subtrack.domain.usecase

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When the daily reminder should run next, on the user's own clock.
 *
 * Every run is aimed at the next occurrence of one local time of day, worked out from the moment
 * it is asked - never from when the previous run happened. A run that was late therefore leaves
 * the following days where they were. Until phase 16x the job repeated every 24 hours from its
 * last run, and a week of late runs moved 09:00 to 10:02 (PROGRESS 16w).
 */
object ReminderSchedule {

    /** The time of day the reminder aims for when none has been stored. */
    val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

    /**
     * The first moment strictly after [now] at which the clock in [zone] shows [time].
     *
     * Strictly after, so a run that finishes exactly on its target is sent on to tomorrow rather
     * than to itself. On the day the clocks go forward a time inside the missing hour is moved on
     * by the length of the gap, and on the day they go back a time that happens twice is taken at
     * its first occurrence - both are [ZonedDateTime.of]'s own rules, so the day is always one
     * calendar day, 23 or 25 hours long when it has to be.
     */
    fun nextRunAfter(now: Instant, zone: ZoneId, time: LocalTime): Instant {
        val today = now.atZone(zone).toLocalDate()
        val todaysRun = ZonedDateTime.of(today, time, zone).toInstant()
        return if (todaysRun.isAfter(now)) {
            todaysRun
        } else {
            ZonedDateTime.of(today.plusDays(1), time, zone).toInstant()
        }
    }

    /**
     * When the job should run once the user has just chosen [time].
     *
     * The start-up rule for a job whose moment has come - it runs now rather than being moved to
     * tomorrow - applied to the new moment. If [time] has already come today and no reminder has
     * been shown today ([lastNotifiedDay], an epoch day), the answer is [now]: today's reminder is
     * still owed. If one has been shown today the day is used up, and the answer is [time]'s next
     * occurrence, tomorrow. Otherwise [time] is still ahead today and that is the answer, shown
     * reminder or not: the day guard in [DailyReminder] keeps a second run silent, not the
     * schedule.
     *
     * "Today" is the calendar day in [zone], the same zone the target is worked out in.
     */
    fun runAfterTimeChange(
        now: Instant,
        zone: ZoneId,
        time: LocalTime,
        lastNotifiedDay: Long?
    ): Instant {
        val today = now.atZone(zone).toLocalDate()
        val todaysRun = ZonedDateTime.of(today, time, zone).toInstant()
        val isTodayOwed = !todaysRun.isAfter(now) && lastNotifiedDay != today.toEpochDay()
        return if (isTodayOwed) now else nextRunAfter(now, zone, time)
    }

    /**
     * Whether the queued job has to be set again for [target].
     *
     * Only a job that is waiting for a future moment other than [target] is moved: a time zone or
     * target change, or a job left over from the 24-hour repeat. A [target] of now - a new time
     * whose reminder is still owed today - moves a waiting job to now. A job that is running, or
     * whose moment has already come, is left alone - it runs now and sets the next target itself,
     * and moving it to tomorrow would drop today's reminder. That is what makes this safe to ask on
     * every start: asking again on the same day gives the same [target], so nothing is pushed back.
     */
    fun needsReschedule(queued: QueuedReminder, now: Instant, target: Instant): Boolean =
        when (queued) {
            QueuedReminder.None -> true
            QueuedReminder.Running -> false
            is QueuedReminder.Waiting -> queued.runAt.isAfter(now) && queued.runAt != target
        }
}

/** The reminder job as the scheduler finds it. */
sealed interface QueuedReminder {

    /** No job at all, or only a finished one. */
    data object None : QueuedReminder

    /** A run is in progress. */
    data object Running : QueuedReminder

    /** Waiting to run at [runAt]. */
    data class Waiting(val runAt: Instant) : QueuedReminder
}
