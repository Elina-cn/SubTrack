package com.elinacn.subtrack.reminder

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Runs the real worker, through the real graph, against the real database.
 *
 * The instrumentation shares a process with the app, so WorkManager here is the app's own
 * instance - the one its Configuration.Provider built with the HiltWorkerFactory. Nothing is
 * faked: no test factory, no test driver, no in-memory database.
 *
 * The request is one-time and undelayed on purpose. WorkManager refuses to run work before its
 * scheduled time, which is what makes the daily job impossible to trigger early from adb; that
 * check does not apply to work with nothing to wait for.
 *
 * **Run this against freshly cleared app data** (`adb shell pm clear com.elinacn.subtrack`). The
 * worker notifies at most once a day and records the day it did, so a second run on the same day
 * is a no-op by design. The method order is fixed for the same reason: the disabled-notifications
 * case also marks the day, so it has to come second.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PaymentReminderWorkerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val notificationManager =
        context.getSystemService(NotificationManager::class.java)

    @Test
    fun reminderWorker_datedSubscriptions_notifiesOnlyTheOnesInsideTheWindows() {
        notificationManager.cancelAll()
        seedSubscriptions()

        val state = runWorkerOnce()
        Log.i(TAG, "first run finished with state=$state")
        assertEquals(WorkInfo.State.SUCCEEDED, state)

        val posted = activeNotification()
        assertNotNull("no notification was posted", posted)
        val notification = requireNotNull(posted).notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        Log.i(TAG, "EXTRA_TITLE=[$title]")
        Log.i(TAG, "EXTRA_TEXT=[$text]")
        Log.i(TAG, "EXTRA_BIG_TEXT=[$bigText]")
        Log.i(TAG, "channelId=[${NotificationCompat.getChannelId(notification)}]")
        Log.i(TAG, "postTime=${posted.postTime}")
        Log.i(TAG, "contentIntent is null=${notification.contentIntent == null}")
        Log.i(
            TAG,
            "FLAG_AUTO_CANCEL set=${notification.flags and Notification.FLAG_AUTO_CANCEL != 0}"
        )

        val shown = "$title\n$text\n$bigText"
        INSIDE_THE_WINDOWS.forEach { name ->
            assertTrue("$name should be in the notification, was: $shown", shown.contains(name))
        }
        OUTSIDE_THE_WINDOWS.forEach { name ->
            assertFalse("$name should not be in the notification, was: $shown", shown.contains(name))
        }

        // A leftover % means a format argument was not supplied, which is how a raw "%1$d" has
        // twice reached the screen in earlier phases.
        assertFalse("the notification text still holds a % placeholder: $shown", shown.contains('%'))

        // Second run, same day. The worker should decide it has nothing to do and leave the
        // existing notification untouched rather than post a fresh one.
        val firstPostTime = posted.postTime
        val secondState = runWorkerOnce()
        Log.i(TAG, "second run finished with state=$secondState")
        assertEquals(WorkInfo.State.SUCCEEDED, secondState)

        val afterSecondRun = activeNotification()
        assertNotNull("the notification disappeared after the second run", afterSecondRun)
        val secondPostTime = requireNotNull(afterSecondRun).postTime
        Log.i(TAG, "postTime first=$firstPostTime second=$secondPostTime")
        assertEquals("a second notification was posted", firstPostTime, secondPostTime)
        assertEquals(
            "more than one notification is showing",
            1,
            notificationManager.activeNotifications.count { it.packageName == context.packageName }
        )
    }

    /**
     * The path the app is in until phase 10c grants the permission: notifications switched off.
     *
     * Skipped when they are on, because there is nothing to observe then. Run it after
     * `adb shell pm revoke com.elinacn.subtrack android.permission.POST_NOTIFICATIONS`.
     */
    @Test
    fun reminderWorker_notificationsDisabled_succeedsWithoutNotifying() {
        val enabled = androidx.core.app.NotificationManagerCompat.from(context)
            .areNotificationsEnabled()
        Log.i(TAG, "areNotificationsEnabled=$enabled")
        assumeFalse("notifications are enabled; this case only applies when they are off", enabled)

        notificationManager.cancelAll()
        seedSubscriptions()

        val state = runWorkerOnce()
        Log.i(TAG, "disabled-notifications run finished with state=$state")
        assertEquals(WorkInfo.State.SUCCEEDED, state)

        val posted = notificationManager.activeNotifications
            .count { it.packageName == context.packageName }
        Log.i(TAG, "active notifications for the app=$posted")
        assertEquals("something was posted with notifications switched off", 0, posted)
    }

    /**
     * Writes the seven rows through a second connection to the app's own database file.
     *
     * A second Room instance over the same file is allowed - unlike DataStore, which forbids it -
     * and the worker opens its own instance later, so it sees whatever is on disk.
     */
    private fun seedSubscriptions() {
        val database = Room
            .databaseBuilder(context, SubTrackDatabase::class.java, SubTrackDatabase.NAME)
            .build()
        val today = LocalDate.now()
        try {
            database.clearAllTables()
            val dao = database.subscriptionDao()
            runBlocking {
                // createdAt descends with the list, so observeAll's ORDER BY createdAt DESC hands
                // them back in exactly this order and the notification body is predictable.
                listOf(
                    "DueToday" to today,
                    "Tomorrow" to today.plusDays(1),
                    "TwoDaysOut" to today.plusDays(2),
                    "OneDayLate" to today.minusDays(1),
                    "ThreeDaysLate" to today.minusDays(3),
                    "FourDaysLate" to today.minusDays(4),
                    "NoDate" to null
                ).forEachIndexed { index, (name, date) ->
                    dao.insert(subscription(name, date, createdAt = (100 - index).toLong()).toEntity())
                }
            }
        } finally {
            database.close()
        }
        Log.i(TAG, "seeded seven subscriptions around today=$today")
    }

    private fun subscription(name: String, date: LocalDate?, createdAt: Long) = Subscription(
        id = 0,
        name = name,
        price = Money(1000),
        currency = Currency.TRY,
        billingPeriod = BillingPeriod.MONTHLY,
        nextPaymentDate = date,
        category = SubscriptionCategory.OTHER,
        iconKey = null,
        createdAt = createdAt
    )

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

    private fun activeNotification(): StatusBarNotification? =
        notificationManager.activeNotifications.firstOrNull { it.packageName == context.packageName }

    private companion object {
        const val TAG = "ReminderWorkerTest"

        const val TIMEOUT_SECONDS = 30L

        const val POLL_INTERVAL_MILLIS = 200L

        val INSIDE_THE_WINDOWS = listOf("DueToday", "Tomorrow", "OneDayLate", "ThreeDaysLate")

        val OUTSIDE_THE_WINDOWS = listOf("TwoDaysOut", "FourDaysLate", "NoDate")
    }
}
