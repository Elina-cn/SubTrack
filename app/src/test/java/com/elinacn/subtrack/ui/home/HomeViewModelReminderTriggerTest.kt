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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * When saving a subscription asks for the notification permission, and when it stays quiet.
 *
 * The moment is deliberate: reminders mean nothing until something has a date, and the system
 * closes the door after a second refusal, so the one chance is spent where the feature has just
 * become useful. Every case below is a reason not to spend it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelReminderTriggerTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneOffset.UTC)
    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    private lateinit var repository: FakeSubscriptionRepository
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var reminderState: FakeReminderStateRepository
    private lateinit var notificationStatus: FakeReminderNotificationStatus

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSubscriptionRepository()
        settingsRepository = FakeSettingsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun save_firstDatedSubscriptionAndPermissionAskable_armsTheRequest() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        save(viewModel, date = today.plusDays(3))

        assertTrue(viewModel.uiState.value.shouldRequestNotificationPermission)
        // Recorded as the request is armed, so the settings row knows the user has been asked.
        assertEquals(listOf(true), reminderState.permissionRequestWrites)
    }

    @Test
    fun save_subscriptionWithoutADate_leavesTheRequestAlone() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        save(viewModel, date = null)

        // Nothing to remind about, so nothing to ask for.
        assertFalse(viewModel.uiState.value.shouldRequestNotificationPermission)
        assertTrue(reminderState.permissionRequestWrites.isEmpty())
    }

    @Test
    fun save_secondDatedSubscription_leavesTheRequestAlone() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        repository.setSubscriptions(listOf(stored(id = 1, date = today.plusDays(10))))

        save(viewModel, date = today.plusDays(3))

        // The moment has passed; only the first dated subscription earns the one ask.
        assertFalse(viewModel.uiState.value.shouldRequestNotificationPermission)
        assertTrue(reminderState.permissionRequestWrites.isEmpty())
    }

    @Test
    fun save_permissionAlreadyRequestedOnce_leavesTheRequestAlone() = runTest {
        val viewModel = viewModel(permissionRequested = true)
        collect(viewModel)

        save(viewModel, date = today.plusDays(3))

        // Asking again after a refusal is how the system closes the door for good.
        assertFalse(viewModel.uiState.value.shouldRequestNotificationPermission)
    }

    @Test
    fun save_remindersAlreadyVisible_leavesTheRequestAlone() = runTest {
        val viewModel = viewModel(remindersVisible = true, permissionGranted = true)
        collect(viewModel)

        save(viewModel, date = today.plusDays(3))

        assertFalse(viewModel.uiState.value.shouldRequestNotificationPermission)
        assertTrue(reminderState.permissionRequestWrites.isEmpty())
    }

    @Test
    fun save_permissionHeldButRemindersOff_leavesTheRequestAlone() = runTest {
        // Settings-only: the permission is granted and reminders are still invisible, so the app
        // switch or the channel is off and no request could change that.
        val viewModel = viewModel(remindersVisible = false, permissionGranted = true)
        collect(viewModel)

        save(viewModel, date = today.plusDays(3))

        assertFalse(viewModel.uiState.value.shouldRequestNotificationPermission)
    }

    @Test
    fun save_belowApi33_leavesTheRequestAlone() = runTest {
        // Settings-only for a different reason: there is no runtime permission to request.
        val viewModel = viewModel(runtimePermissionRequired = false)
        collect(viewModel)

        save(viewModel, date = today.plusDays(3))

        assertFalse(viewModel.uiState.value.shouldRequestNotificationPermission)
        assertTrue(reminderState.permissionRequestWrites.isEmpty())
    }

    @Test
    fun notificationRequestHandled_clearsTheTrigger() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        save(viewModel, date = today.plusDays(3))

        viewModel.onEvent(HomeEvent.NotificationRequestHandled)
        advanceUntilIdle()

        // Otherwise a rotation would put the system dialog up a second time.
        assertFalse(viewModel.uiState.value.shouldRequestNotificationPermission)
    }

    @Test
    fun save_failedWrite_leavesTheRequestAlone() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        // A date far past the ten-year ceiling never reaches storage, so nothing was stored to
        // make reminders worth asking about.
        save(viewModel, date = today.plusYears(20))

        assertFalse(viewModel.uiState.value.shouldRequestNotificationPermission)
        assertTrue(reminderState.permissionRequestWrites.isEmpty())
    }

    private fun viewModel(
        remindersVisible: Boolean = false,
        runtimePermissionRequired: Boolean = true,
        permissionGranted: Boolean = false,
        permissionRequested: Boolean = false
    ): HomeViewModel {
        reminderState = FakeReminderStateRepository(permissionRequested = permissionRequested)
        notificationStatus = FakeReminderNotificationStatus(
            remindersVisible = remindersVisible,
            runtimePermissionRequired = runtimePermissionRequired,
            permissionGranted = permissionGranted
        )
        return HomeViewModel(repository, settingsRepository, reminderState, notificationStatus, clock)
    }

    private fun TestScope.save(viewModel: HomeViewModel, date: LocalDate?) {
        viewModel.onEvent(HomeEvent.Save("Netflix", "159.99", Currency.TRY, date))
        advanceUntilIdle()
    }

    /** WhileSubscribed keeps the state cold until something collects it. */
    private fun TestScope.collect(viewModel: HomeViewModel) {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
    }

    private fun stored(id: Long, date: LocalDate?) = Subscription(
        id = id,
        name = "Existing",
        price = Money(1000),
        currency = Currency.TRY,
        billingPeriod = BillingPeriod.MONTHLY,
        nextPaymentDate = date,
        category = SubscriptionCategory.OTHER,
        iconKey = null,
        createdAt = 0
    )
}
