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
 * The category as it reaches storage.
 *
 * The form owns the pick until the user commits - the same place the name, the price, the currency
 * and the date live - so what the ViewModel can be asked about is what a save carries. Whether the
 * chip survives a rotation and resets afterwards is the sheet's rememberSaveable doing the same
 * thing it does for the other four fields, and is measured on a device.
 *
 * The enum's own conversion is not retested here: SubscriptionMapperTest already covers both
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
    fun save_withACategory_storesIt() = runTest {
        collectState()

        save(category = SubscriptionCategory.ENTERTAINMENT)

        assertEquals(SubscriptionCategory.ENTERTAINMENT, repository.inserted.single().category)
    }

    @Test
    fun save_categoryOmitted_storesOther() = runTest {
        collectState()

        // The event's default. The field is optional, and leaving it out still writes a row.
        viewModel.onEvent(HomeEvent.Save("Netflix", "159.99", Currency.TRY))
        advanceUntilIdle()

        assertEquals(SubscriptionCategory.OTHER, repository.inserted.single().category)
    }

    @Test
    fun save_twoSubscriptions_keepsEachCategoryWithItsOwnRow() = runTest {
        collectState()

        save(name = "Netflix", category = SubscriptionCategory.ENTERTAINMENT)
        save(name = "Gym", category = SubscriptionCategory.HEALTH)

        assertEquals(
            listOf(SubscriptionCategory.ENTERTAINMENT, SubscriptionCategory.HEALTH),
            repository.inserted.map { it.category }
        )
    }

    @Test
    fun save_rejectedEntry_storesNothingWhateverTheCategory() = runTest {
        collectState()

        // Empty name: validation stops the write before the category matters.
        viewModel.onEvent(
            HomeEvent.Save("", "159.99", Currency.TRY, null, SubscriptionCategory.HEALTH)
        )
        advanceUntilIdle()

        assertEquals(0, repository.inserted.size)
    }

    private fun TestScope.save(
        name: String = "Netflix",
        category: SubscriptionCategory
    ) {
        viewModel.onEvent(HomeEvent.Save(name, "159.99", Currency.TRY, null, category))
        advanceUntilIdle()
    }

    /** WhileSubscribed keeps the state cold until something collects it. */
    private fun TestScope.collectState() {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
    }
}
