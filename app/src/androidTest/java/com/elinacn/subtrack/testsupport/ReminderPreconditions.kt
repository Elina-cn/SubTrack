package com.elinacn.subtrack.testsupport

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.test.platform.app.InstrumentationRegistry
import com.elinacn.subtrack.debug.PreferencesStoreEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking

/**
 * What a test class that drives the once-a-day reminder has to put right before it starts.
 *
 * Both of the things here used to be done from outside - `pm clear` before the run, `pm grant`
 * after it - which is why the suite passed one class at a time and failed as a suite. A
 * precondition set up by the person running the tests is a precondition the next class does not
 * have; a precondition set up in @Before is one every class has, in any order, on any run.
 */

/**
 * Forgets that a reminder was already sent today, the way a freshly cleared install has.
 *
 * The worker returns early when the stored day is today, and it records that day itself, so the
 * first class to notify takes the only reminder of the day and every later class sees a worker
 * that does nothing. Removing the key gives each class the state it was written against.
 *
 * Goes through the app's own store rather than its file: a live DataStore answers reads from an
 * in-memory cache and never notices a file changed underneath it. Measured on 1.1.7 - after
 * deleting the file a read still returned the old value, and only the next write saw it gone.
 */
fun clearReminderDayRecord(context: Context) {
    val dataStore = EntryPointAccessors
        .fromApplication(context.applicationContext, PreferencesStoreEntryPoint::class.java)
        .preferencesDataStore()
    runBlocking { dataStore.edit { preferences -> preferences.remove(LAST_NOTIFIED_DAY) } }
    Log.i(TAG, "cleared $LAST_NOTIFIED_DAY")
}

/**
 * Gives the app back the notification permission an install took away.
 *
 * `connectedDebugAndroidTest` reinstalls both APKs immediately before it runs, and on API 33+ a
 * reinstall drops the runtime grant - so a suite that was granted by hand passed and the same
 * suite run through Gradle failed on "no notification was posted". Granting here works either
 * way and needs nothing of whoever starts the run, which is what makes it usable on CI.
 *
 * `pm grant` through the instrumentation's UiAutomation runs as the shell user, which is the
 * account that holds GRANT_RUNTIME_PERMISSIONS. Unlike `pm revoke` it does not kill the process.
 */
fun grantNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val packageName = instrumentation.targetContext.packageName
    val command = "pm grant $packageName ${Manifest.permission.POST_NOTIFICATIONS}"
    // Draining the pipe is what waits for the command; closing it unread can cut it short.
    val output = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
            stream.readBytes().decodeToString()
        }
    }
    Log.i(TAG, "[$command] said [${output.trim()}]")
}

private const val TAG = "ReminderPreconditions"

/**
 * Spelled out again rather than shared with the app.
 *
 * ReminderStateRepositoryImpl keeps its keys private, and opening them up would be production
 * code widened for a test. The cost of repeating it is that a renamed key stops being cleared;
 * the reminder suite fails the moment that happens, which is the point at which it is noticed.
 */
private val LAST_NOTIFIED_DAY = longPreferencesKey("reminder_last_notified_day")
