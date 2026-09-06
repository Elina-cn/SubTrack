package com.elinacn.subtrack.ui.home

import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.domain.model.TotalPeriod
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
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The figure under the heading: which span it covers, and what the category filter does to it.
 *
 * The list is the one the phase was specified against - 100,00 a month, 1.200,00 a year and 10,00
 * a week - so the numbers here are the ones measured on a device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTotalPeriodTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneOffset.UTC)

    private lateinit var repository: FakeSubscriptionRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSubscriptionRepository()
        repository.setSubscriptions(
            listOf(
                subscription(1, "Monthly", 10_000, BillingPeriod.MONTHLY),
                subscription(2, "Yearly", 120_000, BillingPeriod.YEARLY),
                subscription(
                    id = 3,
                    name = "Weekly",
                    cents = 1_000,
                    period = BillingPeriod.WEEKLY,
                    category = SubscriptionCategory.HEALTH
                )
            )
        )
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
    fun uiState_opensOnTheMonthlyView() = runTest {
        collectState()

        val state = viewModel.uiState.value
        assertEquals(TotalPeriod.MONTHLY, state.totalPeriod)
        // 100,00 + 1.200,00 / 12 + 10,00 x 52 / 12 = 243,33
        assertEquals(Money(24_333), state.total)
    }

    @Test
    fun selectYearly_showsWhatTheSameListCostsInAYear() = runTest {
        collectState()

        select(TotalPeriod.YEARLY)

        val state = viewModel.uiState.value
        assertEquals(TotalPeriod.YEARLY, state.totalPeriod)
        // 100,00 x 12 + 1.200,00 + 10,00 x 52 = 2.920,00
        assertEquals(Money(292_000), state.total)
    }

    @Test
    fun yearlyTotal_isNotTheMonthlyFigureTimesTwelve() = runTest {
        collectState()
        val monthly = viewModel.uiState.value.total

        select(TotalPeriod.YEARLY)

        // 24.333 x 12 = 291.996. The four kuruş between them are the rounding the monthly view
        // has to do and the yearly one does not - which is why the yearly figure is computed from
        // the same unrounded sum rather than from the monthly figure.
        assertNotEquals(monthly * 12, viewModel.uiState.value.total)
        assertEquals(4L, viewModel.uiState.value.total.cents - (monthly * 12).cents)
    }

    @Test
    fun selectMonthlyAgain_bringsTheMonthlyFigureBack() = runTest {
        collectState()
        select(TotalPeriod.YEARLY)

        select(TotalPeriod.MONTHLY)

        assertEquals(Money(24_333), viewModel.uiState.value.total)
    }

    @Test
    fun filterAndYearlyView_workTogether() = runTest {
        collectState()

        viewModel.onEvent(HomeEvent.SelectCategoryFilter(SubscriptionCategory.HEALTH))
        advanceUntilIdle()
        select(TotalPeriod.YEARLY)

        val state = viewModel.uiState.value
        assertEquals(listOf("Weekly"), state.subscriptions.map { it.name })
        // Only the weekly row is visible: 10,00 x 52 = 520,00 a year.
        assertEquals(Money(52_000), state.total)
    }

    @Test
    fun filterWithNothingInIt_totalsZeroInEitherView() = runTest {
        collectState()

        viewModel.onEvent(HomeEvent.SelectCategoryFilter(SubscriptionCategory.PRODUCTIVITY))
        advanceUntilIdle()
        assertEquals(Money.ZERO, viewModel.uiState.value.total)

        select(TotalPeriod.YEARLY)
        assertEquals(Money.ZERO, viewModel.uiState.value.total)
    }

    @Test
    fun changingTheView_leavesTheListAlone() = runTest {
        collectState()
        val before = viewModel.uiState.value.subscriptions.map { it.name }

        select(TotalPeriod.YEARLY)

        // The switch changes one number, not what is on the list below it.
        assertEquals(before, viewModel.uiState.value.subscriptions.map { it.name })
    }

    private fun TestScope.select(period: TotalPeriod) {
        viewModel.onEvent(HomeEvent.SelectTotalPeriod(period))
        advanceUntilIdle()
    }

    /** WhileSubscribed keeps the state cold until something collects it. */
    private fun TestScope.collectState() {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
    }

    private fun subscription(
        id: Long,
        name: String,
        cents: Long,
        period: BillingPeriod,
        category: SubscriptionCategory = SubscriptionCategory.OTHER
    ) = Subscription(
        id = id,
        name = name,
        price = Money(cents),
        currency = Currency.TRY,
        billingPeriod = period,
        nextPaymentDate = null,
        category = category,
        iconKey = null,
        createdAt = 100 - id
    )
}
