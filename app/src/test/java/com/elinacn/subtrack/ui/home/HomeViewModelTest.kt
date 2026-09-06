package com.elinacn.subtrack.ui.home

import app.cash.turbine.test
import com.elinacn.subtrack.R
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
import com.elinacn.subtrack.ui.common.UiText
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
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSubscriptionRepository
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var viewModel: HomeViewModel

    /** A fixed clock, so "today" is a date the assertions can name rather than whenever CI ran. */
    private val clock = Clock.fixed(
        LocalDate.of(2026, 3, 15).atStartOfDay(ZoneId.of("UTC")).toInstant(),
        ZoneId.of("UTC")
    )
    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    @Before
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main, which does not exist off-device.
        Dispatchers.setMain(dispatcher)
        repository = FakeSubscriptionRepository()
        settingsRepository = FakeSettingsRepository()
        // Reminders already visible, so the permission trigger stays out of these cases.
        viewModel = HomeViewModel(
            repository,
            settingsRepository,
            FakeReminderStateRepository(),
            FakeReminderNotificationStatus(remindersVisible = true),
            clock
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- state ----------------------------------------------------------------------------

    @Test
    fun uiState_beforeRepositoryEmits_isLoading() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()

            assertTrue(initial.isLoading)
            assertTrue(initial.subscriptions.isEmpty())
            assertEquals(Money.ZERO, initial.total)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_repositoryEmits_exposesSubscriptionsAndStopsLoading() = runTest {
        repository.setSubscriptions(listOf(subscription(id = 1, name = "Netflix", cents = 15999)))

        viewModel.uiState.test {
            awaitItem() // initial, still loading
            val loaded = awaitItem()

            assertEquals(false, loaded.isLoading)
            assertEquals(listOf("Netflix"), loaded.subscriptions.map { it.name })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_severalSubscriptions_sumsMonthlyTotalToTheKurus() = runTest {
        repository.setSubscriptions(
            listOf(
                subscription(id = 1, cents = 15999), // 159,99
                subscription(id = 2, cents = 5990)   // 59,90
            )
        )
        collectState()

        // 219,89 exactly - the sum that a Double would not be trusted to produce.
        assertEquals(Money(21989), viewModel.uiState.value.total)
    }

    @Test
    fun uiState_mixedCurrencies_normalizesTheTotalAndFlagsTheConversion() = runTest {
        repository.setSubscriptions(
            listOf(
                subscription(id = 1, cents = 15999), // 159,99 TRY
                subscription(id = 2, cents = 1099, currency = Currency.USD), // 470,92 TRY
                subscription(id = 3, cents = 2499, currency = Currency.EUR) // 1154,54 TRY
            )
        )
        collectState()

        assertEquals(Money(178545), viewModel.uiState.value.total)
        assertEquals(Currency.TRY, viewModel.uiState.value.baseCurrency)
        assertTrue(viewModel.uiState.value.isTotalConverted)
    }

    @Test
    fun uiState_onlyBaseCurrency_doesNotClaimAConversionHappened() = runTest {
        repository.setSubscriptions(
            listOf(
                subscription(id = 1, cents = 15999),
                subscription(id = 2, cents = 5990)
            )
        )
        collectState()

        assertEquals(false, viewModel.uiState.value.isTotalConverted)
    }

    @Test
    fun uiState_mainCurrencyChanges_recalculatesTheTotalInTheNewCurrency() = runTest {
        repository.setSubscriptions(
            listOf(
                subscription(id = 1, cents = 15999), // 159,99 TRY
                subscription(id = 2, cents = 1099, currency = Currency.USD) // 10,99 USD
            )
        )
        collectState()

        // In TRY: 15999 + round(1099 * 428500 / 10000) = 15999 + 47092.
        assertEquals(Money(63091), viewModel.uiState.value.total)
        assertEquals(Currency.TRY, viewModel.uiState.value.baseCurrency)

        settingsRepository.setMainCurrency(Currency.USD)
        advanceUntilIdle()

        // In USD: round(15999 * 10000 / 428500) + 1099 = 373 + 1099.
        assertEquals(Money(1472), viewModel.uiState.value.total)
        assertEquals(Currency.USD, viewModel.uiState.value.baseCurrency)
        assertTrue(viewModel.uiState.value.isTotalConverted)
    }

    @Test
    fun uiState_mainCurrencyMatchesEverySubscription_doesNotClaimAConversionHappened() = runTest {
        repository.setSubscriptions(
            listOf(subscription(id = 1, cents = 1099, currency = Currency.USD))
        )
        collectState()
        assertTrue(viewModel.uiState.value.isTotalConverted)

        settingsRepository.setMainCurrency(Currency.USD)
        advanceUntilIdle()

        // Converted to itself, so the amount comes back untouched and the note goes away.
        assertEquals(Money(1099), viewModel.uiState.value.total)
        assertEquals(false, viewModel.uiState.value.isTotalConverted)
    }

    @Test
    fun uiState_editedRate_recalculatesTheTotal() = runTest {
        repository.setSubscriptions(
            listOf(subscription(id = 1, cents = 1000, currency = Currency.USD)) // 10,00 USD
        )
        collectState()

        // At the shipped 42,8500 that is 428,50 TRY.
        assertEquals(Money(42_850), viewModel.uiState.value.total)

        settingsRepository.setRate(Currency.USD, 500_000L) // 50,0000
        advanceUntilIdle()

        assertEquals(Money(50_000), viewModel.uiState.value.total)
    }

    @Test
    fun uiState_ratesReset_goesBackToTheShippedTable() = runTest {
        repository.setSubscriptions(
            listOf(subscription(id = 1, cents = 1000, currency = Currency.USD))
        )
        collectState()
        settingsRepository.setRate(Currency.USD, 500_000L)
        advanceUntilIdle()

        settingsRepository.resetRates()
        advanceUntilIdle()

        assertEquals(Money(42_850), viewModel.uiState.value.total)
    }

    // --- saving ---------------------------------------------------------------------------

    @Test
    fun save_validInput_insertsAndClosesSheet() = runTest {
        collectState()
        viewModel.onEvent(HomeEvent.OpenAddSheet)

        viewModel.onEvent(HomeEvent.Save("Netflix", "159,99", Currency.TRY))
        advanceUntilIdle()

        assertEquals(1, repository.inserted.size)
        assertEquals("Netflix", repository.inserted.single().name)
        assertEquals(false, viewModel.uiState.value.isAddSheetOpen)
        assertNull(viewModel.uiState.value.priceError)
    }

    @Test
    fun save_price159_99_storesExactly15999Cents() = runTest {
        collectState()

        viewModel.onEvent(HomeEvent.Save("Netflix", "159,99", Currency.TRY))
        advanceUntilIdle()

        assertEquals(Money(15999), repository.inserted.single().price)
    }

    @Test
    fun save_foreignCurrency_storesWhatWasChosenRatherThanTheBase() = runTest {
        collectState()

        viewModel.onEvent(HomeEvent.Save("Spotify", "10,99", Currency.USD))
        advanceUntilIdle()

        assertEquals(Currency.USD, repository.inserted.single().currency)
        assertEquals(Money(1099), repository.inserted.single().price)
    }

    @Test
    fun save_commaAndDotSeparator_produceTheSameAmount() = runTest {
        collectState()

        viewModel.onEvent(HomeEvent.Save("Comma", "45,50", Currency.TRY))
        advanceUntilIdle()
        viewModel.onEvent(HomeEvent.Save("Dot", "45.50", Currency.TRY))
        advanceUntilIdle()

        assertEquals(Money(4550), repository.inserted[0].price)
        assertEquals(Money(4550), repository.inserted[1].price)
    }

    @Test
    fun save_nameIsOnlyWhitespace_reportsNameErrorAndSkipsInsert() = runTest {
        collectState()
        viewModel.onEvent(HomeEvent.OpenAddSheet)

        viewModel.onEvent(HomeEvent.Save("   ", "159,99", Currency.TRY))
        advanceUntilIdle()

        assertEquals(errorRes(R.string.error_name_empty), viewModel.uiState.value.nameError)
        assertTrue(repository.inserted.isEmpty())
        assertTrue(viewModel.uiState.value.isAddSheetOpen)
    }

    @Test
    fun save_emptyPrice_reportsPriceErrorAndSkipsInsert() = runTest {
        assertPriceRejected(rawPrice = "", expected = R.string.error_price_empty)
    }

    @Test
    fun save_nonNumericPrice_reportsPriceError() = runTest {
        assertPriceRejected(rawPrice = "abc", expected = R.string.error_price_invalid)
    }

    @Test
    fun save_negativePrice_reportsPriceError() = runTest {
        assertPriceRejected(rawPrice = "-50", expected = R.string.error_price_not_positive)
    }

    @Test
    fun save_zeroPrice_reportsPriceError() = runTest {
        assertPriceRejected(rawPrice = "0", expected = R.string.error_price_not_positive)
    }

    @Test
    fun save_priceAtCeiling_isAccepted() = runTest {
        collectState()

        viewModel.onEvent(HomeEvent.Save("Expensive", "1000000", Currency.TRY))
        advanceUntilIdle()

        assertEquals(Money(100_000_000), repository.inserted.single().price)
        assertNull(viewModel.uiState.value.priceError)
    }

    @Test
    fun save_priceAboveCeiling_reportsPriceError() = runTest {
        assertPriceRejected(
            rawPrice = "1000001",
            expected = R.string.error_price_too_large,
            args = listOf(1_000_000L)
        )
    }

    @Test
    fun save_priceAboveCeiling_carriesTheLimitIntoTheMessage() = runTest {
        collectState()

        viewModel.onEvent(HomeEvent.Save("Netflix", "5000000", Currency.TRY))
        advanceUntilIdle()

        // The number travels as an argument, so the string resource never has to repeat a limit
        // that could drift away from the constant.
        val error = viewModel.uiState.value.priceError as UiText.Resource
        assertEquals(listOf(1_000_000L), error.args)
    }

    @Test
    fun save_threeDecimals_reportsPriceError() = runTest {
        assertPriceRejected(rawPrice = "159,999", expected = R.string.error_price_too_many_decimals)
    }

    @Test
    fun save_trailingZeroAfterTwoDecimals_isAccepted() = runTest {
        collectState()

        viewModel.onEvent(HomeEvent.Save("Netflix", "159,990", Currency.TRY))
        advanceUntilIdle()

        assertEquals(Money(15999), repository.inserted.single().price)
    }

    @Test
    fun clearPriceError_afterRejection_removesTheMessage() = runTest {
        collectState()
        viewModel.onEvent(HomeEvent.Save("Netflix", "abc", Currency.TRY))
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.ClearPriceError)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.priceError)
    }

    // --- next payment date ----------------------------------------------------------------

    @Test
    fun save_withADate_storesIt() = runTest {
        collectState()

        viewModel.onEvent(
            HomeEvent.Save("Netflix", "159,99", Currency.TRY, today.plusDays(5))
        )
        advanceUntilIdle()

        assertEquals(today.plusDays(5), repository.inserted.single().nextPaymentDate)
        assertNull(viewModel.uiState.value.dateError)
    }

    @Test
    fun save_withoutADate_storesNullAndIsAccepted() = runTest {
        collectState()

        viewModel.onEvent(HomeEvent.Save("Netflix", "159,99", Currency.TRY, null))
        advanceUntilIdle()

        assertNull(repository.inserted.single().nextPaymentDate)
        assertEquals(false, viewModel.uiState.value.isAddSheetOpen)
    }

    @Test
    fun save_aPastDate_isAccepted() = runTest {
        collectState()

        // Someone entering a subscription they already have knows when it last renewed.
        viewModel.onEvent(
            HomeEvent.Save("Netflix", "159,99", Currency.TRY, today.minusMonths(2))
        )
        advanceUntilIdle()

        assertEquals(today.minusMonths(2), repository.inserted.single().nextPaymentDate)
    }

    @Test
    fun save_exactlyTenYearsAhead_isAccepted() = runTest {
        collectState()

        viewModel.onEvent(
            HomeEvent.Save("Netflix", "159,99", Currency.TRY, today.plusYears(10))
        )
        advanceUntilIdle()

        assertEquals(today.plusYears(10), repository.inserted.single().nextPaymentDate)
        assertNull(viewModel.uiState.value.dateError)
    }

    @Test
    fun save_aDayBeyondTenYears_isRejectedAndCarriesTheLimit() = runTest {
        collectState()
        viewModel.onEvent(HomeEvent.OpenAddSheet)

        viewModel.onEvent(
            HomeEvent.Save("Netflix", "159,99", Currency.TRY, today.plusYears(10).plusDays(1))
        )
        advanceUntilIdle()

        val error = viewModel.uiState.value.dateError as UiText.Resource
        assertEquals(R.string.error_date_too_far, error.id)
        assertEquals(listOf(10L), error.args)
        assertTrue(repository.inserted.isEmpty())
        assertTrue(viewModel.uiState.value.isAddSheetOpen)
    }

    @Test
    fun clearDateError_afterRejection_removesTheMessage() = runTest {
        collectState()
        viewModel.onEvent(
            HomeEvent.Save("Netflix", "159,99", Currency.TRY, today.plusYears(11))
        )
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.ClearDateError)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dateError)
    }

    // --- countdowns -----------------------------------------------------------------------

    @Test
    fun uiState_datedSubscriptions_carryTheirCountdown() = runTest {
        repository.setSubscriptions(
            listOf(
                subscription(id = 1, name = "Future").copy(nextPaymentDate = today.plusDays(3)),
                subscription(id = 2, name = "Today").copy(nextPaymentDate = today),
                subscription(id = 3, name = "Past").copy(nextPaymentDate = today.minusDays(4))
            )
        )
        collectState()

        val countdowns = viewModel.uiState.value.countdowns
        assertEquals(PaymentCountdown.Upcoming(days = 3), countdowns[1])
        assertEquals(PaymentCountdown.DueToday, countdowns[2])
        // Phase 12-2: the row anchored on 11 March is monthly, so by 15 March its next payment is
        // 11 April, twenty-seven days off. Before advancement this said Overdue(4).
        assertEquals(PaymentCountdown.Upcoming(days = 27), countdowns[3])
    }

    @Test
    fun uiState_subscriptionWithoutADate_hasNoCountdownEntry() = runTest {
        repository.setSubscriptions(listOf(subscription(id = 1, name = "No date")))
        collectState()

        // Absent rather than a placeholder: the card shows nothing at all for these.
        assertTrue(viewModel.uiState.value.countdowns.isEmpty())
        assertNull(viewModel.uiState.value.subscriptions.single().nextPaymentDate)
    }

    // --- deleting and undo ----------------------------------------------------------------

    @Test
    fun delete_existingId_removesItAndOffersUndo() = runTest {
        repository.setSubscriptions(listOf(subscription(id = 4, name = "Spotify")))
        collectState()

        viewModel.onEvent(HomeEvent.Delete(4))
        advanceUntilIdle()

        assertEquals(listOf(4L), repository.deletedIds)
        assertTrue(viewModel.uiState.value.subscriptions.isEmpty())
        assertEquals("Spotify", viewModel.uiState.value.pendingUndo?.name)
    }

    @Test
    fun undoDelete_afterDelete_reinsertsWithTheSameId() = runTest {
        repository.setSubscriptions(listOf(subscription(id = 4, name = "Spotify")))
        collectState()
        viewModel.onEvent(HomeEvent.Delete(4))
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.UndoDelete)
        advanceUntilIdle()

        // The original id, so the row returns to its place rather than to the end of the list.
        assertEquals(4L, repository.inserted.single().id)
        assertEquals(listOf("Spotify"), viewModel.uiState.value.subscriptions.map { it.name })
        assertNull(viewModel.uiState.value.pendingUndo)
    }

    @Test
    fun dismissUndo_afterDelete_leavesTheDeletionInPlace() = runTest {
        repository.setSubscriptions(listOf(subscription(id = 4)))
        collectState()
        viewModel.onEvent(HomeEvent.Delete(4))
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.DismissUndo)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingUndo)
        assertTrue(repository.inserted.isEmpty())
        assertTrue(viewModel.uiState.value.subscriptions.isEmpty())
    }

    // --- helpers --------------------------------------------------------------------------

    /**
     * uiState is built with WhileSubscribed, so it stays cold until something collects it.
     * Reading .value without this would only ever return the initial value.
     */
    private fun TestScope.collectState() {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
    }

    private fun TestScope.assertPriceRejected(
        rawPrice: String,
        expected: Int,
        args: List<Any> = emptyList()
    ) {
        collectState()
        viewModel.onEvent(HomeEvent.OpenAddSheet)

        viewModel.onEvent(HomeEvent.Save("Netflix", rawPrice, Currency.TRY))
        advanceUntilIdle()

        assertEquals(UiText.Resource(expected, args), viewModel.uiState.value.priceError)
        assertTrue(repository.inserted.isEmpty())
        assertTrue(viewModel.uiState.value.isAddSheetOpen)
    }

    private fun errorRes(id: Int): UiText = UiText.Resource(id)

    private fun subscription(
        id: Long = 1,
        name: String = "Test",
        cents: Long = 1000,
        currency: Currency = Currency.TRY
    ) = Subscription(
        id = id,
        name = name,
        price = Money(cents),
        currency = currency,
        billingPeriod = BillingPeriod.MONTHLY,
        nextPaymentDate = null,
        category = SubscriptionCategory.OTHER,
        iconKey = null,
        createdAt = 0
    )
}
