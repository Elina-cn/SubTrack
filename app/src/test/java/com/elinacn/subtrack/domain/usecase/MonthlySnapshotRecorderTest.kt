package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.fake.FakeMonthlySnapshotRepository
import com.elinacn.subtrack.fake.FakeReminderNotificationStatus
import com.elinacn.subtrack.fake.FakeReminderStateRepository
import com.elinacn.subtrack.fake.FakeSettingsRepository
import com.elinacn.subtrack.fake.FakeSubscriptionRepository
import com.elinacn.subtrack.ui.home.HomeEvent
import com.elinacn.subtrack.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * What reaches the monthly snapshots table, and what must not.
 *
 * The clock is fixed at 13 September 2026, so every expectation names the month it belongs to.
 * Nothing is mocked: the recorder runs against the same fakes the ViewModel does, which is what
 * lets the filter case below be a real test rather than a restatement of the code.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthlySnapshotRecorderTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 9, 13)
    private val thisMonth: YearMonth = YearMonth.of(2026, 9)
    private val clock: Clock = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone)

    private lateinit var subscriptions: FakeSubscriptionRepository
    private lateinit var settings: FakeSettingsRepository
    private lateinit var snapshots: FakeMonthlySnapshotRepository
    private lateinit var recorder: MonthlySnapshotRecorder

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        subscriptions = FakeSubscriptionRepository()
        settings = FakeSettingsRepository()
        snapshots = FakeMonthlySnapshotRepository()
        recorder = MonthlySnapshotRecorder(subscriptions, settings, snapshots, clock)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- the three things that change a total ---------------------------------------------------

    @Test
    fun record_aSubscriptionIsAdded_writesThisMonthsTotal() = runTest {
        startRecording()

        subscriptions.insert(subscription(price = 15_999))
        settle()

        assertEquals(1, snapshots.rows.size)
        assertEquals(thisMonth, snapshots.rows.single().period)
        assertEquals(Money(15_999), snapshots.rows.single().total)
        assertEquals(Currency.TRY, snapshots.rows.single().currency)
    }

    @Test
    fun record_aSubscriptionIsDeleted_writesTheLowerTotal() = runTest {
        val id = subscriptions.insert(subscription(price = 15_999))
        subscriptions.insert(subscription(price = 5_990))
        startRecording()

        subscriptions.deleteById(id)
        settle()

        assertEquals(Money(5_990), snapshots.rows.single().total)
    }

    @Test
    fun record_aDeletionIsUndone_writesTheTotalBack() = runTest {
        val id = subscriptions.insert(subscription(price = 15_999))
        startRecording()
        val removed = requireNotNull(subscriptions.getById(id))
        subscriptions.deleteById(id)
        settle()
        assertEquals(Money(0), snapshots.rows.single().total)

        subscriptions.insert(removed)
        settle()

        assertEquals(Money(15_999), snapshots.rows.single().total)
    }

    // --- one month, one row ---------------------------------------------------------------------

    @Test
    fun record_aSecondChangeInTheSameMonth_revisesTheRow() = runTest {
        startRecording()
        // Starting on an empty list already records this month at zero, so count from there.
        val writesBefore = snapshots.upserts.size

        subscriptions.insert(subscription(price = 10_000))
        settle()
        subscriptions.insert(subscription(price = 25_000))
        settle()

        assertEquals("a second row was opened for the same month", 1, snapshots.rows.size)
        assertEquals(Money(35_000), snapshots.rows.single().total)
        // Two changes, two writes, one row: each revised what was there.
        assertEquals(writesBefore + 2, snapshots.upserts.size)
    }

    @Test
    fun record_theTotalDidNotMove_doesNotRewriteTheRow() = runTest {
        val id = subscriptions.insert(subscription(name = "Netflix", price = 15_999))
        startRecording()
        settle()
        val writesSoFar = snapshots.upserts.size

        // A rename re-emits the list without touching what it costs.
        subscriptions.update(requireNotNull(subscriptions.getById(id)).copy(name = "Renamed"))
        settle()

        assertEquals(writesSoFar, snapshots.upserts.size)
    }

    // --- the empty list -------------------------------------------------------------------------

    @Test
    fun record_noSubscriptions_writesZeroRatherThanNothing() = runTest {
        startRecording()
        settle()

        // Phase 13 has to tell "that month cost nothing" from "that month has no record", and it
        // can only do that if absence means absence.
        assertEquals(1, snapshots.rows.size)
        assertEquals(Money(0), snapshots.rows.single().total)
    }

    // --- the currency ---------------------------------------------------------------------------

    @Test
    fun record_theMainCurrencyChanges_writesTheNewOneWithItsOwnFigure() = runTest {
        subscriptions.insert(subscription(price = 42_850))
        startRecording()
        settle()
        assertEquals(Currency.TRY, snapshots.rows.single().currency)

        settings.setMainCurrency(Currency.USD)
        settle()

        // 428,50 TRY at the default 42,85 TRY per dollar is ten dollars.
        assertEquals(Currency.USD, snapshots.rows.single().currency)
        assertEquals(Money(1_000), snapshots.rows.single().total)
    }

    @Test
    fun record_anotherMonth_isItsOwnRowAndLeavesTheFirstAlone() = runTest {
        // Last month's figure, as if it had been recorded then.
        val august = com.elinacn.subtrack.domain.model.MonthlySnapshot(
            period = YearMonth.of(2026, 8),
            total = Money(9_900),
            currency = Currency.EUR,
            recordedAt = 1
        )
        snapshots.upsert(august)
        startRecording()

        subscriptions.insert(subscription(price = 15_999))
        settle()

        assertEquals(2, snapshots.rows.size)
        // The month that is not being recorded keeps everything, currency included.
        assertEquals(august, snapshots.rows.first())
        assertEquals(thisMonth, snapshots.rows.last().period)
    }

    // --- the filter trap ------------------------------------------------------------------------

    /**
     * The test this phase exists to pass.
     *
     * The home screen's total follows the category filter, so anything recording from there would
     * write the filtered figure. The recorder reads the repository instead, and the two numbers
     * are asserted side by side to prove they are allowed to differ.
     */
    @Test
    fun record_aCategoryFilterIsOn_stillWritesTheTotalOfEverything() = runTest {
        val viewModel = HomeViewModel(
            subscriptions,
            settings,
            FakeReminderStateRepository(),
            FakeReminderNotificationStatus(remindersVisible = true),
            clock
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        startRecording()

        viewModel.onEvent(
            HomeEvent.Save("Entertainment one", "100", Currency.TRY, null, SubscriptionCategory.ENTERTAINMENT)
        )
        settle()
        viewModel.onEvent(HomeEvent.SelectCategoryFilter(SubscriptionCategory.ENTERTAINMENT))
        viewModel.onEvent(
            HomeEvent.Save("Health one", "50", Currency.TRY, null, SubscriptionCategory.HEALTH)
        )
        settle()

        // On screen: only the filtered category. In the table: everything.
        assertEquals(Money(10_000), viewModel.uiState.value.total)
        assertEquals(Money(15_000), snapshots.rows.single().total)
    }

    // --- failures -------------------------------------------------------------------------------

    @Test
    fun record_theWriteFails_keepsWatchingInsteadOfDying() = runTest {
        startRecording()
        snapshots.failOnUpsert = IllegalStateException("disk full")

        subscriptions.insert(subscription(price = 10_000))
        settle()
        // The row still holds the zero this month opened on: the change never landed.
        assertEquals(Money(0), snapshots.rows.single().total)

        snapshots.failOnUpsert = null
        subscriptions.insert(subscription(price = 5_000))
        settle()

        // The collector survived the failure, so the next change is still recorded.
        assertEquals(Money(15_000), snapshots.rows.single().total)
    }

    /**
     * Starts the recorder beside the test and lets it catch up.
     *
     * See [settle] for why advanceUntilIdle is not enough on its own.
     */
    private fun TestScope.startRecording() {
        backgroundScope.launch { recorder.record() }
        settle()
    }

    /**
     * Runs whatever the last change set in motion.
     *
     * runCurrent, not advanceUntilIdle: since coroutines-test 1.8 advanceUntilIdle advances until
     * the *foreground* work is idle, and a backgroundScope collector is not foreground - with no
     * foreground task pending it returns without dispatching the recorder at all.
     */
    private fun TestScope.settle() {
        testScheduler.runCurrent()
        testScheduler.advanceUntilIdle()
        testScheduler.runCurrent()
    }

    private var nextCreatedAt = 1L

    private fun subscription(
        name: String = "Test",
        price: Long,
        category: SubscriptionCategory = SubscriptionCategory.OTHER
    ) = Subscription(
        id = 0,
        name = name,
        price = Money(price),
        currency = Currency.TRY,
        billingPeriod = BillingPeriod.MONTHLY,
        nextPaymentDate = null,
        category = category,
        iconKey = null,
        createdAt = nextCreatedAt++
    )
}
