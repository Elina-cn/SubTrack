package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.MonthlySnapshot
import com.elinacn.subtrack.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * The trend read off the recorded months, and the comparison with last month.
 *
 * Pure functions, so there is nothing to fake here at all - the rows are built by hand and the
 * month "now" is a parameter. Every case the screen has to survive is below, including the ones
 * that are the **normal** state for a new user: no months, and one month.
 */
class MonthlyTrendTest {

    private val september = YearMonth.of(2026, 9)

    // --- how many months there are -----------------------------------------------------------------

    @Test
    fun series_noMonthsAtAll_hasNoPointsAndNothingHidden() {
        val series = MonthlyTrend.series(emptyList(), september, Currency.TRY)

        assertTrue(series.points.isEmpty())
        assertEquals(0, series.monthsInOtherCurrency)
        assertNull(MonthlyTrend.changeSince(series.points))
        assertNull(MonthlyTrend.peak(series.points))
    }

    @Test
    fun series_oneMonth_isOnePointAndNotEnoughToDraw() {
        val series = MonthlyTrend.series(listOf(snapshot(september, 10_000)), september, Currency.TRY)

        assertEquals(listOf(september), series.points.map { it.period })
        assertTrue(series.points.size < MonthlyTrend.MIN_POINTS_TO_DRAW)
        assertEquals(Money(10_000), MonthlyTrend.peak(series.points))
    }

    @Test
    fun series_twoMonths_areBothDrawnOldestFirst() {
        val series = MonthlyTrend.series(
            listOf(snapshot(september, 12_000), snapshot(september.minusMonths(1), 10_000)),
            september,
            Currency.TRY
        )

        assertEquals(listOf(september.minusMonths(1), september), series.points.map { it.period })
        assertEquals(listOf(Money(10_000), Money(12_000)), series.points.map { it.total })
    }

    @Test
    fun series_moreMonthsThanFit_keepsTheMostRecentSix() {
        // Ten consecutive months ending at September: only April onwards is in the window.
        val rows = (0L until 10L).map { back -> snapshot(september.minusMonths(back), 10_000 + back) }

        val series = MonthlyTrend.series(rows, september, Currency.TRY)

        assertEquals(MonthlyTrend.MAX_MONTHS, series.points.size)
        assertEquals(YearMonth.of(2026, 4), series.points.first().period)
        assertEquals(september, series.points.last().period)
    }

    @Test
    fun series_aMonthInTheFuture_isNotADirection() {
        // A clock moved backwards is the only way to record a month that has not happened yet.
        val series = MonthlyTrend.series(
            listOf(snapshot(september, 10_000), snapshot(september.plusMonths(1), 99_000)),
            september,
            Currency.TRY
        )

        assertEquals(listOf(september), series.points.map { it.period })
    }

    // --- gaps --------------------------------------------------------------------------------------

