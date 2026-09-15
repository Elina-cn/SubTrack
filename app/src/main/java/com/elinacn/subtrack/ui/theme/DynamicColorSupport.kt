package com.elinacn.subtrack.ui.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Whether this device can build a colour scheme out of the wallpaper.
 *
 * An interface for the same reason as
 * [ReminderNotificationStatus][com.elinacn.subtrack.reminder.ReminderNotificationStatus]: the
 * settings screen has to know, and a ViewModel that reads `Build.VERSION` directly cannot be
 * tested off a device, where the field reads zero.
 */
interface DynamicColorSupport {

    /** True from Android 12 on. */
    fun isAvailable(): Boolean

    companion object {

        /**
         * The one place the version number is written.
         *
         * [SubTrackTheme] cannot be handed an injected dependency, so it asks here instead of
         * repeating the comparison - two copies of a version gate are two things to get wrong.
         *
         * Annotated so lint follows the guard through the call; without it every
         * `dynamicLightColorScheme` call site reports `NewApi` against minSdk 24.
         */
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
        fun isAvailableOnThisBuild(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}
