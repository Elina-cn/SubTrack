package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.model.Subscription
import java.time.LocalDate

/**
 * A subscription worth reminding about, carrying the state that earned it a place.
 *
 * The countdown travels with the subscription because the reminder says two different things and
 * the caller would otherwise have to ask [PaymentCountdown] a second question it has already
 * answered here.
 */
data class PaymentReminder(
    val subscription: Subscription,
    val countdown: PaymentCountdown
)

/** Picks the subscriptions a reminder should mention on a given day. */
object PaymentReminderSelection {

    /** How many days ahead an upcoming payment starts being mentioned. */
    const val UPCOMING_WITHIN_DAYS = 1L

    /**
     * The subscriptions to mention on [today], each with the reason it qualified.
     *
     * [today] is a parameter rather than a LocalDate.now() call inside, so the result is a
     * function of its inputs. Subscriptions without a date are skipped: there is nothing to
     * count towards.
     *
     * **The stored date is an anchor, not a due date.** What is measured here is the same thing
     * the card counts towards - the anchor caught up to today by whole billing periods
     * ([NextPaymentDate]) - so the two can never say different things about the same row. Reading
     * the anchor directly is what made a monthly subscription "1 day overdue" in the shade while
     * its card said "29 days left" (ARCHITECTURE §18).
     */
    fun on(today: LocalDate, subscriptions: List<Subscription>): List<PaymentReminder> =
        subscriptions.mapNotNull { subscription ->
            val anchor = subscription.nextPaymentDate ?: return@mapNotNull null
            val due = NextPaymentDate.onOrAfter(today, anchor, subscription.billingPeriod)
            val countdown = PaymentCountdown.between(today, due)
            val qualifies = when (countdown) {
                PaymentCountdown.DueToday -> true
                is PaymentCountdown.Upcoming -> countdown.days <= UPCOMING_WITHIN_DAYS
            }
            if (qualifies) PaymentReminder(subscription, countdown) else null
        }
}
