package com.elinacn.subtrack.domain.usecase

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How far away the next payment is, in whole calendar days.
 *
 * Two cases rather than a number, because the screen says two different things and a caller that
 * only had an Int would have to rediscover the boundary every time it rendered one.
 *
 * There is no "overdue" case. Both callers measure against the date the anchor has actually
 * reached ([NextPaymentDate]), which is never in the past, so a payment is either owed today or
 * owed later - a date that has gone by means the payment was made, not that it is late
 * (ARCHITECTURE §17 and §18).
 */
sealed interface PaymentCountdown {

    /** The payment is in the future; [days] is at least one. */
    data class Upcoming(val days: Long) : PaymentCountdown

    /** The payment falls on the day being asked about. */
    data object DueToday : PaymentCountdown

    companion object {

        /**
         * Counts calendar days between [today] and [nextPayment].
         *
         * [today] is a parameter rather than a LocalDate.now() call inside, so the result is a
         * function of its inputs and can be tested without waiting for a particular date.
         *
         * [nextPayment] must not be before [today] - it is a date this type has no case for. Pass
         * what [NextPaymentDate.onOrAfter] returns, which is what both callers do; the stored
         * anchor on its own is exactly the wrong thing to hand in.
         */
        fun between(today: LocalDate, nextPayment: LocalDate): PaymentCountdown {
            require(!nextPayment.isBefore(today)) {
                "$nextPayment is before $today; advance the anchor with NextPaymentDate first"
            }
            // DAYS.between counts whole days between two dates, so it is the calendar difference
            // and not an elapsed-time one: 23:00 today to 01:00 tomorrow is two hours but one day.
            val days = ChronoUnit.DAYS.between(today, nextPayment)
            return if (days == 0L) DueToday else Upcoming(days)
        }
    }
}
