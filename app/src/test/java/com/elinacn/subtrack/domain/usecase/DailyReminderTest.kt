package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.fake.FakeReminderStateRepository
import com.elinacn.subtrack.fake.FakeSubscriptionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * At most one reminder a day (PROJECT_SPEC), whatever time of day the runs happen at.
 *
 * The runs here only differ in the day they are handed: the time of day never enters the rule,
 * which is exactly the point - a late run at 08:00 and the 09:00 run are the same day.
 */
class DailyReminderTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 2)

    @Test
    fun firstRunOfTheDay_postsAndRecordsTheDay() = runTest {
        val state = FakeReminderStateRepository()
        val posted = mutableListOf<List<PaymentReminder>>()

        val shown = reminder(state).run(today) { posted += it; true }

        assertTrue(shown)
        assertEquals(1, posted.size)
        assertEquals(today.toEpochDay(), state.lastNotifiedDay())
    }

    @Test
    fun lateRunAtEightThenTheNineOClockRun_onlyOneReminder() = runTest {
        // Yesterday's run was held back and went out this morning at 08:00; today's own run
        // follows at 09:00. Both are "today", so the second one stays silent.
        val state = FakeReminderStateRepository()
        val reminder = reminder(state)
        var posts = 0

        val lateRun = reminder.run(today) { posts++; true }
        val nineOClockRun = reminder.run(today) { posts++; true }

        assertTrue(lateRun)
        assertFalse(nineOClockRun)
        assertEquals(1, posts)
    }

    @Test
    fun theNextDay_postsAgain() = runTest {
        val state = FakeReminderStateRepository(notifiedDay = today.toEpochDay())
        var posts = 0

        val shown = reminder(state).run(today.plusDays(1)) { posts++; true }

        assertTrue(shown)
        assertEquals(1, posts)
    }

    @Test
    fun notificationNotShown_dayStaysOpen_aLaterRunPosts() = runTest {
        // Notifications off at 09:00, back on by the next run: that run still owes the reminder.
        val state = FakeReminderStateRepository()
        val reminder = reminder(state)

        val blocked = reminder.run(today) { false }
        assertNull(state.lastNotifiedDay())

        val later = reminder.run(today) { true }

        assertFalse(blocked)
        assertTrue(later)
        assertEquals(today.toEpochDay(), state.lastNotifiedDay())
    }

    @Test
    fun nothingDue_postsNothingAndLeavesTheDayOpen() = runTest {
        val state = FakeReminderStateRepository()
        var posts = 0

        val shown = reminder(state, due = today.plusDays(5)).run(today) { posts++; true }

        assertFalse(shown)
        assertEquals(0, posts)
        assertNull(state.lastNotifiedDay())
    }

    private suspend fun reminder(
        state: FakeReminderStateRepository,
        due: LocalDate = today.plusDays(1)
    ): DailyReminder {
        val subscriptions = FakeSubscriptionRepository()
        subscriptions.insert(
            Subscription(
                id = 0,
                name = "Netflix",
                price = Money(9990),
                currency = Currency.TRY,
                billingPeriod = BillingPeriod.MONTHLY,
                nextPaymentDate = due,
                category = SubscriptionCategory.OTHER,
                iconKey = null,
                createdAt = 0
            )
        )
        return DailyReminder(subscriptions, state)
    }
}
