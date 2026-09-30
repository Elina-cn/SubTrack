package com.elinacn.subtrack.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elinacn.subtrack.domain.usecase.DailyReminder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.LocalDate

/**
 * Runs once a day and notifies about payments that are due today or due tomorrow.
 *
 * The decision itself is [DailyReminder]'s; this class supplies the day, the notifier, and the
 * next run.
 *
 * Deliberately without catch. There is no surface here to show an error on, an unhandled failure
 * is already reported by WorkManager, and catching would only hide it, which ARCHITECTURE
 * section 9 forbids.
 */
@HiltWorker
class PaymentReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val dailyReminder: DailyReminder,
    private val notifier: PaymentReminderNotifier,
    private val scheduler: PaymentReminderScheduler,
    private val clock: Clock
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        try {
            dailyReminder.run(LocalDate.now(clock)) { reminders -> notifier.notify(reminders) }
        } finally {
            // Pinned whether the run succeeded or threw, so the next reminder time is always the
            // next one queued. A run the system stopped part-way is the exception: WorkManager
            // retries it shortly, and pinning tomorrow here would push that retry - today's
            // reminder - to tomorrow. The retry pins when it finishes.
            if (!isStopped) scheduler.pinNextRun()
        }

        // Success whatever was shown: notifications being off is a choice, not a failure, and
        // retrying would only burn battery waiting for a setting this job cannot change.
        return Result.success()
    }
}
