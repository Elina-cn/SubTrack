package com.elinacn.subtrack.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elinacn.subtrack.domain.repository.ReminderStateRepository
import com.elinacn.subtrack.domain.repository.SubscriptionRepository
import com.elinacn.subtrack.domain.usecase.PaymentReminderSelection
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

/**
 * Runs once a day and notifies about payments that are due, nearly due, or recently overdue.
 *
 * Deliberately without try/catch. There is no surface here to show an error on, an unhandled
 * failure is already reported by WorkManager, and the next day's run retries anyway - catching
 * would only hide it, which ARCHITECTURE section 9 forbids.
 */
@HiltWorker
class PaymentReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val subscriptions: SubscriptionRepository,
    private val reminderState: ReminderStateRepository,
    private val notifier: PaymentReminderNotifier,
    private val clock: Clock
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val today = LocalDate.now(clock)

        // At most one reminder a day, however often the job happens to run. WorkManager may run a
        // periodic job more than once in a window, and the app also enqueues on every launch.
        if (reminderState.lastNotifiedDay() == today.toEpochDay()) return Result.success()

        // A single read, not a subscription: this job decides once and ends.
        val reminders = PaymentReminderSelection.on(today, subscriptions.observeAll().first())

        // Nothing to say. The day is left unmarked on purpose, so a subscription added later
        // today can still produce a reminder on the next run.
        if (reminders.isEmpty()) return Result.success()

        // Only a reminder that actually reached the shade counts. When notifications are switched
        // off nothing was shown, so recording the day would swallow the reminder for good - the
        // user who turns them back on an hour later would still see nothing until tomorrow.
        if (notifier.notify(reminders)) {
            reminderState.setLastNotifiedDay(today.toEpochDay())
        }

        // Success either way: notifications being off is a choice, not a failure, and retrying
        // would only burn battery waiting for a setting this job cannot change.
        return Result.success()
    }
}
