package com.elinacn.subtrack.reminder

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Answers [ReminderDeliveryStatus] from the platform. */
class AndroidReminderDeliveryStatus @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ReminderDeliveryStatus {

    /**
     * Written by hand because no Compat class wraps it: the per-app background restriction and the
     * call that reports it both arrived with Android 9. Below that the honest answer is "no", not
     * a guess - the switch does not exist there.
     */
    override fun isBackgroundRestricted(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            ContextCompat.getSystemService(context, ActivityManager::class.java)
                ?.isBackgroundRestricted == true

    override fun isPowerSaveMode(): Boolean =
        ContextCompat.getSystemService(context, PowerManager::class.java)?.isPowerSaveMode == true
}
