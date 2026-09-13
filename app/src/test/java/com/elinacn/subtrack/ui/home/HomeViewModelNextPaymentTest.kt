package com.elinacn.subtrack.ui.home

import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.domain.usecase.PaymentCountdown
import com.elinacn.subtrack.fake.FakeReminderNotificationStatus
import com.elinacn.subtrack.fake.FakeReminderStateRepository
import com.elinacn.subtrack.fake.FakeSettingsRepository
import com.elinacn.subtrack.fake.FakeSubscriptionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the screen counts towards once a passed date is caught up to today.
 *
 * Today is fixed at 15 March 2026, and the stored date is only ever read here - the anchor a test
 * puts in is the anchor it finds afterwards.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelNextPaymentTest {

    private val dispatcher = StandardTestDispatcher()
    private val today: LocalDate = LocalDate.of(2026, 3, 15)
    private val clock: Clock = Clock.fixed(
        today.atStartOfDay(ZoneId.of("UTC")).toInstant(),
        ZoneId.of("UTC")
    )

    private lateinit var repository: FakeSubscriptionRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSubscriptionRepository()
        viewModel = HomeViewModel(
            repository,
            FakeSettingsRepository(),
            FakeReminderStateRepository(),
            FakeReminderNotificationStatus(remindersVisible = true),
            clock
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun monthly_aPassedDate_countsTowardsTheNextOne() = runTest {
        // Anchored on the 31st of January. February has no 31st, but March does, so the next
        // payment is the 31st of March - sixteen days off - not the 28th of February.
        store(LocalDate.of(2026, 1, 31), BillingPeriod.MONTHLY)
        collectState()

        assertEquals(PaymentCountdown.Upcoming(days = 16), countdown())
    }

    @Test
    fun weekly_aDateTenYearsBehind_countsTowardsThisWeek() = runTest {
        // 533 weeks from the first of January 2016 lands on the 20th of March.
        store(LocalDate.of(2016, 1, 1), BillingPeriod.WEEKLY)
        collectState()

        assertEquals(PaymentCountdown.Upcoming(days = 5), countdown())
    }

    @Test
    fun yearly_aDateTenYearsBehind_landingOnToday_isDueToday() = runTest {
        store(LocalDate.of(2016, 3, 15), BillingPeriod.YEARLY)
        collectState()

        assertEquals(PaymentCountdown.DueToday, countdown())
    }

    @Test
    fun aDateInTheFuture_isNotTouched() = runTest {
        store(today.plusDays(3), BillingPeriod.MONTHLY)
        collectState()

        assertEquals(PaymentCountdown.Upcoming(days = 3), countdown())
    }

    @Test
    fun todaysDate_staysDueToday() = runTest {
        store(today, BillingPeriod.WEEKLY)
        collectState()

        // The one case left where the screen says something other than "days left": a payment owed
        // this morning is not one that has been made.
        assertEquals(PaymentCountdown.DueToday, countdown())
    }

    @Test
    fun noDate_noCountdown_andNothingIsInvented() = runTest {
        store(null, BillingPeriod.MONTHLY)
        collectState()

        assertTrue(viewModel.uiState.value.countdowns.isEmpty())
        assertNull(viewModel.uiState.value.subscriptions.single().nextPaymentDate)
    }

    @Test
    fun theStoredDateIsNeverWrittenBack() = runTest {
        val anchor = LocalDate.of(2016, 1, 1)
        store(anchor, BillingPeriod.WEEKLY)
        collectState()

        // The screen counts towards March 2026; the row still holds the day the user gave us, and
        // the edit screen in phase 15 will show that one.
        assertEquals(PaymentCountdown.Upcoming(days = 5), countdown())
        assertEquals(anchor, viewModel.uiState.value.subscriptions.single().nextPaymentDate)
        assertEquals(anchor, repository.getById(id = 1)?.nextPaymentDate)
    }

    @Test
    fun everyPeriod_countsTowardsSomethingThatIsNotBehind() = runTest {
        repository.setSubscriptions(
            BillingPeriod.entries.mapIndexed { index, period ->
                subscription(
                    id = index + 1L,
                    anchor = LocalDate.of(2019, 1, 31),
                    period = period
                )
            }
        )
        collectState()

        // Overdue is what the screen used to say about all three, and since the 12-2 hotfix the
        // type has no such case: the date the countdown is measured against is never in the past.
        // What is left to check is that each period found its own day from the same anchor - the
        // 31st of March, the 31st of January 2027, and the 19th of March.
        val countdowns = viewModel.uiState.value.countdowns
        assertEquals(3, countdowns.size)
        assertEquals(PaymentCountdown.Upcoming(days = 16), countdowns[MONTHLY_ID])
        assertEquals(PaymentCountdown.Upcoming(days = 322), countdowns[YEARLY_ID])
        assertEquals(PaymentCountdown.Upcoming(days = 4), countdowns[WEEKLY_ID])
    }

    private companion object {
        /** The ids [everyPeriod_countsTowardsSomethingThatIsNotBehind] hands out, in enum order. */
        const val MONTHLY_ID = 1L
        const val YEARLY_ID = 2L
        const val WEEKLY_ID = 3L
    }

    private fun countdown(): PaymentCountdown? =
        viewModel.uiState.value.countdowns.values.singleOrNull()

    private fun store(anchor: LocalDate?, period: BillingPeriod) {
        repository.setSubscriptions(listOf(subscription(id = 1, anchor = anchor, period = period)))
    }

    /** WhileSubscribed keeps the state cold until something collects it. */
    private fun TestScope.collectState() {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
    }

    private fun subscription(
        id: Long,
        anchor: LocalDate?,
        period: BillingPeriod
    ) = Subscription(
        id = id,
        name = "Test",
        price = Money(10_000),
        currency = Currency.TRY,
        billingPeriod = period,
        nextPaymentDate = anchor,
        category = SubscriptionCategory.OTHER,
        iconKey = null,
        createdAt = id
    )
}
