package com.elinacn.subtrack.ui.statistics

import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

    private lateinit var repository: FakeSubscriptionRepository
    private lateinit var settings: FakeSettingsRepository
    private lateinit var viewModel: StatisticsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSubscriptionRepository()
        settings = FakeSettingsRepository()
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

    private fun store(vararg subscriptions: Subscription) {
        repository.setSubscriptions(subscriptions.toList())
    }

    /** WhileSubscribed keeps the state cold until something collects it. */
    private fun TestScope.collectState() {
        viewModel = StatisticsViewModel(repository, settings)
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
