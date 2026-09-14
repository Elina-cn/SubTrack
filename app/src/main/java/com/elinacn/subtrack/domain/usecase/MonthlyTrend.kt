package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.MonthlySnapshot
import java.time.YearMonth
import kotlin.math.abs

/**
 * One month's place on the trend.
 *
 * [total] is null when nothing was recorded for that month, which is **not** the same as a month
 * that cost nothing: phase 12a records an empty list as zero on purpose (ARCHITECTURE §19), so a
 * null here means "no figure" and `Money.ZERO` means "looked, and it was nothing". The chart has to
 * draw those two differently, or the distinction 12a paid for is lost on the way to the screen.
 */
data class TrendPoint(
    val period: YearMonth,
    val total: Money?
)

/** Which way the figure moved between two months. */
enum class TrendDirection { UP, DOWN, UNCHANGED }

/**
 * How far this month sits from the one before it.
 *
 * [amount] is never negative - the sign lives in [direction] instead. That keeps the two apart for
 * the screen: the amount is formatted as money and read aloud as money, and a minus sign buried
 * inside it would have to be stripped back out before either could happen.
 */
data class MonthlyChange(
    val amount: Money,
    val direction: TrendDirection
)

/** The months the trend can draw, together with what had to be left out of them. */
data class MonthlyTrendSeries(
    /** Consecutive months, oldest first, ending at the month asked about. */
    val points: List<TrendPoint> = emptyList(),
    /**
     * How many months in range were recorded in some other currency.
     *
     * Counted rather than converted - see [MonthlyTrend.series]. The screen says this out loud
     * instead of quietly showing a shorter chart.
     */
    val monthsInOtherCurrency: Int = 0
)

/**
 * Reads the recorded monthly totals as a trend.
 *
 * Pure functions over a list: no repository, no clock, no Android. The clock's answer arrives as
 * the `currentMonth` parameter, the way the rest of the app hands time in rather than reading it
 * (ARCHITECTURE §17).
 *
 * This object only **reads**. Recording is phase 12a's [MonthlySnapshotRecorder] and nothing here
 * writes a row.
 */
object MonthlyTrend {

    /**
     * How many months the chart covers.
     *
     * Six, chosen against the narrow screen. A 360dp phone leaves 328dp between the screen
     * paddings, so six columns get 54dp each - enough for a three-letter month label at font scale
     * 2.0, which is where labels start running into each other. Twelve would halve that to 27dp and
     * the labels would collide at normal scale, never mind a large one. Six is also half a year,
     * which is about as much as a reader can hold in their head while looking at it.
     */
    const val MAX_MONTHS = 6

    /**
     * How many months it takes before there is a trend at all.
     *
     * One point is a dot, not a direction. The screen says so in words instead of drawing a chart
     * with a single column in it - and this is the **usual** state for a new user rather than an
     * edge case: the table holds one month until their second month comes round.
     */
    const val MIN_POINTS_TO_DRAW = 2

    /**
     * The months to draw, ending at [currentMonth].
     *
     * **Mixed currencies are left out, never converted.** A row keeps the currency it was written
     * in (§19), so a user who has changed their main currency has a table with two units in it.
     * Converting the older months at today's rate would restate history with a number that was
     * never true - and the rates here are hand-entered and editable (§15), so every rate edit would
     * silently redraw the past. Putting both units on one axis is worse: it is arithmetic on apples
     * and pears, and the chart would show a cliff where the user only changed a setting. So the
     * trend is drawn in [currency] alone, the rest are counted into
     * [MonthlyTrendSeries.monthsInOtherCurrency], and the screen tells the reader they are there.
     * The series heals itself - after [MAX_MONTHS] months in the new currency the old rows have
     * left the window anyway.
     *
     * **Gaps keep their place.** A month with no row still gets a point with a null total, so the
     * months stay evenly spaced in time. Dropping it would pack the remaining columns together and
     * make a two-month climb look like a one-month one: a chart whose spacing lies about how fast
     * something changed. A gap is not expected today, because the total is written on every change,
     * but a month in which the app is never opened leaves one.
     *
     * Leading months with nothing in them are trimmed, so the chart starts where the records start
     * rather than opening with empty air. Months **after** [currentMonth] are dropped too: they can
     * only come from a clock that has been moved back, and a trend running into the future is not a
     * trend.
     */
    fun series(
        snapshots: List<MonthlySnapshot>,
        currentMonth: YearMonth,
        currency: Currency,
        maxMonths: Int = MAX_MONTHS
    ): MonthlyTrendSeries {
        val earliest = currentMonth.minusMonths((maxMonths - 1).toLong())
        val inWindow = snapshots.filter { it.period >= earliest && it.period <= currentMonth }
        val drawable = inWindow.filter { it.currency == currency }
        val otherCurrency = inWindow.size - drawable.size
        if (drawable.isEmpty()) return MonthlyTrendSeries(emptyList(), otherCurrency)

        val byPeriod = drawable.associateBy { it.period }
        val points = buildList {
            // The caller's ordering is not trusted: the series starts at the earliest month there
            // actually is, whatever order the rows arrived in.
            var month = drawable.minOf { it.period }
            while (month <= currentMonth) {
                add(TrendPoint(month, byPeriod[month]?.total))
                month = month.plusMonths(1)
            }
        }
        return MonthlyTrendSeries(points, otherCurrency)
    }

    /**
     * The difference between the last month of [points] and the one before it, or null when there
     * is nothing to compare.
     *
     * Null in three cases that all mean the same thing - there is no last month to go by: fewer
     * than two points, no figure for this month, or no figure for the month before it. The screen
     * then shows **nothing at all**, no placeholder and no dash, which is the rule the payment
     * countdown already follows for a subscription with no date (phase 10a).
     *
     * [points] is contiguous by construction, so the entry before the last one really is last
     * month. Comparing this month against whichever month happened to be recorded before it would
     * be a different question wearing this one's label.
     */
    fun changeSince(points: List<TrendPoint>): MonthlyChange? {
        if (points.size < MIN_POINTS_TO_DRAW) return null
        val current = points.last().total ?: return null
        val previous = points[points.lastIndex - 1].total ?: return null

        val difference = current - previous
        return MonthlyChange(
            amount = Money(abs(difference.cents)),
            direction = when {
                difference > Money.ZERO -> TrendDirection.UP
                difference < Money.ZERO -> TrendDirection.DOWN
                else -> TrendDirection.UNCHANGED
            }
        )
    }

    /**
     * The largest figure in [points], or null when none of them has one.
     *
     * This is the top of the chart's scale and the one amount written beside it, so it is worked
     * out here rather than in the composable: how tall a column is drawn is geometry, but which
     * number the axis is labelled with is data.
     */
    fun peak(points: List<TrendPoint>): Money? = points.mapNotNull { it.total }.maxOrNull()
}
