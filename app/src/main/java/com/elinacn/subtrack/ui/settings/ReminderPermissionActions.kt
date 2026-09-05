package com.elinacn.subtrack.ui.settings

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import com.elinacn.subtrack.R

/**
 * The parts of the reminder row that need an Activity.
 *
 * They live outside the ViewModel because an Activity is not something a ViewModel may hold, and
 * outside the screen body because they are plain functions with nothing to compose.
 */

/** The one line of the reminder row that changes with the state. */
internal fun ReminderPermissionState.statusTextId(): Int = when (this) {
    ReminderPermissionState.ENABLED -> R.string.reminder_notifications_on
    ReminderPermissionState.CAN_REQUEST -> R.string.reminder_notifications_can_request
    ReminderPermissionState.SETTINGS_ONLY -> R.string.reminder_notifications_settings_only
}

/**
 * Whether the system will still let the app ask for the notification permission.
 *
 * False both when it has never been asked and when it has been refused for good - the system does
 * not tell the two apart, which is why the ViewModel pairs this with its own stored flag. A null
 * activity means a preview, where there is nothing to ask.
 */
internal fun Activity?.canShowNotificationRationale(): Boolean =
    this != null && ActivityCompat.shouldShowRequestPermissionRationale(
        this,
        Manifest.permission.POST_NOTIFICATIONS
    )

/** Opens this app's notification settings, falling back to its app details page. */
internal fun Activity.openNotificationSettings() {
    val appNotifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    try {
        startActivity(appNotifications)
    } catch (notFound: ActivityNotFoundException) {
        // Spelled out rather than swallowed: the per-app notification screen arrived in API 26,
        // and below that - or on a build that ships without it - the app details page is the
        // deliberate second choice. A stated fallback, not the silent catch section 9 forbids.
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }
}
