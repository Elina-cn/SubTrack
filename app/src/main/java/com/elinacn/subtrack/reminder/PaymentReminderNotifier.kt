package com.elinacn.subtrack.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.elinacn.subtrack.MainActivity
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.usecase.PaymentCountdown
import com.elinacn.subtrack.domain.usecase.PaymentReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Puts the day's reminders into a single notification.
 *
 * One summary rather than one notification per subscription: a user with eight subscriptions
 * renewing in the same week would otherwise get a shade full of near-identical rows.
 */
class PaymentReminderNotifier @Inject constructor(
    // @param: because Dagger reads the constructor parameter; without it Kotlin 2.2 warns that a
    // future release would also put the qualifier on the backing field.
    @param:ApplicationContext private val context: Context
) {

    /**
     * Shows [reminders] as one notification, replacing yesterday's if it is still on screen.
     *
     * Does nothing when the user has notifications switched off. On API 33+ that is also what a
     * denied POST_NOTIFICATIONS permission looks like, which is the expected state until the
     * runtime request arrives in phase 10c.
     */
    fun notify(reminders: List<PaymentReminder>) {
        if (reminders.isEmpty()) return

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        // areNotificationsEnabled already covers a denied POST_NOTIFICATIONS, but lint wants the
        // permission itself checked before notify(). The version guard matters: the permission
        // does not exist below API 33, where checkSelfPermission would answer "denied" for it and
        // silence the reminder on every older device.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // Rebuilt every time on purpose. Creating a channel that exists is a no-op, and it is what
        // updates the channel's name and description after the user changes the device language.
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_name))
                .setDescription(context.getString(R.string.notification_channel_description))
                .build()
        )

        val body = reminders.joinToString(context.getString(R.string.notification_entry_separator)) {
            context.getString(R.string.notification_entry, it.subscription.name, describe(it.countdown))
        }

        manager.notify(NOTIFICATION_ID, build(title(reminders.size), body))
    }

    /**
     * Says how many subscriptions are covered and nothing more.
     *
     * No verb: the same notification can hold a payment due today and one that is three days
     * late, so any wording that commits to one of those would be wrong half the time.
     */
    private fun title(count: Int): String =
        context.resources.getQuantityString(R.plurals.notification_title, count, count)

    private fun describe(countdown: PaymentCountdown): String = when (countdown) {
        PaymentCountdown.DueToday -> context.getString(R.string.notification_due_today)
        is PaymentCountdown.Upcoming ->
            // The selection window is one day today, so this is "tomorrow" in practice. The other
            // branch is there so a wider window later cannot silently mislabel a payment.
            if (countdown.days == 1L) {
                context.getString(R.string.notification_due_tomorrow)
            } else {
                quantity(R.plurals.days_until_payment, countdown.days)
            }
        is PaymentCountdown.Overdue -> quantity(R.plurals.days_overdue, countdown.days)
    }

    /** The count is both the quantity that picks the wording and the number written into it. */
    private fun quantity(plurals: Int, days: Long): String =
        context.resources.getQuantityString(plurals, days.toInt(), days.toInt())

    private fun build(title: String, body: String) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            // Without this the shade truncates the list to one line, which is the wrong line as
            // soon as there is more than one subscription in it.
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openTheApp())
            .setAutoCancel(true)
            .build()

    /**
     * Opens the app on the home screen. No deep link: there is no screen to land on yet, and the
     * edit screen it would point at is phase 15.
     */
    private fun openTheApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // FLAG_IMMUTABLE is required from API 31 and is right regardless: nothing outside this
        // process has any business rewriting the intent.
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private companion object {
        const val CHANNEL_ID = "payment_reminders"

        /** Fixed, so today's summary replaces yesterday's instead of stacking up. */
        const val NOTIFICATION_ID = 1
    }
}
