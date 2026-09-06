package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.model.BillingPeriod
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Where a subscription's payment date has reached by a given day, without moving what is stored.
 *
 * The stored date is an **anchor**: the day the user told us about, which is theirs and stays put.
 * What a card shows is this - the anchor stepped forward by whole billing periods until it lands
 * on or after today. It is not a property of the subscription but of the subscription and today
 * together, which is the same reason [PaymentCountdown] takes today as a parameter and the same
 * reason neither of them writes anything back (ARCHITECTURE §17).
 *
 * Calendar arithmetic, not day counting: `plusMonths` on the 31st of January lands on the 28th of
 * February, which is what a monthly subscription actually does.
 */
object NextPaymentDate {

    /**
     * The anchor moved forward by whole [period]s until it is not before [today].
     *
     * An anchor that is already today or later comes back untouched - there is nothing to catch up
     * on, and a date the user picked for next week is the answer to the question.
     *
     * **Always counted from the anchor, never from the last step.** Stepping one month at a time
     * from the 31st of January would reach the 28th of February and then the 28th of March,
     * losing the 31st for good; asking how many whole months have passed and adding that many to
     * the anchor gives the 31st of March, because the clamping happens once, on the way out.
     *
     * That is also why this loops zero times whatever the gap: a weekly subscription anchored ten
     * years ago is one subtraction and one addition, not five hundred and twenty of them.
     */
    fun onOrAfter(today: LocalDate, anchor: LocalDate, period: BillingPeriod): LocalDate {
        if (!anchor.isBefore(today)) return anchor
        val unit = period.unit
        // between counts whole units, so the anchor plus that many is at or before today and one
        // more is past it - at most one step of correction, whichever period this is.
        val whole = unit.between(anchor, today)
        val reached = anchor.plus(whole, unit)
        return if (reached.isBefore(today)) anchor.plus(whole + 1, unit) else reached
    }

    /**
     * The calendar unit a period steps in.
     *
     * The same unit measures the gap and takes the step, so the two cannot disagree about what a
     * month is.
     */
    private val BillingPeriod.unit: ChronoUnit
        get() = when (this) {
            BillingPeriod.WEEKLY -> ChronoUnit.WEEKS
            BillingPeriod.MONTHLY -> ChronoUnit.MONTHS
            BillingPeriod.YEARLY -> ChronoUnit.YEARS
        }
}
