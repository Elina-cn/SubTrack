package com.elinacn.subtrack.domain.usecase

import org.junit.Assert.assertEquals
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

    @Test
    fun between_dateWasYesterday_isOneDayOverdue() {
        val result = PaymentCountdown.between(today, LocalDate.of(2026, 3, 14))

        // Overdue carries a positive count; the direction is in the type, not in the sign.
        assertEquals(PaymentCountdown.Overdue(days = 1), result)
    }

    @Test
    fun between_dateIsWeeksAway_countsWholeDays() {
        val result = PaymentCountdown.between(today, LocalDate.of(2026, 4, 15))

        // March has 31 days: 16 left in March plus 15 in April.
        assertEquals(PaymentCountdown.Upcoming(days = 31), result)
    }

    @Test
    fun between_dateIsLongPast_countsWholeDays() {
        val result = PaymentCountdown.between(today, LocalDate.of(2025, 3, 15))

        // 2026 is not a leap year and the span does not cross 29 February 2024.
        assertEquals(PaymentCountdown.Overdue(days = 365), result)
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
