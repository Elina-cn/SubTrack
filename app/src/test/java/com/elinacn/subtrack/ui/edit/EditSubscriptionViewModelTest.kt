package com.elinacn.subtrack.ui.edit

import androidx.lifecycle.SavedStateHandle
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.fake.FakeSubscriptionRepository
import com.elinacn.subtrack.ui.common.UiText
import com.elinacn.subtrack.ui.navigation.Destination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * What the edit screen is told, read from the ViewModel's only public surface.
 *
 * Nothing is mocked: the fake actually holds rows, so "was it written" is answered by looking at
 * what is stored rather than at which method was called (ARCHITECTURE §11).
 *
 * The rules being enforced are the shared ones - [SubscriptionInput][com.elinacn.subtrack.domain
 * .usecase.SubscriptionInput] - so what is checked here is that this screen applies them and
 * refuses to write, not what each message says.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditSubscriptionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** Mid-September 2026, so "ten years ahead" is a fixed date rather than whenever this runs. */
    private val clock = Clock.fixed(Instant.parse("2026-09-15T09:00:00Z"), ZoneOffset.UTC)

    private lateinit var repository: FakeSubscriptionRepository

    private val stored = Subscription(
        id = 7,
        name = "Netflix",
        price = Money.of(159, 99),
        currency = Currency.TRY,
        billingPeriod = BillingPeriod.MONTHLY,
        nextPaymentDate = LocalDate.of(2026, 9, 9),
        category = SubscriptionCategory.ENTERTAINMENT,
        iconKey = "netflix",
        createdAt = 1_000L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSubscriptionRepository()
        repository.setSubscriptions(listOf(stored))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- coming in -------------------------------------------------------------------------------

    @Test
    fun uiState_loadsTheSubscriptionTheIdNames() = runTest {
        val viewModel = viewModel(id = 7)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(stored, state.subscription)
        assertTrue(!state.isLoading)
        assertTrue(!state.isMissing)
    }

    @Test
    fun uiState_theStoredDateIsTheAnchor_notWhereItHasGotTo() = runTest {
        // The card counts towards 9 October; the form has to offer the 9 September the user chose,
        // or saving would write back a date nobody entered (ARCHITECTURE §17).
        val viewModel = viewModel(id = 7)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 9, 9), viewModel.uiState.value.subscription?.nextPaymentDate)
    }

    @Test
    fun uiState_anIdThatNamesNothing_isMissingRatherThanACrash() = runTest {
        val viewModel = viewModel(id = 404)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isMissing)
        assertNull(state.subscription)
        assertTrue(!state.isLoading)
    }

    @Test
    fun uiState_noIdAtAll_isAlsoMissing() = runTest {
        val viewModel = EditSubscriptionViewModel(repository, clock, SavedStateHandle())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isMissing)
    }

    // --- saving ----------------------------------------------------------------------------------

    @Test
    fun save_writesEveryEditedField() = runTest {
        val viewModel = loaded()

        viewModel.onEvent(
            EditSubscriptionEvent.Save(
                name = "Netflix Premium",
                rawPrice = "249,90",
                currency = Currency.USD,
                nextPaymentDate = LocalDate.of(2026, 12, 1),
                category = SubscriptionCategory.PRODUCTIVITY,
                billingPeriod = BillingPeriod.YEARLY
            )
        )
        advanceUntilIdle()

        val written = repository.getById(7)
        assertEquals("Netflix Premium", written?.name)
        assertEquals(Money(24_990), written?.price)
        assertEquals(Currency.USD, written?.currency)
        assertEquals(LocalDate.of(2026, 12, 1), written?.nextPaymentDate)
        assertEquals(SubscriptionCategory.PRODUCTIVITY, written?.category)
        assertEquals(BillingPeriod.YEARLY, written?.billingPeriod)
    }

    @Test
    fun save_keepsWhatTheFormNeverAsksAbout() = runTest {
        val viewModel = loaded()

        viewModel.onEvent(save(name = "Renamed"))
        advanceUntilIdle()

        val written = repository.getById(7)
        assertEquals(7L, written?.id)
        assertEquals("netflix", written?.iconKey)
        // createdAt is what the list is ordered by: an edited row must not jump to the top.
        assertEquals(1_000L, written?.createdAt)
    }

    @Test
    fun save_trimsTheName() = runTest {
        val viewModel = loaded()

        viewModel.onEvent(save(name = "  Spotify  "))
        advanceUntilIdle()

        assertEquals("Spotify", repository.getById(7)?.name)
    }

    @Test
    fun save_clearingTheDate_isStored() = runTest {
        val viewModel = loaded()

        viewModel.onEvent(save(nextPaymentDate = null))
        advanceUntilIdle()

        assertNull(repository.getById(7)?.nextPaymentDate)
    }

    @Test
    fun save_marksTheScreenDone_soItCanLeave() = runTest {
        val viewModel = loaded()

        viewModel.onEvent(save())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
    }

    // --- refusing to save ------------------------------------------------------------------------

    @Test
    fun save_anEmptyName_isRefusedAndNothingIsWritten() = runTest {
        val viewModel = loaded()

        viewModel.onEvent(save(name = "   "))
        advanceUntilIdle()

        assertEquals(UiText.Resource(R.string.error_name_empty), viewModel.uiState.value.nameError)
        assertTrue("nothing may reach the table", repository.updated.isEmpty())
        assertTrue(!viewModel.uiState.value.isSaved)
    }

    @Test
    fun save_aPriceThatIsNotOne_isRefusedAndNothingIsWritten() = runTest {
        val rejected = listOf("", "abc", "0", "-5", "1.234", "1000000.01")

        rejected.forEach { typed ->
            repository = FakeSubscriptionRepository().also { it.setSubscriptions(listOf(stored)) }
            val viewModel = loaded()

            viewModel.onEvent(save(rawPrice = typed))
            advanceUntilIdle()

            assertTrue("$typed should have been refused", viewModel.uiState.value.priceError != null)
            assertTrue("$typed reached the table", repository.updated.isEmpty())
        }
    }

    @Test
    fun save_aDateBeyondTheCeiling_isRefusedAndNothingIsWritten() = runTest {
        val viewModel = loaded()

        viewModel.onEvent(save(nextPaymentDate = LocalDate.of(2036, 9, 16)))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.dateError != null)
        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun save_theRowIsNotLoadedYet_writesNothing() = runTest {
        val viewModel = viewModel(id = 404)
        advanceUntilIdle()

        viewModel.onEvent(save())
        advanceUntilIdle()

        assertTrue(repository.updated.isEmpty())
        assertTrue(!viewModel.uiState.value.isSaved)
    }

    @Test
    fun save_theWriteFails_saysSoAndStaysPut() = runTest {
        val viewModel = loaded()
        repository.failOnWrite = IllegalStateException("disk full")

        viewModel.onEvent(save())
        advanceUntilIdle()

        assertEquals(UiText.Raw("disk full"), viewModel.uiState.value.errorMessage)
        assertTrue("a failed save must not send the screen away", !viewModel.uiState.value.isSaved)
    }

    // --- leaving without saving ------------------------------------------------------------------

    @Test
    fun anythingOtherThanSave_leavesTheTableAlone() = runTest {
        val viewModel = loaded()

        // What the screen sends while the user types, and then they press back: no Save event is
        // ever raised, so the stored row is untouched.
        viewModel.onEvent(EditSubscriptionEvent.ClearNameError)
        viewModel.onEvent(EditSubscriptionEvent.ClearPriceError)
        viewModel.onEvent(EditSubscriptionEvent.ClearDateError)
        advanceUntilIdle()

        assertTrue(repository.updated.isEmpty())
        assertEquals(stored, repository.getById(7))
    }

    @Test
    fun clearingAnError_dropsItWithoutTouchingTheOthers() = runTest {
        val viewModel = loaded()
        viewModel.onEvent(save(name = "  ", rawPrice = "abc"))
        advanceUntilIdle()

        viewModel.onEvent(EditSubscriptionEvent.ClearNameError)

        assertNull(viewModel.uiState.value.nameError)
        assertTrue(viewModel.uiState.value.priceError != null)
    }

    @Test
    fun dismissingAFailure_dropsTheMessage() = runTest {
        val viewModel = loaded()
        repository.failOnWrite = IllegalStateException("disk full")
        viewModel.onEvent(save())
        advanceUntilIdle()

        viewModel.onEvent(EditSubscriptionEvent.DismissError)

        assertNull(viewModel.uiState.value.errorMessage)
    }

    private fun viewModel(id: Long) = EditSubscriptionViewModel(
        repository,
        clock,
        SavedStateHandle(mapOf(Destination.EDIT_SUBSCRIPTION_ARG to id))
    )

    /** A ViewModel that has already read its row. */
    private fun TestScope.loaded(): EditSubscriptionViewModel {
        val viewModel = viewModel(id = 7)
        advanceUntilIdle()
        return viewModel
    }

    /** The form as it stands, with one field at a time bent out of shape. */
    private fun save(
        name: String = "Netflix",
        rawPrice: String = "159.99",
        currency: Currency = Currency.TRY,
        nextPaymentDate: LocalDate? = LocalDate.of(2026, 9, 9),
        category: SubscriptionCategory = SubscriptionCategory.ENTERTAINMENT,
        billingPeriod: BillingPeriod = BillingPeriod.MONTHLY
    ) = EditSubscriptionEvent.Save(
        name, rawPrice, currency, nextPaymentDate, category, billingPeriod
    )
}
