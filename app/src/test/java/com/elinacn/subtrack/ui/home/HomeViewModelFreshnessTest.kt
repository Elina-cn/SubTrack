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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Home is under every other screen, and those screens change its inputs. What it shows when the
 * user comes back has to be the current answer from the first value, not the one it had when they
 * left (ARCHITECTURE section 29).
 *
 * Each test leaves home for a minute - far past the five-second stop timeout that used to shut the
 * flow down - changes something, and reads the first value a returning screen would draw.
 *
 * The list is the phase 16u fixture: ₺2.925,40 a month, all in lira, shown in dollars. At the
 * shipped 42,85 that is $68,27; at 39,59 it is $73,89 - the two totals the tester reported.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelFreshnessTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = MovableClock(LocalDate.of(2026, 9, 28))
    private lateinit var repository: FakeSubscriptionRepository
    private lateinit var settings: FakeSettingsRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSubscriptionRepository()
        repository.setSubscriptions(listOf(lira(292_540)))
        settings = FakeSettingsRepository(initial = Currency.USD)
        viewModel = HomeViewModel(
            repository,
            settings,
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
    fun rateChangedWhileAwayFromHome_isAlreadyInTheTotalOnReturn() = runTest {
        settings.setRates(mapOf(Currency.USD to 395_900L))
        val screen = showHome()
        assertEquals(Money(7_389), viewModel.uiState.value.total)

        leave(screen)
        settings.resetRates()
        advanceUntilIdle()

        assertEquals(Money(6_827), viewModel.uiState.value.total)
    }

    @Test
    fun mainCurrencyChangedWhileAwayFromHome_isAlreadyInTheTotalOnReturn() = runTest {
        val screen = showHome()
        assertEquals(Money(6_827), viewModel.uiState.value.total)

        leave(screen)
        settings.setMainCurrency(Currency.TRY)
        advanceUntilIdle()

        assertEquals(Money(292_540), viewModel.uiState.value.total)
        assertEquals(Currency.TRY, viewModel.uiState.value.baseCurrency)
    }

    /** The edit screen's case: a price changed there, and home comes back into view. */
    @Test
    fun listChangedWhileAwayFromHome_isAlreadyInTheTotalOnReturn() = runTest {
        val screen = showHome()

        leave(screen)
        repository.setSubscriptions(listOf(lira(342_750)))
        advanceUntilIdle()

        // 3.427,50 / 42,85 = 79,99
        assertEquals(Money(7_999), viewModel.uiState.value.total)
    }

    /**
     * The restart after the stop timeout used to be what moved "today" on. The flow no longer
     * stops, so the screen coming back says so itself.
     */
    @Test
    fun screenStarted_onALaterDay_movesTheCountdownsOn() = runTest {
        repository.setSubscriptions(listOf(lira(292_540, nextPayment = LocalDate.of(2026, 10, 1))))
        val screen = showHome()
        assertEquals(PaymentCountdown.Upcoming(3), viewModel.uiState.value.countdowns[1L])

        leave(screen)
        clock.today = LocalDate.of(2026, 9, 29)
        viewModel.onEvent(HomeEvent.ScreenStarted)
        advanceUntilIdle()

        assertEquals(PaymentCountdown.Upcoming(2), viewModel.uiState.value.countdowns[1L])
    }

    // --- helpers --------------------------------------------------------------------------

    /** Home on screen: something collects its state, as the composable does. */
    private fun TestScope.showHome(): Job {
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        return job
    }

    /** Another screen on top: the collector goes, and a minute passes. */
    private fun TestScope.leave(screen: Job) {
        screen.cancel()
        advanceTimeBy(LEFT_FOR_MS)
    }

    private fun lira(cents: Long, nextPayment: LocalDate? = null) = Subscription(
        id = 1,
        name = "Netflix",
        price = Money(cents),
        currency = Currency.TRY,
        billingPeriod = BillingPeriod.MONTHLY,
        nextPaymentDate = nextPayment,
        category = SubscriptionCategory.ENTERTAINMENT,
        iconKey = null,
        createdAt = 0
    )

    /** A clock whose day a test can move. */
    private class MovableClock(var today: LocalDate) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = today.atStartOfDay(ZoneOffset.UTC).toInstant()
    }

    private companion object {
        /** Well past the five seconds WhileSubscribed used to wait before stopping. */
        const val LEFT_FOR_MS = 60_000L
    }
}
