package com.elinacn.subtrack.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Writes a time of day the way the phone writes its own clock: on a 24- or 12-hour clock as the
 * phone is set, in the reader's language.
 *
 * Here rather than in the domain for the same reason as [MonthFormatter]: it needs a Locale. The
 * pattern is the platform's best one for the language and is read by [SimpleDateFormat], the
 * pairing the platform documents; java.time is left out because the platform's patterns are not
 * guaranteed to use only letters java.time understands on older releases.
 */
class TimeOfDayFormatter(locale: Locale, is24Hour: Boolean) {

    private val format = SimpleDateFormat(
        DateFormat.getBestDateTimePattern(locale, if (is24Hour) SKELETON_24 else SKELETON_12),
        locale
    ).apply { timeZone = TimeZone.getTimeZone(UTC) }

    /** "09:00" on a 24-hour phone, "9:00 AM" on a 12-hour one in English. */
    fun format(time: LocalTime): String =
        format.format(Date(time.toSecondOfDay() * MILLIS_PER_SECOND))

    private companion object {
        /** Hour of the day (0-23) and minute. */
        const val SKELETON_24 = "Hm"

        /** Hour of the half-day (1-12), minute, and the language's own AM/PM mark. */
        const val SKELETON_12 = "hm"

        /** The time is laid on the first day of the epoch in UTC, so no zone can shift it. */
        const val UTC = "UTC"

        const val MILLIS_PER_SECOND = 1_000L
    }
}

/** Rebuilt only when the language or the phone's clock setting changes. */
@Composable
fun rememberTimeOfDayFormatter(is24Hour: Boolean): TimeOfDayFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, is24Hour) { TimeOfDayFormatter(locale, is24Hour) }
}
