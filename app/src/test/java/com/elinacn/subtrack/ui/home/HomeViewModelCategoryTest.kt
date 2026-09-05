package com.elinacn.subtrack.ui.home

import com.elinacn.subtrack.domain.model.Currency
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
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The category the add form carries, from the tap to the stored row and back to the default.
 *
 * The enum's own conversion is not retested here - SubscriptionMapperTest already covers both
 * directions and the fallback for a name it does not recognise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelCategoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneOffset.UTC)

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
            // Reminders already visible, so saving never arms the permission request.
            FakeReminderNotificationStatus(remindersVisible = true),
            clock
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_nothingPicked_startsOnOther() = runTest {
        collectState()

        assertEquals(SubscriptionCategory.OTHER, viewModel.uiState.value.selectedCategory)
    }

    @Test
    fun selectCategory_reachesTheScreen() = runTest {
        collectState()

        viewModel.onEvent(HomeEvent.SelectCategory(SubscriptionCategory.HEALTH))
        advanceUntilIdle()

        assertEquals(SubscriptionCategory.HEALTH, viewModel.uiState.value.selectedCategory)
    }

    @Test
    fun save_afterPickingACategory_storesThatCategory() = runTest {
        collectState()
        viewModel.onEvent(HomeEvent.SelectCategory(SubscriptionCategory.ENTERTAINMENT))
        advanceUntilIdle()

        save()

        assertEquals(SubscriptionCategory.ENTERTAINMENT, repository.inserted.single().category)
    }

    @Test
    fun save_withoutPickingACategory_storesOther() = runTest {
        collectState()

        save()

        // The field is optional; leaving it alone still writes a row.
        assertEquals(SubscriptionCategory.OTHER, repository.inserted.single().category)
    }

    @Test
    fun save_afterStoring_leavesTheFormOnOther() = runTest {
        collectState()
        viewModel.onEvent(HomeEvent.SelectCategory(SubscriptionCategory.PRODUCTIVITY))
        advanceUntilIdle()

        save()

        // Same rule as the name and the price: the next form starts empty.
        assertEquals(SubscriptionCategory.OTHER, viewModel.uiState.value.selectedCategory)
    }

    @Test
    fun dismissAddSheet_afterPicking_leavesTheFormOnOther() = runTest {
        collectState()
        viewModel.onEvent(HomeEvent.SelectCategory(SubscriptionCategory.HEALTH))
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.DismissAddSheet)
        advanceUntilIdle()

        // A pick that was abandoned must not come back next to a blank name and price.
        assertEquals(SubscriptionCategory.OTHER, viewModel.uiState.value.selectedCategory)
    }

    @Test
    fun save_rejectedEntry_keepsTheCategoryPicked() = runTest {
        collectState()
        viewModel.onEvent(HomeEvent.SelectCategory(SubscriptionCategory.HEALTH))
        advanceUntilIdle()

        // Empty name: nothing is stored and the sheet stays open with what was entered.
        viewModel.onEvent(HomeEvent.Save("", "159.99", Currency.TRY))
        advanceUntilIdle()

        assertEquals(SubscriptionCategory.HEALTH, viewModel.uiState.value.selectedCategory)
        assertEquals(0, repository.inserted.size)
    }

    private fun TestScope.save() {
        viewModel.onEvent(HomeEvent.Save("Netflix", "159.99", Currency.TRY))
        advanceUntilIdle()
    }

    /** WhileSubscribed keeps the state cold until something collects it. */
    private fun TestScope.collectState() {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
    }
}