    @Test
    fun series_aMissingMonth_keepsItsPlaceWithNoFigure() {
        val series = MonthlyTrend.series(
            listOf(snapshot(YearMonth.of(2026, 7), 10_000), snapshot(september, 30_000)),
            september,
            Currency.TRY
        )

        // August is still there, and still August: the columns stay a month apart.
        assertEquals(
            listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 8), september),
            series.points.map { it.period }
        )
        assertEquals(listOf(Money(10_000), null, Money(30_000)), series.points.map { it.total })
    }

    @Test
    fun series_aMonthRecordedAsZero_isNotTheSameAsAMissingOne() {
        val series = MonthlyTrend.series(
            listOf(snapshot(YearMonth.of(2026, 7), 10_000), snapshot(september, 0)),
            september,
            Currency.TRY
        )

        val totals = series.points.map { it.total }
        // August has no row; September was measured and came out at nothing.
        assertEquals(listOf(Money(10_000), null, Money.ZERO), totals)
    }

    @Test
    fun series_leadingEmptyMonths_areTrimmed() {
        val series = MonthlyTrend.series(
            listOf(snapshot(YearMonth.of(2026, 8), 10_000), snapshot(september, 12_000)),
            september,
            Currency.TRY
        )

        // April to July are in the window and empty; the chart starts where the records start.
        assertEquals(2, series.points.size)
        assertEquals(YearMonth.of(2026, 8), series.points.first().period)
    }

    @Test
    fun series_noFigureForThisMonth_stillEndsAtThisMonth() {
        val series = MonthlyTrend.series(
            listOf(snapshot(YearMonth.of(2026, 7), 10_000), snapshot(YearMonth.of(2026, 8), 12_000)),
            september,
            Currency.TRY
        )

        assertEquals(september, series.points.last().period)
        assertNull(series.points.last().total)
        // No figure for this month means no comparison, not a comparison of the two before it.
        assertNull(MonthlyTrend.changeSince(series.points))
    }

    // --- mixed currencies --------------------------------------------------------------------------

    @Test
    fun series_monthsInAnotherCurrency_areCountedAndLeftOut() {
        val series = MonthlyTrend.series(
            listOf(
                snapshot(YearMonth.of(2026, 7), 100_000, Currency.TRY),
                snapshot(YearMonth.of(2026, 8), 100_000, Currency.TRY),
                snapshot(september, 2_500, Currency.USD)
            ),
            september,
            Currency.USD
        )

        // Only the dollar month is drawable; the two lira months are counted, not converted.
        assertEquals(listOf(september), series.points.map { it.period })
        assertEquals(2, series.monthsInOtherCurrency)
    }

    @Test
    fun series_everyMonthInAnotherCurrency_drawsNothingAndSaysHowMany() {
        val series = MonthlyTrend.series(
            listOf(snapshot(YearMonth.of(2026, 8), 100_000), snapshot(september, 120_000)),
            september,
            Currency.EUR
        )

        assertTrue(series.points.isEmpty())
        assertEquals(2, series.monthsInOtherCurrency)
    }

    @Test
    fun series_aMonthOutsideTheWindow_isNotCountedAsHidden() {
        // A year ago, in another currency: out of range, so it is not something the chart is
        // hiding from the reader.
        val series = MonthlyTrend.series(
            listOf(
                snapshot(september.minusMonths(11), 100_000, Currency.USD),
                snapshot(september, 120_000, Currency.TRY)
            ),
            september,
            Currency.TRY
        )

        assertEquals(0, series.monthsInOtherCurrency)
    }

    @Test
    fun changeSince_lastMonthInAnotherCurrency_isNoComparisonAtAll() {
        val series = MonthlyTrend.series(
            listOf(
                snapshot(YearMonth.of(2026, 8), 100_000, Currency.USD),
                snapshot(september, 120_000, Currency.TRY)
            ),
            september,
            Currency.TRY
        )

        // Last month exists, but not in a unit this month can be subtracted from.
        assertNull(MonthlyTrend.changeSince(series.points))
    }

    // --- the comparison ----------------------------------------------------------------------------

    @Test
    fun changeSince_moreThanLastMonth_isAnIncreaseOfTheDifference() {
        val change = MonthlyTrend.changeSince(points(10_000, 12_500))

        assertEquals(MonthlyChange(Money(2_500), TrendDirection.UP), change)
    }

    @Test
    fun changeSince_lessThanLastMonth_isADecreaseWithNoMinusSign() {
        val change = MonthlyTrend.changeSince(points(12_500, 10_000))

        assertEquals(TrendDirection.DOWN, change?.direction)
        // The size is positive; the direction carries the sign, so the screen never formats a
        // negative amount of money.
        assertEquals(Money(2_500), change?.amount)
    }

    @Test
    fun changeSince_theSameAsLastMonth_isNoChangeRatherThanNoComparison() {
        val change = MonthlyTrend.changeSince(points(10_000, 10_000))

        assertEquals(MonthlyChange(Money.ZERO, TrendDirection.UNCHANGED), change)
    }

    @Test
    fun changeSince_noMonthBeforeThisOne_isNothingToShow() {
        assertNull(MonthlyTrend.changeSince(points(10_000)))
        assertNull(MonthlyTrend.changeSince(emptyList()))
    }

    @Test
    fun changeSince_readsTheLastTwoMonthsOnly() {
        // Whatever happened in the spring does not change what this month did against last month.
        val change = MonthlyTrend.changeSince(points(90_000, 1_000, 10_000, 12_000))

        assertEquals(MonthlyChange(Money(2_000), TrendDirection.UP), change)
    }

    // --- the top of the scale ----------------------------------------------------------------------

    @Test
    fun peak_isTheLargestFigureAndIgnoresMissingMonths() {
        val points = listOf(
            TrendPoint(YearMonth.of(2026, 7), Money(10_000)),
            TrendPoint(YearMonth.of(2026, 8), null),
            TrendPoint(september, Money(30_000))
        )

        assertEquals(Money(30_000), MonthlyTrend.peak(points))
    }

    @Test
    fun peak_everyMonthAtZero_isZeroRatherThanNothing() {
        assertEquals(Money.ZERO, MonthlyTrend.peak(points(0, 0)))
    }

    /** Consecutive months ending at September, one figure each. */
    private fun points(vararg cents: Long): List<TrendPoint> = cents.mapIndexed { index, value ->
        TrendPoint(september.minusMonths((cents.size - 1 - index).toLong()), Money(value))
    }

    private fun snapshot(
        period: YearMonth,
        cents: Long,
        currency: Currency = Currency.TRY
    ) = MonthlySnapshot(
        period = period,
        total = Money(cents),
        currency = currency,
        recordedAt = 1_000L
    )
}
