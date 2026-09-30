package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.repository.ReminderStateRepository
import com.elinacn.subtrack.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * One run of the daily reminder: at most one notification per calendar day.
 *
 * Moved out of the worker in phase 16x so the rule can be tested without a device. The day is a
 * calendar question, not a clock one: a run that was late and went out at 08:00 has used up the
 * day, and the run aimed at 09:00 the same morning stays silent (PROJECT_SPEC: at most one
 * notification a day; ARCHITECTURE §18).
 */
class DailyReminder @Inject constructor(
    private val subscriptions: SubscriptionRepository,
    private val reminderState: ReminderStateRepository
) {

    /**
     * Decides whether [today] still needs a reminder and hands it to [post] if so.
     *
     * [post] answers whether the notification actually reached the user. Only then is the day
     * recorded: when notifications are off nothing was shown, and recording the day anyway would
     * swallow the reminder for a user who turns them back on an hour later.
     *
     * Returns whether this run showed a reminder.
     */
    suspend fun run(today: LocalDate, post: (List<PaymentReminder>) -> Boolean): Boolean {
        if (reminderState.lastNotifiedDay() == today.toEpochDay()) return false

        // A single read, not a subscription: a run decides once and ends.
        val reminders = PaymentReminderSelection.on(today, subscriptions.observeAll().first())

        // Nothing to say. The day is left unmarked on purpose, so a subscription added later
        // today can still produce a reminder on a later run.
        if (reminders.isEmpty()) return false

        if (!post(reminders)) return false
        reminderState.setLastNotifiedDay(today.toEpochDay())
        return true
    }
}
