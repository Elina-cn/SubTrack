package com.elinacn.subtrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Names a month the way the reader's language names it.
 *
 * Here rather than in the domain for the same reason as [MoneyFormatter]: it needs a Locale, and
 * the domain stays free of platform APIs. A [YearMonth] is a calendar fact; what it is called is
 * not.
 *
 * **No year, in either form.** The trend covers the last six months ending at this one, so a bare
 * month name cannot be mistaken for a month six years ago - and a screen reader working through
 * six points should not have to hear the year six times to learn nothing.
 */
class MonthFormatter(private val locale: Locale) {

    /** The abbreviation under a column, e.g. "Eyl" or "Sep". */
    fun short(period: YearMonth): String =
        period.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale)

    /**
     * The full name, for the sentence a screen reader hears: "Eylül", not "Eyl".
     *
     * The standalone form on purpose: Turkish and English happen to spell the two alike, but a
     * language that inflects the month after a date would put the wrong form in a list like this.
     */
    fun full(period: YearMonth): String =
        period.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
}

/** Rebuilt only when the language changes, the way [rememberMoneyFormatter] is. */
@Composable
fun rememberMonthFormatter(): MonthFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { MonthFormatter(locale) }
}
