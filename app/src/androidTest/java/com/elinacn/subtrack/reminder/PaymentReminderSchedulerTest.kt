package com.elinacn.subtrack.reminder

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.elinacn.subtrack.data.repository.SettingsRepositoryImpl
import com.elinacn.subtrack.debug.PreferencesStoreEntryPoint
import com.elinacn.subtrack.domain.usecase.ReminderSchedule
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The scheduler against the app's real WorkManager: one job, at the next reminder time.
 *
 * Unit tests cover the arithmetic; only a device can say whether WorkManager accepts the requests.
 * Phase 16x found that the hard way - a pinned next run enqueued with CANCEL_AND_REENQUEUE is
 * rejected with an exception, and the first launch after install crashed on the emulator.
 *
 * The scheduler reads the reminder time through the app's own preference store, the one the app
 * uses; a second store over the same file is a runtime error (ARCHITECTURE section 14).
 */
@RunWith(AndroidJUnit4::class)
class PaymentReminderSchedulerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val workManager = WorkManager.getInstance(context)

    private val settings = SettingsRepositoryImpl(
        EntryPointAccessors
            .fromApplication(context.applicationContext, PreferencesStoreEntryPoint::class.java)
            .preferencesDataStore()
    )

    private val scheduler = PaymentReminderScheduler(context, settings, Clock.systemDefaultZone())

    /** Starts from no job at all, as a fresh install does. */
    @Before
    fun clearTheJob() {
        workManager.cancelUniqueWork(WORK_NAME).result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        workManager.pruneWork().result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    @Test
    fun ensureScheduled_fromNothing_queuesOneJobAtTheNextReminderTime() = runBlocking {
        scheduler.ensureScheduled()

        val job = onlyJob()
        assertEquals(WorkInfo.State.ENQUEUED, job.state)
        assertEquals(expectedTarget(), job.nextScheduleTimeMillis)
    }

    @Test
    fun ensureScheduled_again_leavesTheSameJobWhereItWas() = runBlocking {
        scheduler.ensureScheduled()
        val first = onlyJob()

        scheduler.ensureScheduled()
        val second = onlyJob()

        assertEquals(first.id, second.id)
        assertEquals(first.nextScheduleTimeMillis, second.nextScheduleTimeMillis)
    }

    @Test
    fun pinNextRun_keepsOneJobAtTheNextReminderTime() = runBlocking {
        scheduler.ensureScheduled()
        val queued = onlyJob()

        scheduler.pinNextRun()
        val pinned = onlyJob()

        assertEquals(queued.id, pinned.id)
        assertEquals(expectedTarget(), pinned.nextScheduleTimeMillis)
    }

    /** Every record under the name, finished or not: the point is that there is only one. */
    private fun onlyJob(): WorkInfo {
        val jobs = workManager.getWorkInfosForUniqueWork(WORK_NAME).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertEquals("jobs under $WORK_NAME: $jobs", 1, jobs.size)
        return jobs.single()
    }

    private suspend fun expectedTarget(): Long =
        ReminderSchedule.nextRunAfter(
            now = Instant.now(),
            zone = ZoneId.systemDefault(),
            time = settings.observeReminderTime().first()
        ).toEpochMilli()

    private companion object {
        /** The scheduler's unique name, repeated here because the scheduler keeps it private. */
        const val WORK_NAME = "payment_reminder"

        const val TIMEOUT_SECONDS = 10L
    }
}
