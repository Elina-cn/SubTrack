package com.elinacn.subtrack.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

/**
 * Every case names its own dates, so nothing here depends on when the suite runs. That is the
 * whole point of today being a parameter rather than a LocalDate.now() call inside the function.
 */
class PaymentCountdownTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    @Test
    fun between_dateIsTomorrow_isOneDayUpcoming() {
        val result = PaymentCountdown.between(today, LocalDate.of(2026, 3, 16))

        assertEquals(PaymentCountdown.Upcoming(days = 1), result)
    }

    @Test
    fun between_dateIsToday_isDueToday() {
        assertEquals(PaymentCountdown.DueToday, PaymentCountdown.between(today, today))
    }

    /**
     * Changed by the 12-2 hotfix: this used to assert Overdue(1).
     *
     * There is no overdue case any more, and a date in the past is not something this type can
     * describe - it is a caller that skipped [NextPaymentDate]. Rejecting it says so at the point
     * of the mistake instead of putting an invented figure on a card.
     */
    @Test
    fun between_theDateIsBehindToday_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PaymentCountdown.between(today, LocalDate.of(2026, 3, 14))
        }
    }

    @Test
    fun between_dateIsWeeksAway_countsWholeDays() {
        val result = PaymentCountdown.between(today, LocalDate.of(2026, 4, 15))

        // March has 31 days: 16 left in March plus 15 in April.
        assertEquals(PaymentCountdown.Upcoming(days = 31), result)
    }

    /**
     * Changed by the 12-2 hotfix: this used to assert Overdue(365). A year-old date reaches the
     * countdown as whatever day its own cycle has got to, never as itself.
     */
    @Test
    fun between_theDateIsAYearBehind_isRejectedTheSameWay() {
        assertThrows(IllegalArgumentException::class.java) {
            PaymentCountdown.between(today, LocalDate.of(2025, 3, 15))
        }
    }

    @Test
    fun between_spanCrossesALeapDay_countsIt() {
        val leapYearToday = LocalDate.of(2028, 2, 28)

        val result = PaymentCountdown.between(leapYearToday, LocalDate.of(2028, 3, 1))

        // 2028 is a leap year, so 29 February sits in between and the answer is two, not one.
        assertEquals(PaymentCountdown.Upcoming(days = 2), result)
    }

    @Test
    fun between_spanCrossesTheYearEnd_countsWholeDays() {
        val result = PaymentCountdown.between(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2))

        assertEquals(PaymentCountdown.Upcoming(days = 3), result)
    }
}
