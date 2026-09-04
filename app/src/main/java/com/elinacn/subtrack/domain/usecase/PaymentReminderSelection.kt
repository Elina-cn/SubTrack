package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.model.Subscription
import java.time.LocalDate

/**
 * A subscription worth reminding about, carrying the state that earned it a place.
 *
 * The countdown travels with the subscription because the reminder says three different things
 * and the caller would otherwise have to ask [PaymentCountdown] a second question it has already
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
     * How many days an overdue payment keeps being mentioned.
     *
     * A window rather than "forever" because the app deliberately never rolls a passed date
     * forward (ARCHITECTURE §17), so the overdue state is permanent - without a window the
     * reminder would be permanent too. See ARCHITECTURE §18.
     */
    const val OVERDUE_WITHIN_DAYS = 3L

    /**
     * The subscriptions to mention on [today], each with the reason it qualified.
     *
     * [today] is a parameter rather than a LocalDate.now() call inside, so the result is a
     * function of its inputs. Subscriptions without a date are skipped: there is nothing to
     * count towards.
     */
    fun on(today: LocalDate, subscriptions: List<Subscription>): List<PaymentReminder> =
        subscriptions.mapNotNull { subscription ->
            val nextPayment = subscription.nextPaymentDate ?: return@mapNotNull null
            val countdown = PaymentCountdown.between(today, nextPayment)
            val qualifies = when (countdown) {
                PaymentCountdown.DueToday -> true
                is PaymentCountdown.Upcoming -> countdown.days <= UPCOMING_WITHIN_DAYS
                is PaymentCountdown.Overdue -> countdown.days <= OVERDUE_WITHIN_DAYS
            }
            if (qualifies) PaymentReminder(subscription, countdown) else null
        }
}
