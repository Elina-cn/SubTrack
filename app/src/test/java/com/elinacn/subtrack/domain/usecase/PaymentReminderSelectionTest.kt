package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Every case names its own dates. The selection is a function of its inputs, so none of this
 * depends on when the suite runs.
 */
class PaymentReminderSelectionTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    @Test
    fun on_paymentIsToday_isSelectedAsDueToday() {
        val netflix = subscription(id = 1, name = "Netflix", nextPaymentDate = today)

        val selected = PaymentReminderSelection.on(today, listOf(netflix))

        assertEquals(listOf(PaymentReminder(netflix, PaymentCountdown.DueToday)), selected)
    }

    @Test
    fun on_paymentIsTomorrow_isSelectedAsUpcoming() {
        val netflix = subscription(id = 1, nextPaymentDate = LocalDate.of(2026, 3, 16))

        val selected = PaymentReminderSelection.on(today, listOf(netflix))

        assertEquals(listOf(PaymentReminder(netflix, PaymentCountdown.Upcoming(days = 1))), selected)
    }

    @Test
    fun on_paymentIsTwoDaysAway_isNotSelected() {
        val netflix = subscription(id = 1, nextPaymentDate = LocalDate.of(2026, 3, 17))

        // The upcoming window is one day; two days out is outside it.
        assertEquals(emptyList<PaymentReminder>(), PaymentReminderSelection.on(today, listOf(netflix)))
    }

    @Test
    fun on_paymentWasYesterday_isSelectedAsOverdue() {
        val netflix = subscription(id = 1, nextPaymentDate = LocalDate.of(2026, 3, 14))

        val selected = PaymentReminderSelection.on(today, listOf(netflix))

        assertEquals(listOf(PaymentReminder(netflix, PaymentCountdown.Overdue(days = 1))), selected)
    }

    @Test
    fun on_paymentIsThreeDaysOverdue_isStillSelected() {
        val netflix = subscription(id = 1, nextPaymentDate = LocalDate.of(2026, 3, 12))

        // The inclusive edge of the overdue window.
        val selected = PaymentReminderSelection.on(today, listOf(netflix))

        assertEquals(listOf(PaymentReminder(netflix, PaymentCountdown.Overdue(days = 3))), selected)
    }

    @Test
    fun on_paymentIsFourDaysOverdue_isNotSelected() {
        val netflix = subscription(id = 1, nextPaymentDate = LocalDate.of(2026, 3, 11))

        // One day past the edge. Without this stop the reminder would never end, because nothing
        // rolls a passed date forward.
        assertEquals(emptyList<PaymentReminder>(), PaymentReminderSelection.on(today, listOf(netflix)))
    }

    @Test
    fun on_subscriptionHasNoDate_isNotSelected() {
        val netflix = subscription(id = 1, nextPaymentDate = null)

        assertEquals(emptyList<PaymentReminder>(), PaymentReminderSelection.on(today, listOf(netflix)))
    }

    @Test
    fun on_emptyList_selectsNothing() {
        assertEquals(emptyList<PaymentReminder>(), PaymentReminderSelection.on(today, emptyList()))
    }

    @Test
    fun on_mixedList_selectsOnlyTheOnesInsideTheWindows() {
        val dueToday = subscription(id = 1, name = "Today", nextPaymentDate = today)
        val tomorrow = subscription(id = 2, name = "Tomorrow", nextPaymentDate = LocalDate.of(2026, 3, 16))
        val twoDaysOut = subscription(id = 3, name = "TwoOut", nextPaymentDate = LocalDate.of(2026, 3, 17))
        val oneDayLate = subscription(id = 4, name = "OneLate", nextPaymentDate = LocalDate.of(2026, 3, 14))
        val threeDaysLate = subscription(id = 5, name = "ThreeLate", nextPaymentDate = LocalDate.of(2026, 3, 12))
        val fourDaysLate = subscription(id = 6, name = "FourLate", nextPaymentDate = LocalDate.of(2026, 3, 11))
        val undated = subscription(id = 7, name = "Undated", nextPaymentDate = null)

        val selected = PaymentReminderSelection.on(
            today,
            listOf(dueToday, tomorrow, twoDaysOut, oneDayLate, threeDaysLate, fourDaysLate, undated)
        )

        // Order follows the input list, and each entry carries its own reason.
        assertEquals(
            listOf(
                PaymentReminder(dueToday, PaymentCountdown.DueToday),
                PaymentReminder(tomorrow, PaymentCountdown.Upcoming(days = 1)),
                PaymentReminder(oneDayLate, PaymentCountdown.Overdue(days = 1)),
                PaymentReminder(threeDaysLate, PaymentCountdown.Overdue(days = 3))
            ),
            selected
        )
        assertTrue(selected.none { it.subscription.name == "TwoOut" })
        assertTrue(selected.none { it.subscription.name == "FourLate" })
        assertTrue(selected.none { it.subscription.name == "Undated" })
    }

    @Test
    fun on_windowCrossesAMonthBoundary_stillCountsCalendarDays() {
        val lastDayOfMarch = LocalDate.of(2026, 3, 31)
        val firstOfApril = subscription(id = 1, nextPaymentDate = LocalDate.of(2026, 4, 1))
        val lateMarch = subscription(id = 2, nextPaymentDate = LocalDate.of(2026, 3, 28))

        val selected = PaymentReminderSelection.on(lastDayOfMarch, listOf(firstOfApril, lateMarch))

        assertEquals(
            listOf(
                PaymentReminder(firstOfApril, PaymentCountdown.Upcoming(days = 1)),
                PaymentReminder(lateMarch, PaymentCountdown.Overdue(days = 3))
            ),
            selected
        )
    }

    @Test
    fun on_windowCrossesAYearBoundary_stillCountsCalendarDays() {
        val newYearsEve = LocalDate.of(2026, 12, 31)
        val firstOfJanuary = subscription(id = 1, nextPaymentDate = LocalDate.of(2027, 1, 1))
        val lateDecember = subscription(id = 2, nextPaymentDate = LocalDate.of(2026, 12, 28))
        val tooLate = subscription(id = 3, name = "TooLate", nextPaymentDate = LocalDate.of(2026, 12, 27))

        val selected = PaymentReminderSelection.on(
            newYearsEve,
            listOf(firstOfJanuary, lateDecember, tooLate)
        )

        assertEquals(
            listOf(
                PaymentReminder(firstOfJanuary, PaymentCountdown.Upcoming(days = 1)),
                PaymentReminder(lateDecember, PaymentCountdown.Overdue(days = 3))
            ),
            selected
        )
    }

    private fun subscription(
        id: Long,
        name: String = "Netflix",
        nextPaymentDate: LocalDate?
    ) = Subscription(
        id = id,
        name = name,
        price = Money(9990),
        currency = Currency.TRY,
        billingPeriod = BillingPeriod.MONTHLY,
        nextPaymentDate = nextPaymentDate,
        category = SubscriptionCategory.OTHER,
        iconKey = null,
        createdAt = 0
    )
}
