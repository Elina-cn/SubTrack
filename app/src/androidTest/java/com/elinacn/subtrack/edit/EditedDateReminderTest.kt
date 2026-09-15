package com.elinacn.subtrack.edit

import android.app.Notification
import android.app.NotificationManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.elinacn.subtrack.data.local.SubTrackDatabase
import com.elinacn.subtrack.data.mapper.toEntity
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.reminder.PaymentReminderWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * That editing a subscription's date changes what the reminder sees.
 *
 * Phase 15 added a second way to write the table, and the claim it rests on is that the reminder
 * needs no new wiring: the worker reads the list again on every run and advances each anchor from
 * scratch, so whatever is stored at that moment is what it reports. That is a claim about
 * behaviour, and this is it being run rather than reasoned about.
 *
 * The row is changed with the same `update` the edit screen's repository performs, through a
 * second Room instance over the app's own file - the way the reminder suite already seeds.
 *
 * **Run against freshly cleared app data.** The worker notifies at most once a day and records the
 * day it did, so a suite that has already notified today leaves nothing for this to observe. Run
 * it on its own, not alongside the reminder suite.
 */
@RunWith(AndroidJUnit4::class)
class EditedDateReminderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun editingTheDateToToday_bringsTheSubscriptionIntoTheReminder() {
        assumeTrue(
            "notifications are off; nothing can reach the shade to be read back",
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        )
        notificationManager.cancelAll()

        // A month out: far outside the window, so the first run has nothing to say and - because
        // nothing was posted - does not mark the day as done.
        val id = seedOneSubscription(EDITED_NAME, LocalDate.now().plusDays(30))

        val firstState = runWorkerOnce()
        Log.i(TAG, "before the edit: state=$firstState, shown=[${shownText()}]")
        assertEquals(WorkInfo.State.SUCCEEDED, firstState)
        assertFalse(
            "a subscription a month away should not be in the shade",
            shownText().contains(EDITED_NAME)
        )

        // What the edit screen does: the same row, the same id, a different date.
        moveDateTo(id, LocalDate.now())

        val secondState = runWorkerOnce()
        val shown = shownText()
        Log.i(TAG, "after the edit: state=$secondState, shown=[$shown]")
        assertEquals(WorkInfo.State.SUCCEEDED, secondState)
        assertNotNull("no notification was posted after the edit", activeNotificationText())
        assertTrue("the edited subscription is not in the shade: $shown", shown.contains(EDITED_NAME))
        // Said as today, not as a date that has passed: the anchor is re-read and re-advanced, so
        // an edit can only ever be reported against the day it now names.
        assertTrue("the reminder does not say it is due today: $shown", shown.containsDueToday())
    }

    /** Writes one row and hands back the id Room gave it. */
    private fun seedOneSubscription(name: String, date: LocalDate): Long {
        val database = Room
            .databaseBuilder(context, SubTrackDatabase::class.java, SubTrackDatabase.NAME)
            .build()
        return try {
            database.clearAllTables()
            runBlocking {
                database.subscriptionDao().insert(
                    Subscription(
                        id = 0,
                        name = name,
                        price = Money(1000),
                        currency = Currency.TRY,
                        billingPeriod = BillingPeriod.MONTHLY,
                        nextPaymentDate = date,
                        category = SubscriptionCategory.OTHER,
                        iconKey = null,
                        createdAt = 1L
                    ).toEntity()
                )
            }
        } finally {
            database.close()
        }
    }

    /** The one field this test is about, changed the way the edit screen changes it. */
    private fun moveDateTo(id: Long, date: LocalDate) {
        val database = Room
            .databaseBuilder(context, SubTrackDatabase::class.java, SubTrackDatabase.NAME)
            .build()
        try {
            runBlocking {
                val dao = database.subscriptionDao()
                val stored = requireNotNull(dao.getById(id)) { "the seeded row is gone" }
                dao.update(stored.copy(nextPaymentDate = date.toEpochMillisAtStartOfDay()))
            }
        } finally {
            database.close()
        }
        Log.i(TAG, "moved the date of id=$id to $date")
    }

    /** Local midnight, which is how the mapper stores a calendar day. */
    private fun LocalDate.toEpochMillisAtStartOfDay(): Long =
        atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Enqueues one undelayed run and blocks until WorkManager reports it finished. */
    private fun runWorkerOnce(): WorkInfo.State {
        val workManager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<PaymentReminderWorker>().build()
        workManager.enqueue(request).result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
        var info: WorkInfo? = workManager.getWorkInfoById(request.id).get()
        while (info?.state?.isFinished != true && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MILLIS)
            info = workManager.getWorkInfoById(request.id).get()
        }
        return requireNotNull(info) { "WorkManager never reported on ${request.id}" }.state
    }

    private fun activeNotificationText(): String? = notificationManager.activeNotifications
        .firstOrNull { it.packageName == context.packageName }
        ?.notification
        ?.let { notification ->
            val extras = notification.extras
            listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT)
                .mapNotNull { extras.getCharSequence(it)?.toString() }
                .joinToString("\n")
        }

    private fun shownText(): String = activeNotificationText().orEmpty()

    /** In either language the app ships. */
    private fun String.containsDueToday(): Boolean =
        contains("today", ignoreCase = true) || contains("bugün", ignoreCase = true)

    private companion object {
        const val TAG = "EditedDateReminderTest"
        const val TIMEOUT_SECONDS = 30L
        const val POLL_INTERVAL_MILLIS = 200L
        const val EDITED_NAME = "EditedRow"
    }
}
