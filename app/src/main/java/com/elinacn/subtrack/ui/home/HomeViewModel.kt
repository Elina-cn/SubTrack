package com.elinacn.subtrack.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.domain.repository.ReminderStateRepository
import com.elinacn.subtrack.domain.repository.SettingsRepository
import com.elinacn.subtrack.domain.repository.SubscriptionRepository
import com.elinacn.subtrack.domain.usecase.CurrencyConverter
import com.elinacn.subtrack.domain.usecase.NextPaymentDate
import com.elinacn.subtrack.domain.usecase.PaymentCountdown
import com.elinacn.subtrack.domain.usecase.PriceResult
import com.elinacn.subtrack.domain.usecase.SubscriptionInput
import com.elinacn.subtrack.reminder.ReminderNotificationStatus
import com.elinacn.subtrack.ui.common.UiText
import com.elinacn.subtrack.ui.common.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Holds the home screen's state and turns its events into repository calls. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderState: ReminderStateRepository,
    private val notificationStatus: ReminderNotificationStatus,
    private val clock: Clock
) : ViewModel() {

    /** Everything that is not stored: sheet visibility, validation errors, the pending undo. */
    private val screenState = MutableStateFlow(HomeScreenState(today = LocalDate.now(clock)))

    /**
     * The single source of truth for the screen.
     *
     * combine merges the stored list, the main currency and the rates with the state above.
     *
     * **Eagerly, not WhileSubscribed: it keeps running while another screen is on top.** Home is
     * the start destination and sits under every other screen, and the settings those screens edit
     * are inputs here. Stopped after five seconds, the flow kept its last total, and a user coming
     * back from changing a rate was shown the old total until the restarted flow caught up - phase
     * 16u measured three frames of it on the way back (ARCHITECTURE section 29). Running, it has
     * already recomputed by the time the screen returns. The cost is a database observer and two
     * preference reads staying open while this ViewModel lives.
     *
     * The restart also used to be what moved "today" on; [HomeEvent.ScreenStarted] does that now.
     */
    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeAll(),
        settingsRepository.observeMainCurrency(),
        settingsRepository.observeRates(),
        screenState
    ) { subscriptions, mainCurrency, rates, screen ->
        // Built per emission rather than held as a field: the table is now editable, and a
        // converter that outlived it would keep totalling at yesterday's rates.
        val converter = CurrencyConverter(rates)
        val today = screen.today
        // Filtered here rather than in the DAO: four categories over a list this size is not a
        // query, and a second query would have to be kept in step with the one the screen already
        // observes. The composable gets a list it only draws (ARCHITECTURE section 3).
        val visible = screen.categoryFilter
            ?.let { filter -> subscriptions.filter { it.category == filter } }
            ?: subscriptions
        HomeUiState(
            subscriptions = visible,
            categoryFilter = screen.categoryFilter,
            hasAnySubscriptions = subscriptions.isNotEmpty(),
            // Computed here rather than in the composable: it needs today, which is
            // state, and ARCHITECTURE section 3 keeps calculation out of composables.
            //
            // The stored date is an anchor and stays one - nothing is written back. What the card
            // counts towards is where that anchor has reached by today, which for a date that has
            // passed is a later day on the same cycle (ARCHITECTURE section 17).
            countdowns = visible.mapNotNull { subscription ->
                subscription.nextPaymentDate?.let { anchor ->
                    val due = NextPaymentDate.onOrAfter(today, anchor, subscription.billingPeriod)
                    subscription.id to PaymentCountdown.between(today, due)
                }
            }.toMap(),
            // The total follows the filter: the number under the heading has to cover the rows
            // the user can see, or it is answering a question nobody asked. It follows the chosen
            // span too - one figure, not a second indicator beside it.
            total = converter.totalIn(visible, mainCurrency, screen.totalPeriod),
            totalPeriod = screen.totalPeriod,
            baseCurrency = mainCurrency,
            isTotalConverted = visible.any { it.currency != mainCurrency },
            isLoading = false,
            isAddSheetOpen = screen.isAddSheetOpen,
            nameError = screen.nameError,
            priceError = screen.priceError,
            dateError = screen.dateError,
            errorMessage = screen.errorMessage,
            pendingUndo = screen.pendingUndo,
            shouldRequestNotificationPermission = screen.shouldRequestNotificationPermission
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState()
    )

    /** Single entry point for everything the screen can ask for. */
    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.OpenAddSheet -> screenState.update { it.clearedErrors(open = true) }

            HomeEvent.DismissAddSheet -> screenState.update { it.clearedErrors(open = false) }

            is HomeEvent.Save -> save(
                event.name,
                event.rawPrice,
                event.currency,
                event.nextPaymentDate,
                event.category,
                event.billingPeriod
            )

            is HomeEvent.SelectCategoryFilter ->
                screenState.update { it.copy(categoryFilter = event.category) }

            is HomeEvent.SelectTotalPeriod ->
                screenState.update { it.copy(totalPeriod = event.period) }

            HomeEvent.ClearNameError -> screenState.update { it.copy(nameError = null) }

            HomeEvent.ClearPriceError -> screenState.update { it.copy(priceError = null) }

            HomeEvent.ClearDateError -> screenState.update { it.copy(dateError = null) }

            is HomeEvent.Delete -> delete(event.id)

            HomeEvent.UndoDelete -> undoDelete()

            HomeEvent.DismissUndo -> screenState.update { it.copy(pendingUndo = null) }

            HomeEvent.DismissError -> screenState.update { it.copy(errorMessage = null) }

            HomeEvent.NotificationRequestHandled ->
                screenState.update { it.copy(shouldRequestNotificationPermission = false) }

            // Unchanged on the same day, so the state only moves when the date really has.
            HomeEvent.ScreenStarted -> screenState.update { it.copy(today = LocalDate.now(clock)) }
        }
    }

    /**
     * Validates first and only writes if everything checks out. The sheet stays open on a bad
     * entry - closing it would throw away what the user typed along with the explanation.
     */
    private fun save(
        name: String,
        rawPrice: String,
        currency: Currency,
        nextPaymentDate: LocalDate?,
        category: SubscriptionCategory,
        billingPeriod: BillingPeriod
    ) {
        val trimmedName = SubscriptionInput.trimName(name)
        val nameError = SubscriptionInput.validateName(name)?.asUiText()
        val priceResult = SubscriptionInput.parsePrice(rawPrice)
        val dateError = SubscriptionInput.validateDate(nextPaymentDate, LocalDate.now(clock))
            ?.asUiText()

        if (nameError != null || priceResult is PriceResult.Invalid || dateError != null) {
            screenState.update {
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
                repository.insert(
                    Subscription(
                        id = 0,
                        name = trimmedName,
                        price = price,
                        currency = currency,
                        billingPeriod = billingPeriod,
                        nextPaymentDate = nextPaymentDate,
                        category = category,
                        iconKey = null,
                        createdAt = System.currentTimeMillis()
                    )
                )
                screenState.update { it.clearedErrors(open = false) }
                armNotificationRequest(nextPaymentDate)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                // Never swallowed: the sheet stays open and the reason reaches the screen.
                screenState.update { it.copy(errorMessage = failure.asMessage(R.string.error_save_failed)) }
            }
        }
    }

    /** Reads the row before removing it, so undo has something to put back. */
    private fun delete(id: Long) {
        viewModelScope.launch {
            try {
                val removed = repository.getById(id) ?: return@launch
                repository.deleteById(id)
                screenState.update { it.copy(pendingUndo = removed) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                screenState.update { it.copy(errorMessage = failure.asMessage(R.string.error_delete_failed)) }
            }
        }
    }

    /**
     * Puts the row back with its original id, so it lands in the same place in the list rather
     * than jumping to the end.
     */
    private fun undoDelete() {
        val restored = screenState.value.pendingUndo ?: return
        screenState.update { it.copy(pendingUndo = null) }
        viewModelScope.launch {
            try {
                repository.insert(restored)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                screenState.update { it.copy(errorMessage = failure.asMessage(R.string.error_save_failed)) }
            }
        }
    }

    /** Falls back to a generic line when the exception has nothing readable to say. */
    private fun Exception.asMessage(fallback: Int): UiText =
        message?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) } ?: UiText.Resource(fallback)

    /**
     * Asks for the notification permission the moment reminders first become worth having: the
     * first subscription with a date on it.
     *
     * Every condition is a reason not to ask. Reminders already visible - nothing to gain. No
     * runtime permission on this build, or the permission already held while reminders are still
     * off - asking would do nothing, only the system settings can help (ARCHITECTURE §18). Asked
     * before - the system closes the door after a second refusal, so the one chance is spent.
     * Not the first dated subscription - the moment has passed.
     */
    private suspend fun armNotificationRequest(savedDate: LocalDate?) {
        if (savedDate == null) return
        if (notificationStatus.areRemindersVisible()) return
        if (!notificationStatus.isRuntimePermissionRequired()) return
        if (notificationStatus.isPermissionGranted()) return
        if (reminderState.wasPermissionRequested()) return

        // Read back rather than counted from the state above: the stored list is the truth, and
        // the flow behind uiState has not necessarily emitted the new row yet. No new query - the
        // same observeAll the screen already lives on, taken once.
        val datedCount = repository.observeAll().first().count { it.nextPaymentDate != null }
        if (datedCount != FIRST_DATED_SUBSCRIPTION) return

        // Written as the request is armed, not when it returns: the same record the settings row
        // reads, so a refusal here leaves that row saying "turn it on in system settings" instead
        // of offering an ask that would never appear.
        reminderState.setPermissionRequested()
        screenState.update { it.copy(shouldRequestNotificationPermission = true) }
    }

    private companion object {
        /** The list holds exactly one dated subscription only right after the first one lands. */
        const val FIRST_DATED_SUBSCRIPTION = 1
    }
}
