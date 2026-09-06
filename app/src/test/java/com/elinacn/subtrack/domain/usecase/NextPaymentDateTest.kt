package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.model.BillingPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Catching an anchor up to today, one billing period at a time.
 *
 * Fixed dates throughout: every expectation here can be checked against a calendar.
 */
class NextPaymentDateTest {

    // --- nothing to catch up on -----------------------------------------------------------

    @Test
    fun aDateInTheFuture_isLeftAlone() {
        val anchor = LocalDate.of(2026, 10, 1)

        val next = NextPaymentDate.onOrAfter(
            today = LocalDate.of(2026, 9, 6),
            anchor = anchor,
            period = BillingPeriod.MONTHLY
        )

        assertEquals(anchor, next)
    }

    @Test
    fun todaysDate_staysToday() {
        val today = LocalDate.of(2026, 9, 6)

        val next = NextPaymentDate.onOrAfter(today, anchor = today, period = BillingPeriod.MONTHLY)

        // Due today has to survive: a payment owed this morning is not one that has been made.
        assertEquals(today, next)
    }

    // --- monthly, and the end of the month --------------------------------------------------

    @Test
    fun monthly_movesToTheNextDueDate() {
        val next = NextPaymentDate.onOrAfter(
            today = LocalDate.of(2026, 9, 6),
            anchor = LocalDate.of(2026, 7, 20),
            period = BillingPeriod.MONTHLY
        )

        assertEquals(LocalDate.of(2026, 9, 20), next)
    }

    @Test
    fun monthly_aShortMonthClampsTheDay() {
        // The 31st of January is billed on the 28th in February; there is no 31st to bill on.
        val next = NextPaymentDate.onOrAfter(
            today = LocalDate.of(2026, 2, 15),
            anchor = LocalDate.of(2026, 1, 31),
            period = BillingPeriod.MONTHLY
        )

        assertEquals(LocalDate.of(2026, 2, 28), next)
    }

    /**
     * The trap this is written against: February must not cost the anchor its 31st.
     *
     * Stepping a month at a time would go 31 Jan -> 28 Feb -> 28 Mar and the subscription would
     * quietly move to the 28th for the rest of its life. Counting whole months from the anchor
     * and adding them once gives the 31st back as soon as a month has one.
     */
    @Test
    fun monthly_theClampDoesNotStick_becauseCountingStartsAtTheAnchor() {
        val anchor = LocalDate.of(2026, 1, 31)

        val next = NextPaymentDate.onOrAfter(
            today = LocalDate.of(2026, 3, 1),
            anchor = anchor,
            period = BillingPeriod.MONTHLY
        )

        assertEquals(LocalDate.of(2026, 3, 31), next)
    }

    @Test
    fun monthly_theSameAnchorAcrossFourMonths() {
        val anchor = LocalDate.of(2026, 1, 31)

        val dates = listOf(
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 5, 1)
        ).map { NextPaymentDate.onOrAfter(it, anchor, BillingPeriod.MONTHLY) }

        // April has no 31st either, and the anchor still comes back for May.
        assertEquals(
            listOf(
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 31)
            ),
            dates
        )
    }

    @Test
    fun monthly_landingExactlyOnToday_isNotSteppedPastIt() {
        val next = NextPaymentDate.onOrAfter(
            today = LocalDate.of(2026, 9, 6),
            anchor = LocalDate.of(2026, 8, 6),
            period = BillingPeriod.MONTHLY
        )

        assertEquals(LocalDate.of(2026, 9, 6), next)
    }

    // --- yearly, and the 29th of February -----------------------------------------------------

    @Test
    fun yearly_aLeapDayAnchorClampsInCommonYears() {
        val next = NextPaymentDate.onOrAfter(
            today = LocalDate.of(2029, 1, 1),
            anchor = LocalDate.of(2028, 2, 29),
            period = BillingPeriod.YEARLY
        )

        assertEquals(LocalDate.of(2029, 2, 28), next)
    }

    @Test
    fun yearly_aLeapDayAnchorComesBackOnTheNextLeapYear() {
        // Four common-year clamps in a row would have left this on the 28th for good.
        val next = NextPaymentDate.onOrAfter(
            today = LocalDate.of(2032, 2, 1),
            anchor = LocalDate.of(2028, 2, 29),
            period = BillingPeriod.YEARLY
        )

        assertEquals(LocalDate.of(2032, 2, 29), next)
    }

    @Test
    fun yearly_tenYearsBehind() {
        val next = NextPaymentDate.onOrAfter(
            today = LocalDate.of(2026, 9, 6),
            anchor = LocalDate.of(2016, 3, 15),
            period = BillingPeriod.YEARLY
        )

        assertEquals(LocalDate.of(2027, 3, 15), next)
    }

    // --- weekly, including a very long gap ----------------------------------------------------

    @Test
    fun weekly_movesToTheNextWeek() {
        val next = NextPaymentDate.onOrAfter(
            today = LocalDate.of(2026, 9, 6),
            anchor = LocalDate.of(2026, 8, 31),
            period = BillingPeriod.WEEKLY
        )

        assertEquals(LocalDate.of(2026, 9, 7), next)
    }

    @Test
    fun weekly_tenYearsBehind_isFiveHundredAndFiftyEightPeriods() {
        val anchor = LocalDate.of(2016, 1, 1)
        val today = LocalDate.of(2026, 9, 6)

        val next = NextPaymentDate.onOrAfter(today, anchor, BillingPeriod.WEEKLY)

        // 3.901 days is 557 whole weeks, which lands two days short of today, so 558 it is.
        assertEquals(558L, ChronoUnit.WEEKS.between(anchor, next))
        assertEquals(LocalDate.of(2026, 9, 11), next)
    }

    // --- the shape of the answer, whatever the inputs -----------------------------------------

    /**
     * The one property everything above is a special case of: the answer is never in the past and
     * never more than one period away, and it always sits on the anchor's own cycle.
     */
    @Test
    fun theAnswer_isAlwaysTheFirstDueDateNotBeforeToday() {
        val today = LocalDate.of(2026, 9, 6)
        val anchors = listOf(
            LocalDate.of(2026, 1, 31),
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2016, 1, 1),
            LocalDate.of(2026, 9, 5),
            LocalDate.of(2026, 9, 6),
            LocalDate.of(2026, 12, 25)
        )

        for (period in BillingPeriod.entries) {
            for (anchor in anchors) {
                val next = NextPaymentDate.onOrAfter(today, anchor, period)
                val previous = if (anchor.isBefore(today)) next.minus(1, unitOf(period)) else null

                assertTrue("$period $anchor gave $next", !next.isBefore(today))
                assertTrue(
                    "$period $anchor: $previous should be before today",
                    previous == null || previous.isBefore(today)
                )
            }
        }
    }

    private fun unitOf(period: BillingPeriod): ChronoUnit = when (period) {
        BillingPeriod.WEEKLY -> ChronoUnit.WEEKS
        BillingPeriod.MONTHLY -> ChronoUnit.MONTHS
        BillingPeriod.YEARLY -> ChronoUnit.YEARS
    }
}
