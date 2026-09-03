package com.elinacn.subtrack.domain.usecase

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How far away the next payment is, in whole calendar days.
 *
 * Three cases rather than a signed number, because the screen says three different things and a
 * caller that only had an Int would have to rediscover the boundaries every time it rendered one.
 */
sealed interface PaymentCountdown {

    /** The payment is in the future; [days] is at least one. */
    data class Upcoming(val days: Long) : PaymentCountdown

    /** The payment falls on the day being asked about. */
    data object DueToday : PaymentCountdown

    /**
     * The date has passed and nothing advanced it; [days] is at least one.
     *
     * The app deliberately does not roll the date forward - how far to roll depends on
     * [com.elinacn.subtrack.domain.model.BillingPeriod], which the user cannot choose until phase
     * 12. Assuming monthly would quietly corrupt a yearly subscription. See ARCHITECTURE §17.
     */
    data class Overdue(val days: Long) : PaymentCountdown

    companion object {

        /**
         * Counts calendar days between [today] and [nextPayment].
         *
         * [today] is a parameter rather than a LocalDate.now() call inside, so the result is a
         * function of its inputs and can be tested without waiting for a particular date.
         */
        fun between(today: LocalDate, nextPayment: LocalDate): PaymentCountdown {
            // DAYS.between counts whole days between two dates, so it is the calendar difference
            // and not an elapsed-time one: 23:00 today to 01:00 tomorrow is two hours but one day.
            val days = ChronoUnit.DAYS.between(today, nextPayment)
            return when {
                days > 0 -> Upcoming(days)
                days == 0L -> DueToday
                else -> Overdue(-days)
            }
        }
    }
}
