package com.elinacn.subtrack.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.domain.repository.SubscriptionRepository
import com.elinacn.subtrack.domain.usecase.PriceResult
import com.elinacn.subtrack.domain.usecase.SubscriptionInput
import com.elinacn.subtrack.ui.common.UiText
import com.elinacn.subtrack.ui.common.asUiText
import com.elinacn.subtrack.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Loads one subscription, takes the edited form back, and writes it.
 *
 * **Read once, not observed.** The row is fetched with a single `getById` rather than a Flow. A
 * Flow would re-emit the moment this screen's own save landed, and there is nothing useful it
 * could do with that: the form already holds what the user typed, and overwriting it mid-edit is
 * the one thing that must not happen. The home screen keeps watching the table; this screen does
 * not need to.
 *
 * The rules it validates with are the ones the add sheet uses - [SubscriptionInput] - so the two
 * forms cannot disagree about what a price is.
 */
@HiltViewModel
class EditSubscriptionViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * Nullable rather than a crash.
     *
     * The graph declares this argument as `NavType.LongType`, so it arrives typed and it is always
     * there in practice. Reading it as nullable anyway costs one `?:` and removes the only way
     * this screen could take the app down - `!!` is banned, and `checkNotNull` would just throw
     * with better wording (CLAUDE.md §4). A missing id is treated as a missing subscription, which
     * is a state the screen already has to draw.
     */
    private val subscriptionId: Long? = savedStateHandle[Destination.EDIT_SUBSCRIPTION_ARG]

    private val state = MutableStateFlow(EditSubscriptionUiState())

    val uiState: StateFlow<EditSubscriptionUiState> = state.asStateFlow()

    init {
        load()
    }

    /** Single entry point for everything the screen can ask for. */
    fun onEvent(event: EditSubscriptionEvent) {
        when (event) {
            is EditSubscriptionEvent.Save -> save(
                event.name,
                event.rawPrice,
                event.currency,
                event.nextPaymentDate,
                event.category,
                event.billingPeriod
            )

            EditSubscriptionEvent.ClearNameError -> state.update { it.copy(nameError = null) }

            EditSubscriptionEvent.ClearPriceError -> state.update { it.copy(priceError = null) }

            EditSubscriptionEvent.ClearDateError -> state.update { it.copy(dateError = null) }

            EditSubscriptionEvent.DismissError -> state.update { it.copy(errorMessage = null) }
        }
    }

    /**
     * A row that is not there is a **state**, not a failure.
     *
     * It is reachable without anything going wrong: a subscription can be swiped away while its
     * edit screen is opening, and a back stack entry outlives the row it names. Bouncing straight
     * back would look like the tap did nothing and invite a second tap; a sentence says what
     * happened, and leaving is then the user's move.
     */
    private fun load() {
        viewModelScope.launch {
            try {
                val id = subscriptionId
                val loaded = if (id == null) null else repository.getById(id)
                state.update {
                    it.copy(
                        subscription = loaded,
                        isMissing = loaded == null,
                        isLoading = false
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                // A read that failed is not a row that is gone, so it says so rather than
                // claiming the subscription no longer exists.
                state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = failure.asMessage(R.string.error_load_failed)
                    )
                }
            }
        }
    }

    /**
     * Validates first and only writes if everything checks out. The screen stays put on a bad
     * entry - leaving would throw away what the user typed along with the explanation.
     *
     * The stored row is **copied**, not rebuilt: the id, the icon key and `createdAt` are carried
     * over untouched. `createdAt` is what the list is ordered by, so an edited subscription stays
     * where the user last saw it instead of jumping to the top.
     */
    private fun save(
        name: String,
        rawPrice: String,
        currency: Currency,
        nextPaymentDate: LocalDate?,
        category: SubscriptionCategory,
        billingPeriod: BillingPeriod
    ) {
        val current = state.value.subscription ?: return

        val nameError = SubscriptionInput.validateName(name)?.asUiText()
        val priceResult = SubscriptionInput.parsePrice(rawPrice)
        val dateError = SubscriptionInput.validateDate(nextPaymentDate, LocalDate.now(clock))
            ?.asUiText()

        if (nameError != null || priceResult is PriceResult.Invalid || dateError != null) {
            state.update {
                it.copy(
                    nameError = nameError,
                    priceError = (priceResult as? PriceResult.Invalid)?.problem?.asUiText(),
                    dateError = dateError
                )
            }
            return
        }

        val price = (priceResult as PriceResult.Valid).money
        viewModelScope.launch {
            try {
                repository.update(
                    current.copy(
                        name = SubscriptionInput.trimName(name),
                        price = price,
                        currency = currency,
                        billingPeriod = billingPeriod,
                        nextPaymentDate = nextPaymentDate,
                        category = category
                    )
                )
                state.update { it.copy(isSaved = true) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                // Never swallowed: the screen stays open and the reason reaches it.
                state.update { it.copy(errorMessage = failure.asMessage(R.string.error_save_failed)) }
            }
        }
    }

    /** Falls back to a generic line when the exception has nothing readable to say. */
    private fun Exception.asMessage(fallback: Int): UiText =
        message?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) } ?: UiText.Resource(fallback)
}
