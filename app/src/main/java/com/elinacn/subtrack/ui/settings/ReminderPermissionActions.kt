package com.elinacn.subtrack.ui.settings

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
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

/**
 * Opens the best notification screen this Android version actually has.
 *
 * The per-app notification screen arrived in API 26. Below that the version branch is the only
 * thing that helps, because catching cannot: Android 7.x's Settings answers
 * ACTION_APP_NOTIFICATION_SETTINGS, so nothing is thrown - the screen opens, finds the app_uid
 * extra it wants missing, and closes itself, leaving the tap looking like it did nothing.
 * Measured in phase 16b; see ARCHITECTURE section 18.
 */
internal fun Activity.openNotificationSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        startAppDetails()
        return
    }
    try {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    } catch (notFound: ActivityNotFoundException) {
        // Spelled out rather than swallowed: a build that ships without the per-app screen still
        // has the app details page, which is where the older versions go anyway. A stated
        // fallback, not the silent catch section 9 forbids.
        startAppDetails()
    }
}

/**
 * Where both branches end up: this app's page in Settings, with the notification section on it.
 *
 * It is one step further from the reminder toggle than the per-app screen, and that is the price
 * of the versions that have no per-app screen. Leaving the row dead instead would take away the
 * only way in.
 */
private fun Activity.startAppDetails() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
    )
}
