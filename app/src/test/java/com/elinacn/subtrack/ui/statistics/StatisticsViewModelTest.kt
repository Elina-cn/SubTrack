package com.elinacn.subtrack.ui.statistics

import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.MonthlySnapshot
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.domain.usecase.TrendDirection
import com.elinacn.subtrack.fake.FakeMonthlySnapshotRepository
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
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * What the statistics screen is told, read from the ViewModel's only public surface.
 *
 * Nothing is mocked: the fakes actually hold rows, so a wrong grouping shows up as a wrong figure
 * rather than as a missing call (ARCHITECTURE §11). Every expectation below is worked out on paper
 * first - the default rates are 42,85 TRY to the dollar and 46,20 to the euro.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** Mid-September 2026, so "this month" is a fixed fact rather than whenever the suite runs. */
    private val clock = Clock.fixed(Instant.parse("2026-09-14T09:00:00Z"), ZoneOffset.UTC)
    private val thisMonth = YearMonth.of(2026, 9)

    private lateinit var repository: FakeSubscriptionRepository
    private lateinit var settings: FakeSettingsRepository
    private lateinit var snapshots: FakeMonthlySnapshotRepository
    private lateinit var viewModel: StatisticsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSubscriptionRepository()
        settings = FakeSettingsRepository()
        snapshots = FakeMonthlySnapshotRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- the breakdown ---------------------------------------------------------------------------

    @Test
    fun uiState_subscriptionsAcrossCategories_areGroupedAndOrderedByCost() = runTest {
        store(
            subscription(name = "Netflix", cents = 15_000, category = SubscriptionCategory.ENTERTAINMENT),
            subscription(name = "Spotify", cents = 5_000, category = SubscriptionCategory.ENTERTAINMENT),
            subscription(name = "Notion", cents = 10_000, category = SubscriptionCategory.PRODUCTIVITY),
            subscription(name = "Gym", cents = 30_000, category = SubscriptionCategory.HEALTH)
        )
        collectState()

        val shares = viewModel.uiState.value.categoryShares
        // 300,00 health · 200,00 entertainment · 100,00 productivity, biggest first.
        assertEquals(
            listOf(
                SubscriptionCategory.HEALTH,
                SubscriptionCategory.ENTERTAINMENT,
                SubscriptionCategory.PRODUCTIVITY
            ),
            shares.map { it.category }
        )
        assertEquals(listOf(Money(30_000), Money(20_000), Money(10_000)), shares.map { it.total })
        assertEquals(listOf(50, 33, 17), shares.map { it.percent })
    }

    @Test
    fun uiState_aCategoryWithNothingInIt_getsNoRow() = runTest {
        store(subscription(cents = 10_000, category = SubscriptionCategory.HEALTH))
        collectState()

        // Four categories exist; only the one with money in it is a row.
        assertEquals(1, viewModel.uiState.value.categoryShares.size)
        assertEquals(SubscriptionCategory.HEALTH, viewModel.uiState.value.categoryShares.single().category)
    }

    @Test
    fun uiState_oneCategory_takesTheWholeHundred() = runTest {
        store(
            subscription(name = "A", cents = 10_000, category = SubscriptionCategory.OTHER),
            subscription(name = "B", cents = 20_000, category = SubscriptionCategory.OTHER)
        )
        collectState()

        val share = viewModel.uiState.value.categoryShares.single()
        assertEquals(Money(30_000), share.total)
        assertEquals(100, share.percent)
    }

    @Test
    fun uiState_percentagesAlwaysAddUpToAHundred() = runTest {
        // Thirds: rounding each on its own would give 33 + 33 + 33 = 99.
        store(
            subscription(name = "A", cents = 10_000, category = SubscriptionCategory.ENTERTAINMENT),
            subscription(name = "B", cents = 10_000, category = SubscriptionCategory.PRODUCTIVITY),
            subscription(name = "C", cents = 10_000, category = SubscriptionCategory.HEALTH)
        )
        collectState()

        val percents = viewModel.uiState.value.categoryShares.map { it.percent }
        assertEquals(100, percents.sum())
        assertEquals(listOf(34, 33, 33), percents)
    }

    @Test
    fun uiState_noSubscriptions_isAnEmptyStateAndDividesNothing() = runTest {
        collectState()

        val state = viewModel.uiState.value
        assertTrue(state.categoryShares.isEmpty())
        assertTrue(state.mostExpensive.isEmpty())
        assertTrue(!state.hasAnySubscriptions)
        assertTrue(!state.isLoading)
    }

    @Test
    fun uiState_everythingRoundsToNothing_isNotAnEmptyState() = runTest {
        // A yearly subscription of one kuruş is a real subscription that costs nothing a month.
        store(
            subscription(
                cents = 1,
                category = SubscriptionCategory.OTHER,
                period = BillingPeriod.YEARLY
            )
        )
        collectState()

        val state = viewModel.uiState.value
        assertTrue("having a subscription is not an empty state", state.hasAnySubscriptions)
        // Nothing to draw a bar of, and nothing was divided by zero on the way here.
        assertTrue(state.categoryShares.isEmpty())
        assertEquals(Money(0), state.mostExpensive.single().monthlyCost)
    }

    // --- mixed currencies and periods, through the converter --------------------------------------

    @Test
    fun uiState_mixedCurrenciesAndPeriods_useTheSameChainAsTheDashboard() = runTest {
        store(
            // 120,00 USD a year is 10,00 USD a month, which is 428,50 TRY.
            subscription(
                name = "Cloud",
                cents = 12_000,
                currency = Currency.USD,
                period = BillingPeriod.YEARLY,
                category = SubscriptionCategory.PRODUCTIVITY
            ),
            // 10,00 EUR a week is 52 payments a year over twelve months: 43,33 EUR, 2.002,00 TRY.
            subscription(
                name = "Weekly",
                cents = 1_000,
                currency = Currency.EUR,
                period = BillingPeriod.WEEKLY,
                category = SubscriptionCategory.HEALTH
            ),
            subscription(
                name = "Local",
                cents = 10_000,
                category = SubscriptionCategory.ENTERTAINMENT
            )
        )
        collectState()

        val byCategory = viewModel.uiState.value.categoryShares.associate { it.category to it.total }
        assertEquals(Money(42_850), byCategory[SubscriptionCategory.PRODUCTIVITY])
        assertEquals(Money(200_200), byCategory[SubscriptionCategory.HEALTH])
        assertEquals(Money(10_000), byCategory[SubscriptionCategory.ENTERTAINMENT])
        assertEquals(100, viewModel.uiState.value.categoryShares.sumOf { it.percent })
    }

    @Test
    fun uiState_theMainCurrencyChanges_everyFigureFollowsIt() = runTest {
        store(subscription(name = "Local", cents = 42_850, category = SubscriptionCategory.OTHER))
        collectState()
        assertEquals(Money(42_850), viewModel.uiState.value.categoryShares.single().total)

        settings.setMainCurrency(Currency.USD)
        advanceUntilIdle()

        assertEquals(Currency.USD, viewModel.uiState.value.currency)
        assertEquals(Money(1_000), viewModel.uiState.value.categoryShares.single().total)
    }

    // --- the most expensive list ------------------------------------------------------------------

    @Test
    fun uiState_mostExpensive_isOrderedByMonthlyCostAndCutAtFive() = runTest {
        store(
            subscription(name = "One", cents = 1_000),
            subscription(name = "Two", cents = 2_000),
            subscription(name = "Three", cents = 3_000),
            subscription(name = "Four", cents = 4_000),
            subscription(name = "Five", cents = 5_000),
            subscription(name = "Six", cents = 6_000),
            subscription(name = "Seven", cents = 7_000)
        )
        collectState()

        val names = viewModel.uiState.value.mostExpensive.map { it.subscription.name }
        assertEquals(listOf("Seven", "Six", "Five", "Four", "Three"), names)
    }

    @Test
    fun uiState_mostExpensive_ranksOnMonthlyCostRatherThanPrice() = runTest {
        store(
            // The larger number on the card is the smaller cost per month.
            subscription(name = "Yearly", cents = 120_000, period = BillingPeriod.YEARLY),
            subscription(name = "Monthly", cents = 20_000)
        )
        collectState()

        val costs = viewModel.uiState.value.mostExpensive
        assertEquals(listOf("Monthly", "Yearly"), costs.map { it.subscription.name })
        assertEquals(listOf(Money(20_000), Money(10_000)), costs.map { it.monthlyCost })
    }

    @Test
    fun uiState_fewerThanFiveSubscriptions_listsThemAll() = runTest {
        store(
            subscription(name = "One", cents = 1_000),
            subscription(name = "Two", cents = 2_000)
        )
        collectState()

        assertEquals(2, viewModel.uiState.value.mostExpensive.size)
    }

    // --- the filter trap --------------------------------------------------------------------------

    @Test
    fun uiState_readsEverySubscription_whateverTheHomeScreenIsShowing() = runTest {
        store(
            subscription(name = "Ent", cents = 10_000, category = SubscriptionCategory.ENTERTAINMENT),
            subscription(name = "Health", cents = 5_000, category = SubscriptionCategory.HEALTH)
        )
        collectState()

        // The home screen's filter is its own state and never reaches this ViewModel; there is no
        // way to narrow what is counted here, which is the point.
        assertEquals(2, viewModel.uiState.value.categoryShares.size)
        assertEquals(2, viewModel.uiState.value.mostExpensive.size)
    }

    // --- the trend ------------------------------------------------------------------------------

    @Test
    fun uiState_noRecordedMonths_hasNoTrendAndNothingToDraw() = runTest {
        collectState()

        val state = viewModel.uiState.value
        assertTrue(state.trend.isEmpty())
        assertTrue(!state.canDrawTrend)
        assertNull(state.trendPeak)
        assertNull(state.monthlyChange)
    }

    @Test
    fun uiState_oneRecordedMonth_isAPointButNotAChart() = runTest {
        record(monthsAgo = 0, cents = 25_000)
        collectState()

        val state = viewModel.uiState.value
        assertEquals(1, state.trend.size)
        assertTrue("one month is a dot, not a direction", !state.canDrawTrend)
        assertNull(state.monthlyChange)
    }

    @Test
    fun uiState_twoRecordedMonths_areAChartWithAPeak() = runTest {
        record(monthsAgo = 1, cents = 20_000)
        record(monthsAgo = 0, cents = 25_000)
        collectState()

        val state = viewModel.uiState.value
        assertTrue(state.canDrawTrend)
        assertEquals(listOf(YearMonth.of(2026, 8), thisMonth), state.trend.map { it.period })
        assertEquals(Money(25_000), state.trendPeak)
    }

    @Test
    fun uiState_manyRecordedMonths_areCutToTheWindow() = runTest {
        (0L until 9L).forEach { back -> record(monthsAgo = back, cents = 10_000 + back) }
        collectState()

        val state = viewModel.uiState.value
        assertEquals(6, state.trend.size)
        assertEquals(YearMonth.of(2026, 4), state.trend.first().period)
        assertEquals(thisMonth, state.trend.last().period)
    }

    @Test
    fun uiState_aMonthWithNoRow_staysInPlaceWithNoFigure() = runTest {
        record(monthsAgo = 2, cents = 20_000)
        record(monthsAgo = 0, cents = 25_000)
        collectState()

        val state = viewModel.uiState.value
        assertEquals(listOf(Money(20_000), null, Money(25_000)), state.trend.map { it.total })
        // Last month has no figure, so there is nothing to compare this month with.
        assertNull(state.monthlyChange)
    }

    @Test
    fun uiState_monthsInAnotherCurrency_areReportedRatherThanConverted() = runTest {
        record(monthsAgo = 1, cents = 100_000, currency = Currency.TRY)
        record(monthsAgo = 0, cents = 2_500, currency = Currency.USD)
        settings.setMainCurrency(Currency.USD)
        collectState()

        val state = viewModel.uiState.value
        // The lira month is not redrawn at today's rate; it is left out and counted.
        assertEquals(listOf(thisMonth), state.trend.map { it.period })
        assertEquals(1, state.monthsInOtherCurrency)
    }

    @Test
    fun uiState_theMainCurrencyChanges_theTrendFollowsItAndTheOldMonthsDropOut() = runTest {
        record(monthsAgo = 1, cents = 100_000, currency = Currency.TRY)
        record(monthsAgo = 0, cents = 120_000, currency = Currency.TRY)
        collectState()
        assertEquals(2, viewModel.uiState.value.trend.size)

        settings.setMainCurrency(Currency.USD)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.trend.isEmpty())
        assertEquals(2, state.monthsInOtherCurrency)
        assertNull("apples and pears cannot be subtracted", state.monthlyChange)
    }

    @Test
    fun uiState_recordedHistoryButNoSubscriptionsLeft_isNotAnEmptyScreen() = runTest {
        record(monthsAgo = 1, cents = 100_000)
        record(monthsAgo = 0, cents = 0)
        collectState()

        val state = viewModel.uiState.value
        assertTrue("the subscriptions are gone", !state.hasAnySubscriptions)
        assertTrue("but the months they cost money in are not", !state.hasNothingToShow)
    }

    @Test
    fun uiState_noSubscriptionsAndOnlyThisMonthRecorded_isAnEmptyScreen() = runTest {
        // A clean install: the recorder leaves one row saying "looked, and it was nothing".
        record(monthsAgo = 0, cents = 0)
        collectState()

        assertTrue(viewModel.uiState.value.hasNothingToShow)
    }

    // --- against last month ---------------------------------------------------------------------

    @Test
    fun uiState_spendingWentUp_isAnIncreaseOfTheDifference() = runTest {
        record(monthsAgo = 1, cents = 20_000)
        record(monthsAgo = 0, cents = 25_000)
        collectState()

        val change = viewModel.uiState.value.monthlyChange
        assertEquals(TrendDirection.UP, change?.direction)
        assertEquals(Money(5_000), change?.amount)
    }

    @Test
    fun uiState_spendingWentDown_isADecreaseOfTheDifference() = runTest {
        record(monthsAgo = 1, cents = 25_000)
        record(monthsAgo = 0, cents = 20_000)
        collectState()

        val change = viewModel.uiState.value.monthlyChange
        assertEquals(TrendDirection.DOWN, change?.direction)
        assertEquals(Money(5_000), change?.amount)
    }

    @Test
    fun uiState_spendingStayedStill_isNoChangeRatherThanNoComparison() = runTest {
        record(monthsAgo = 1, cents = 25_000)
        record(monthsAgo = 0, cents = 25_000)
        collectState()

        val change = viewModel.uiState.value.monthlyChange
        assertEquals(TrendDirection.UNCHANGED, change?.direction)
        assertEquals(Money.ZERO, change?.amount)
    }

    @Test
    fun uiState_thereIsNoLastMonth_showsNoComparison() = runTest {
        record(monthsAgo = 0, cents = 25_000)
        collectState()

        assertNull(viewModel.uiState.value.monthlyChange)
    }

    private fun store(vararg subscriptions: Subscription) {
        repository.setSubscriptions(subscriptions.toList())
    }

    /**
     * Puts rows in the snapshot table the way the recorder would have, counting back from this
     * month - `record(0, ...)` is September, `record(1, ...)` is August.
     */
    private suspend fun record(monthsAgo: Long, cents: Long, currency: Currency = Currency.TRY) {
        snapshots.upsert(
            MonthlySnapshot(
                period = thisMonth.minusMonths(monthsAgo),
                total = Money(cents),
                currency = currency,
                recordedAt = 1_000L
            )
        )
    }

    /** WhileSubscribed keeps the state cold until something collects it. */
    private fun TestScope.collectState() {
        viewModel = StatisticsViewModel(repository, settings, snapshots, clock)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
    }

    private var nextId = 1L

    private fun subscription(
        name: String = "Test",
        cents: Long,
        currency: Currency = Currency.TRY,
        period: BillingPeriod = BillingPeriod.MONTHLY,
        category: SubscriptionCategory = SubscriptionCategory.OTHER
    ) = Subscription(
        id = nextId++,
        name = name,
        price = Money(cents),
        currency = currency,
        billingPeriod = period,
        nextPaymentDate = null,
        category = category,
        iconKey = null,
        createdAt = nextId
    )
}
