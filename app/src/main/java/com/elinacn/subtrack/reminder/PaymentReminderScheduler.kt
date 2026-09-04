package com.elinacn.subtrack.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Keeps the daily reminder job queued. */
class PaymentReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock
) {

    /**
     * Queues the daily job, leaving an existing one alone.
     *
     * KEEP is not a preference, it is the only correct policy here: this runs on every launch,
     * and UPDATE or REPLACE would reset the initial delay each time, so a user who opens the app
     * daily would never reach the first run.
     *
     * No constraints. The job reads local data and posts a notification - it needs no network, no
     * charger and no idle device, and asking for any of those would only delay it.
     */
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<PaymentReminderWorker>(REPEAT_INTERVAL_DAYS, TimeUnit.DAYS)
            .setInitialDelay(secondsUntilNextRun(), TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Seconds from now to the next local [RUN_AT_HOUR], today's if it has not passed yet.
     *
     * Read through the injected clock rather than the system one, and through [ZonedDateTime] so
     * the answer follows the zone's own rules on the days a clock change makes shorter or longer.
     */
    private fun secondsUntilNextRun(): Long {
        val now = ZonedDateTime.now(clock)
        val todaysRun = now.with(LocalTime.of(RUN_AT_HOUR, 0))
        val nextRun = if (todaysRun.isAfter(now)) todaysRun else todaysRun.plusDays(1)
        return Duration.between(now, nextRun).seconds
    }

    private companion object {
        /** Unique name of the periodic work; the same name is what KEEP compares against. */
        const val WORK_NAME = "payment_reminder"

        const val REPEAT_INTERVAL_DAYS = 1L

        /** Local hour the reminder aims for. WorkManager treats it as a target, not a promise. */
        const val RUN_AT_HOUR = 9
    }
}
