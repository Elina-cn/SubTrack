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

/**
 * The one line of the reminder row that changes with the state.
 *
 * [delivery] only matters while reminders are on: a warning that they will come late means
 * nothing to a user who has not switched them on yet.
 */
internal fun ReminderPermissionState.statusTextId(delivery: ReminderDelivery): Int = when (this) {
    ReminderPermissionState.ENABLED -> when (delivery) {
        ReminderDelivery.ON_TIME -> R.string.reminder_notifications_on
        ReminderDelivery.UNTIL_OPENED -> R.string.reminder_notifications_on_until_opened
        ReminderDelivery.MAY_BE_DELAYED -> R.string.reminder_notifications_on_may_be_delayed
    }
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
        openAppDetails()
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
        openAppDetails()
    }
}

/**
 * Opens the system's battery saver screen, where the reminder row sends a user whose battery
 * saver is holding reminders back.
 *
 * The action exists on every supported version (API 22), but a manufacturer build may leave it
 * unanswered; the app's own page, whose battery section leads to the same switch on most phones,
 * is the stated fallback - the same shape as [openNotificationSettings].
 */
internal fun Activity.openBatterySaverSettings() {
    try {
        startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
    } catch (notFound: ActivityNotFoundException) {
        openAppDetails()
    }
}

/**
 * This app's page in Settings, with the notification and battery sections on it.
 *
 * Where both notification branches end up: one step further from the reminder toggle than the
 * per-app screen, and that is the price of the versions that have no per-app screen. Leaving the
 * row dead instead would take away the only way in. It is also where the background restriction
 * and the manufacturer's battery rules for the app live, so the restricted reminder row and the
 * note under it lead here too.
 */
internal fun Activity.openAppDetails() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
    )
}
