package com.elinacn.subtrack.reminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Answers [ReminderNotificationStatus] from the platform. */
class AndroidReminderNotificationStatus @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ReminderNotificationStatus {

    override fun areRemindersVisible(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false

        // A channel that is not there yet is not a channel the user has silenced - the notifier
        // creates it on the first send, and below API 26 there are no channels at all. Compat
        // returns null in both cases, so no version branch is written here.
        val channel = manager.getNotificationChannelCompat(ReminderNotificationStatus.CHANNEL_ID)
            ?: return true
        return channel.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }

    /**
     * The one version check that is written by hand.
     *
     * No Compat class answers "does this build need a runtime permission to notify" - the whole
     * concept arrived with Android 13, and the settings screen has to know which of its two ways
     * of turning reminders on is even available.
     */
    override fun isRuntimePermissionRequired(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun isPermissionGranted(): Boolean =
        !isRuntimePermissionRequired() ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
