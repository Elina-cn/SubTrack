package com.elinacn.subtrack.ui.home

import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * What the category filter does to the list and to the total under the heading.
 *
 * The rates are the defaults, so everything here is priced in TRY and the total is plain addition
 * - the conversion has its own tests and is not what these are about.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelFilterTest {

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
                subscription(1, "Netflix", 15999, SubscriptionCategory.ENTERTAINMENT),
                subscription(2, "Spotify", 5990, SubscriptionCategory.ENTERTAINMENT),
                subscription(3, "Notion", 1000, SubscriptionCategory.PRODUCTIVITY),
                subscription(4, "Loose", 500, SubscriptionCategory.OTHER)
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
    fun uiState_nothingSelected_showsEverything() = runTest {
        collectState()

        val state = viewModel.uiState.value
        assertNull(state.categoryFilter)
        assertEquals(4, state.subscriptions.size)
        assertEquals(Money(15999 + 5990 + 1000 + 500), state.total)
    }

    @Test
    fun selectFilter_narrowsTheListToThatCategory() = runTest {
        collectState()

        filter(SubscriptionCategory.ENTERTAINMENT)

        val state = viewModel.uiState.value
        assertEquals(listOf("Netflix", "Spotify"), state.subscriptions.map { it.name })
    }

    @Test
    fun selectFilter_totalFollowsTheVisibleRows() = runTest {
        collectState()

        filter(SubscriptionCategory.ENTERTAINMENT)

        // The number under the heading has to be the sum of what is on screen.
        assertEquals(Money(15999 + 5990), viewModel.uiState.value.total)
    }

    @Test
    fun selectFilter_thenAll_bringsEverythingBack() = runTest {
        collectState()
        filter(SubscriptionCategory.PRODUCTIVITY)

        filter(null)

        val state = viewModel.uiState.value
        assertNull(state.categoryFilter)
        assertEquals(4, state.subscriptions.size)
        assertEquals(Money(15999 + 5990 + 1000 + 500), state.total)
    }

    @Test
    fun selectFilter_categoryWithNothingInIt_showsAnEmptyListButKnowsRowsExist() = runTest {
        collectState()

        filter(SubscriptionCategory.HEALTH)

        val state = viewModel.uiState.value
        assertTrue(state.subscriptions.isEmpty())
        assertEquals(Money.ZERO, state.total)
        // What the screen uses to say "nothing in this category" rather than "nothing yet".
        assertTrue(state.hasAnySubscriptions)
    }

    @Test
    fun uiState_storeIsEmpty_saysSoRegardlessOfTheFilter() = runTest {
        repository.setSubscriptions(emptyList())
        collectState()

        filter(SubscriptionCategory.HEALTH)

        assertFalse(viewModel.uiState.value.hasAnySubscriptions)
    }

    @Test
    fun delete_withAFilterOn_removesTheRowAndLeavesTheRestFiltered() = runTest {
        collectState()
        filter(SubscriptionCategory.ENTERTAINMENT)

        viewModel.onEvent(HomeEvent.Delete(id = 1))
        advanceUntilIdle()

        assertEquals(listOf("Spotify"), viewModel.uiState.value.subscriptions.map { it.name })
    }

    @Test
    fun undoDelete_withAFilterOn_putsTheRowBackWhereItWas() = runTest {
        collectState()
        filter(SubscriptionCategory.ENTERTAINMENT)
        viewModel.onEvent(HomeEvent.Delete(id = 1))
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.UndoDelete)
        advanceUntilIdle()

        // Restored with its original id, so the stored order puts it back in front of Spotify -
        // and the filter, being a plain filter over that order, shows the same thing.
        assertEquals(listOf("Netflix", "Spotify"), viewModel.uiState.value.subscriptions.map { it.name })
    }

    @Test
    fun undoDelete_withAFilterOn_alsoRestoresOrderInTheFullList() = runTest {
        collectState()
        filter(SubscriptionCategory.ENTERTAINMENT)
        viewModel.onEvent(HomeEvent.Delete(id = 1))
        advanceUntilIdle()
        viewModel.onEvent(HomeEvent.UndoDelete)
        advanceUntilIdle()

        filter(null)

        assertEquals(
            listOf("Netflix", "Spotify", "Notion", "Loose"),
            viewModel.uiState.value.subscriptions.map { it.name }
        )
    }

    private fun TestScope.filter(category: SubscriptionCategory?) {
        viewModel.onEvent(HomeEvent.SelectCategoryFilter(category))
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
        category: SubscriptionCategory
    ) = Subscription(
        id = id,
        name = name,
        price = Money(cents),
        currency = Currency.TRY,
        billingPeriod = BillingPeriod.MONTHLY,
        nextPaymentDate = null,
        category = category,
        iconKey = null,
        createdAt = 100 - id
    )
}
