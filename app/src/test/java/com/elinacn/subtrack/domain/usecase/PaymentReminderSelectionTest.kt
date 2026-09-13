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
 *
 * Since the 12-2 hotfix the stored date is read as an **anchor**: what qualifies a subscription is
 * where its payment date has got to by today, the same figure the card counts towards. A date in
 * the past therefore no longer means "late" - it means the payment has already moved on.
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

    /**
     * Changed by the 12-2 hotfix: this used to assert Overdue(1).
     *
     * Yesterday's anchor on a monthly subscription is not a late payment, it is a payment made
     * last month - the next one falls on the 14th of April. The card has said so since 12-2; now
     * the reminder agrees with it instead of contradicting it.
     */
    @Test
    fun on_theAnchorWasYesterday_isNotSelected_becauseTheNextPaymentIsAMonthOff() {
        val netflix = subscription(id = 1, nextPaymentDate = LocalDate.of(2026, 3, 14))

        assertEquals(emptyList<PaymentReminder>(), PaymentReminderSelection.on(today, listOf(netflix)))
    }

    /**
     * Changed by the 12-2 hotfix: this used to assert Overdue(3), the inclusive edge of the
     * three-day window that no longer exists. The 12th of March anchors on the 12th of April.
     */
    @Test
    fun on_theAnchorWasThreeDaysAgo_isNotSelected() {
        val netflix = subscription(id = 1, nextPaymentDate = LocalDate.of(2026, 3, 12))

        assertEquals(emptyList<PaymentReminder>(), PaymentReminderSelection.on(today, listOf(netflix)))
    }

    /**
     * Same expectation as before the hotfix, different reason: it used to fall one day outside the
     * overdue window, and now there is no window to fall outside of - the next payment is simply
     * the 11th of April.
     */
    @Test
    fun on_theAnchorWasFourDaysAgo_isNotSelected() {
        val netflix = subscription(id = 1, nextPaymentDate = LocalDate.of(2026, 3, 11))

        assertEquals(emptyList<PaymentReminder>(), PaymentReminderSelection.on(today, listOf(netflix)))
    }

    // --- what a passed anchor actually qualifies on --------------------------------------------

    @Test
    fun on_aPassedAnchorLandingTomorrow_isSelected() {
        // Six days back, weekly: the next payment is the 16th, which is tomorrow.
        val netflix = subscription(
            id = 1,
            nextPaymentDate = LocalDate.of(2026, 3, 9),
            billingPeriod = BillingPeriod.WEEKLY
        )

        val selected = PaymentReminderSelection.on(today, listOf(netflix))

        assertEquals(listOf(PaymentReminder(netflix, PaymentCountdown.Upcoming(days = 1))), selected)
    }

    @Test
    fun on_aPassedAnchorLandingTwentyDaysOut_isNotSelected() {
        // The 4th of February, monthly: the 4th of March has gone too, so the next one is the 4th
        // of April - twenty days off, nowhere near the window.
        val netflix = subscription(id = 1, nextPaymentDate = LocalDate.of(2026, 2, 4))

        assertEquals(emptyList<PaymentReminder>(), PaymentReminderSelection.on(today, listOf(netflix)))
    }

    @Test
    fun on_aPassedAnchorLandingOnToday_isSelectedAsDueToday() {
        val netflix = subscription(id = 1, nextPaymentDate = LocalDate.of(2026, 2, 15))

        val selected = PaymentReminderSelection.on(today, listOf(netflix))

        assertEquals(listOf(PaymentReminder(netflix, PaymentCountdown.DueToday)), selected)
    }

    @Test
    fun on_thePeriodDecidesHowFarTheAnchorMoves() {
        val aWeekAgo = LocalDate.of(2026, 3, 8)
        val weekly = subscription(
            id = 1,
            name = "Weekly",
            nextPaymentDate = aWeekAgo,
            billingPeriod = BillingPeriod.WEEKLY
        )
        val monthly = subscription(id = 2, name = "Monthly", nextPaymentDate = aWeekAgo)
        val yearly = subscription(
            id = 3,
            name = "Yearly",
            nextPaymentDate = aWeekAgo,
            billingPeriod = BillingPeriod.YEARLY
        )

        val selected = PaymentReminderSelection.on(today, listOf(weekly, monthly, yearly))

        // The same anchor reaches three different days: today weekly, the 8th of April monthly,
        // the 8th of March 2027 yearly. Assuming monthly for all three is what phase 12 was for.
        assertEquals(listOf(PaymentReminder(weekly, PaymentCountdown.DueToday)), selected)
    }

    @Test
    fun on_aSelectedReminder_stillCarriesTheAnchorItWasStoredWith() {
        val anchor = LocalDate.of(2026, 3, 9)
        val netflix = subscription(
            id = 1,
            nextPaymentDate = anchor,
            billingPeriod = BillingPeriod.WEEKLY
        )

        val selected = PaymentReminderSelection.on(today, listOf(netflix))

        // Advancement happens on the way to the countdown and nowhere else: what the reminder
        // hands on is the row as stored (ARCHITECTURE section 17).
        assertEquals(anchor, selected.single().subscription.nextPaymentDate)
    }

    // --- lists, and the edges of the calendar --------------------------------------------------

    @Test
    fun on_subscriptionHasNoDate_isNotSelected() {
        val netflix = subscription(id = 1, nextPaymentDate = null)

        assertEquals(emptyList<PaymentReminder>(), PaymentReminderSelection.on(today, listOf(netflix)))
    }

    @Test
    fun on_emptyList_selectsNothing() {
        assertEquals(emptyList<PaymentReminder>(), PaymentReminderSelection.on(today, emptyList()))
    }

    /**
     * Changed by the 12-2 hotfix: OneLate and ThreeLate used to be selected as Overdue and are now
     * a month off. PassedLandsTomorrow is new and is the point of the whole change - a passed
     * anchor still qualifies, on the day it has actually reached.
     */
    @Test
    fun on_mixedList_selectsOnlyTheOnesInsideTheWindow() {
        val dueToday = subscription(id = 1, name = "Today", nextPaymentDate = today)
        val tomorrow = subscription(id = 2, name = "Tomorrow", nextPaymentDate = LocalDate.of(2026, 3, 16))
        val twoDaysOut = subscription(id = 3, name = "TwoOut", nextPaymentDate = LocalDate.of(2026, 3, 17))
        val oneDayLate = subscription(id = 4, name = "OneLate", nextPaymentDate = LocalDate.of(2026, 3, 14))
        val threeDaysLate = subscription(id = 5, name = "ThreeLate", nextPaymentDate = LocalDate.of(2026, 3, 12))
        val fourDaysLate = subscription(id = 6, name = "FourLate", nextPaymentDate = LocalDate.of(2026, 3, 11))
        val undated = subscription(id = 7, name = "Undated", nextPaymentDate = null)
        val passedLandsTomorrow = subscription(
            id = 8,
            name = "PassedLandsTomorrow",
            nextPaymentDate = LocalDate.of(2026, 3, 9),
            billingPeriod = BillingPeriod.WEEKLY
        )

        val selected = PaymentReminderSelection.on(
            today,
            listOf(
                dueToday, tomorrow, twoDaysOut, oneDayLate, threeDaysLate, fourDaysLate, undated,
                passedLandsTomorrow
            )
        )

        // Order follows the input list, and each entry carries its own reason.
        assertEquals(
            listOf(
                PaymentReminder(dueToday, PaymentCountdown.DueToday),
                PaymentReminder(tomorrow, PaymentCountdown.Upcoming(days = 1)),
                PaymentReminder(passedLandsTomorrow, PaymentCountdown.Upcoming(days = 1))
            ),
            selected
        )
        listOf("TwoOut", "OneLate", "ThreeLate", "FourLate", "Undated").forEach { name ->
            assertTrue("$name should not be selected", selected.none { it.subscription.name == name })
        }
    }

    /**
     * Changed by the 12-2 hotfix: lateMarch used to be selected as Overdue(3). The window it came
     * through is gone; the 28th of March anchors on the 28th of April.
     */
    @Test
    fun on_theWindowCrossesAMonthBoundary_stillCountsCalendarDays() {
        val lastDayOfMarch = LocalDate.of(2026, 3, 31)
        val firstOfApril = subscription(id = 1, nextPaymentDate = LocalDate.of(2026, 4, 1))
        val lateMarch = subscription(id = 2, name = "LateMarch", nextPaymentDate = LocalDate.of(2026, 3, 28))

        val selected = PaymentReminderSelection.on(lastDayOfMarch, listOf(firstOfApril, lateMarch))

        assertEquals(
            listOf(PaymentReminder(firstOfApril, PaymentCountdown.Upcoming(days = 1))),
            selected
        )
    }

    /**
     * Changed by the 12-2 hotfix, same reason as the month boundary above: lateDecember was an
     * Overdue(3) and is now the 28th of January. The weekly row is new and shows advancement
     * itself crossing the year.
     */
    @Test
    fun on_theWindowCrossesAYearBoundary_stillCountsCalendarDays() {
        val newYearsEve = LocalDate.of(2026, 12, 31)
        val firstOfJanuary = subscription(id = 1, nextPaymentDate = LocalDate.of(2027, 1, 1))
        val lateDecember = subscription(id = 2, name = "LateDecember", nextPaymentDate = LocalDate.of(2026, 12, 28))
        val weeklyFromChristmas = subscription(
            id = 3,
            name = "WeeklyFromChristmas",
            nextPaymentDate = LocalDate.of(2026, 12, 25),
            billingPeriod = BillingPeriod.WEEKLY
        )

        val selected = PaymentReminderSelection.on(
            newYearsEve,
            listOf(firstOfJanuary, lateDecember, weeklyFromChristmas)
        )

        // A week on from Christmas is the first of January, so the advancement crosses the year
        // and the countdown is still one calendar day.
        assertEquals(
            listOf(
                PaymentReminder(firstOfJanuary, PaymentCountdown.Upcoming(days = 1)),
                PaymentReminder(weeklyFromChristmas, PaymentCountdown.Upcoming(days = 1))
            ),
            selected
        )
    }

    private fun subscription(
        id: Long,
        name: String = "Netflix",
        nextPaymentDate: LocalDate?,
        billingPeriod: BillingPeriod = BillingPeriod.MONTHLY
    ) = Subscription(
        id = id,
        name = name,
        price = Money(9990),
        currency = Currency.TRY,
        billingPeriod = billingPeriod,
        nextPaymentDate = nextPaymentDate,
        category = SubscriptionCategory.OTHER,
        iconKey = null,
        createdAt = 0
    )
}
