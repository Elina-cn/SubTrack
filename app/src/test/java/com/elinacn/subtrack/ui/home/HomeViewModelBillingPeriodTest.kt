package com.elinacn.subtrack.ui.home

import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
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
 * The billing period as it reaches storage.
 *
 * Like the category, the form owns the choice until the user commits, so what the ViewModel can be
 * asked about is what a save carries. That the chip survives a rotation and returns to monthly
 * after a save is the sheet's rememberSaveable doing what it does for every other field, and is
 * measured on a device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelBillingPeriodTest {

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
    fun save_withAPeriod_storesIt() = runTest {
        collectState()

        save(BillingPeriod.YEARLY)

        assertEquals(BillingPeriod.YEARLY, repository.inserted.single().billingPeriod)
    }

    @Test
    fun save_periodOmitted_storesMonthly() = runTest {
        collectState()

        // The event's default, which is also what the form opens on.
        viewModel.onEvent(HomeEvent.Save("Netflix", "159.99", Currency.TRY))
        advanceUntilIdle()

        assertEquals(BillingPeriod.MONTHLY, repository.inserted.single().billingPeriod)
    }

    @Test
    fun save_threeSubscriptions_keepEachPeriodWithItsOwnRow() = runTest {
        collectState()

        save(BillingPeriod.MONTHLY, name = "Netflix")
        save(BillingPeriod.YEARLY, name = "Domain")
        save(BillingPeriod.WEEKLY, name = "Paper")

        assertEquals(
            listOf(BillingPeriod.MONTHLY, BillingPeriod.YEARLY, BillingPeriod.WEEKLY),
            repository.inserted.map { it.billingPeriod }
        )
    }

    @Test
    fun save_rejectedEntry_storesNothingWhateverThePeriod() = runTest {
        collectState()

        // Empty name: validation stops the write before the period matters.
        viewModel.onEvent(
            HomeEvent.Save("", "159.99", Currency.TRY, billingPeriod = BillingPeriod.WEEKLY)
        )
        advanceUntilIdle()

        assertEquals(0, repository.inserted.size)
    }

    private fun TestScope.save(period: BillingPeriod, name: String = "Netflix") {
        viewModel.onEvent(
            HomeEvent.Save(name, "159.99", Currency.TRY, billingPeriod = period)
        )
        advanceUntilIdle()
    }

    /** WhileSubscribed keeps the state cold until something collects it. */
    private fun TestScope.collectState() {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
    }
}
