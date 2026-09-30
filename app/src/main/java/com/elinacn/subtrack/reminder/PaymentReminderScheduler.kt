package com.elinacn.subtrack.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.elinacn.subtrack.domain.repository.SettingsRepository
import com.elinacn.subtrack.domain.usecase.QueuedReminder
import com.elinacn.subtrack.domain.usecase.ReminderSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps exactly one daily reminder job queued, aimed at the next occurrence of the reminder time.
 *
 * Still a periodic job, now with its next run pinned by hand: every run, and every start of the
 * app, sets the next run to the reminder time's next occurrence through
 * [PeriodicWorkRequest.Builder.setNextScheduleTimeOverride]. The 24-hour period only matters if a
 * run ends without pinning - it keeps a next run queued whatever happens to one run, which a chain
 * of one-time jobs cannot promise. See ARCHITECTURE §18.
 */
@Singleton
class PaymentReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val clock: Clock
) {

    /** One check at a time, so two starts close together cannot both enqueue. */
    private val mutex = Mutex()

    /**
     * Makes sure a job is queued for the next reminder time, and changes it only when it has to.
     *
     * Called whenever the app comes to the front. A job that is waiting for the right moment is
     * left alone, so opening the app every day never pushes the reminder back - the reason the old
     * code insisted on KEEP. A missing job is created, and one waiting for another moment - the
     * time zone changed, or a job left over from 1.0.3's 24-hour repeat - is moved. A job that is
     * running or already due is left to run: it pins the next day itself.
     */
    suspend fun ensureScheduled() {
        mutex.withLock {
            val now = clock.instant()
            val target = nextTarget(now)
            val queued = queuedReminder()
            if (!ReminderSchedule.needsReschedule(queued, now, target)) return

            // Nothing queued: replace, which also clears any finished record under the name, so
            // exactly one job is left. Something waiting: update it in place, which never cancels
            // a run that has just begun.
            val policy = if (queued == QueuedReminder.None) {
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
            } else {
                ExistingPeriodicWorkPolicy.UPDATE
            }
            enqueue(target, policy)
        }
    }

    /**
     * Sets the job's next run to the reminder time's next occurrence after now.
     *
     * Called at the end of every run. UPDATE is what makes this safe from inside the running job:
     * WorkManager applies the new time to the next run and keeps it when the current one finishes.
     */
    suspend fun pinNextRun() {
        mutex.withLock {
            enqueue(nextTarget(clock.instant()), ExistingPeriodicWorkPolicy.UPDATE)
        }
    }

    /**
     * The reminder time's next occurrence after [now].
     *
     * The zone is read from the system on every call rather than from [clock], which fixes its
     * zone once when the process starts: a device that crossed a time zone with the app still in
     * memory has to aim at 09:00 where it is now.
     */
    private suspend fun nextTarget(now: Instant): Instant =
        ReminderSchedule.nextRunAfter(
            now = now,
            zone = ZoneId.systemDefault(),
            time = settings.observeReminderTime().first()
        )

    private suspend fun queuedReminder(): QueuedReminder {
        val work = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(WORK_NAME)
            .first()
            .firstOrNull { !it.state.isFinished }
        return when (work?.state) {
            null -> QueuedReminder.None
            WorkInfo.State.ENQUEUED ->
                QueuedReminder.Waiting(Instant.ofEpochMilli(work.nextScheduleTimeMillis))
            // Running, or blocked behind something - neither is ours to move.
            else -> QueuedReminder.Running
        }
    }

    private suspend fun enqueue(target: Instant, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<PaymentReminderWorker>(
            REPEAT_INTERVAL_DAYS,
            TimeUnit.DAYS
        )
            .setNextScheduleTimeOverride(target.toEpochMilli())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, policy, request)
            .await()
    }

    private companion object {
        /**
         * Unique name of the job. Unchanged since 1.0.0, so the update finds 1.0.3's periodic job
         * under it and replaces its schedule instead of adding a second job next to it.
         */
        const val WORK_NAME = "payment_reminder"

        /** The fallback between runs; the pinned time decides when a run actually happens. */
        const val REPEAT_INTERVAL_DAYS = 1L
    }
}
