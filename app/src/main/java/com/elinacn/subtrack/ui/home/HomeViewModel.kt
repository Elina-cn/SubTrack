package com.elinacn.subtrack.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.domain.repository.ReminderStateRepository
import com.elinacn.subtrack.domain.repository.SettingsRepository
import com.elinacn.subtrack.domain.repository.SubscriptionRepository
import com.elinacn.subtrack.domain.usecase.CurrencyConverter
import com.elinacn.subtrack.domain.usecase.PaymentCountdown
import com.elinacn.subtrack.reminder.ReminderNotificationStatus
import com.elinacn.subtrack.ui.common.UiText
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
import java.math.BigDecimal
import java.math.RoundingMode
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
    private val screenState = MutableStateFlow(ScreenState())

    /**
     * The single source of truth for the screen.
     *
     * combine merges the stored list with the state above, and stateIn turns the result into a hot
     * StateFlow that remembers its last value, so a recomposing screen reads it without re-running
     * the query. WhileSubscribed(5_000) keeps the query alive for five seconds after the last
     * collector leaves: a rotation tears the activity down and rebuilds it well inside that window,
     * so the database observer survives and the list does not blink.
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
        val today = LocalDate.now(clock)
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
            countdowns = visible.mapNotNull { subscription ->
                subscription.nextPaymentDate?.let { date ->
                    subscription.id to PaymentCountdown.between(today, date)
                }
            }.toMap(),
            // The total follows the filter: the number under the heading has to be the sum of
            // the rows the user can see, or it is answering a question nobody asked.
            monthlyTotal = converter.totalIn(visible, mainCurrency),
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
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HomeUiState()
    )

    /** Single entry point for everything the screen can ask for. */
    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.OpenAddSheet -> screenState.update { it.clearedErrors(open = true) }

            HomeEvent.DismissAddSheet -> screenState.update { it.clearedErrors(open = false) }

            is HomeEvent.Save ->
                save(event.name, event.rawPrice, event.currency, event.nextPaymentDate, event.category)

            is HomeEvent.SelectCategoryFilter ->
                screenState.update { it.copy(categoryFilter = event.category) }

            HomeEvent.ClearNameError -> screenState.update { it.copy(nameError = null) }

            HomeEvent.ClearPriceError -> screenState.update { it.copy(priceError = null) }

            HomeEvent.ClearDateError -> screenState.update { it.copy(dateError = null) }

            is HomeEvent.Delete -> delete(event.id)

            HomeEvent.UndoDelete -> undoDelete()

            HomeEvent.DismissUndo -> screenState.update { it.copy(pendingUndo = null) }

            HomeEvent.DismissError -> screenState.update { it.copy(errorMessage = null) }

            HomeEvent.NotificationRequestHandled ->
                screenState.update { it.copy(shouldRequestNotificationPermission = false) }
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
        category: SubscriptionCategory
    ) {
        val trimmedName = name.trim()
        val nameError = if (trimmedName.isEmpty()) UiText.Resource(R.string.error_name_empty) else null
        val priceResult = parsePrice(rawPrice)
        val dateError = validateDate(nextPaymentDate)

        if (nameError != null || priceResult is PriceResult.Invalid || dateError != null) {
            screenState.update {
                it.copy(
                    nameError = nameError,
                    priceError = (priceResult as? PriceResult.Invalid)?.reason,
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
                        billingPeriod = BillingPeriod.MONTHLY,
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

    /**
     * A past date is fine - someone entering a subscription they already have knows when it
     * last renewed. Only the far future is refused, for the same reason as [MAX_PRICE]: it
     * catches a slipped keystroke in the year, not a plausible entry.
     */
    private fun validateDate(nextPaymentDate: LocalDate?): UiText? {
        if (nextPaymentDate == null) return null
        val furthest = LocalDate.now(clock).plusYears(MAX_YEARS_AHEAD)
        // The limit travels as an argument so message and constant cannot drift apart.
        return if (nextPaymentDate.isAfter(furthest)) {
            UiText.Resource(R.string.error_date_too_far, listOf(MAX_YEARS_AHEAD))
        } else {
            null
        }
    }

    /**
     * Reads what the user typed into whole minor units.
     *
     * BigDecimal, never Double: "159,99" has to come back out as exactly 15999 kuruş, and binary
     * floating point cannot promise that.
     *
     * Both the ceiling and the decimal count are checked before converting, so nothing is ever
     * quietly reshaped on the way to storage.
     */
    private fun parsePrice(rawPrice: String): PriceResult {
        val normalized = rawPrice.trim().replace(',', '.')
        if (normalized.isEmpty()) {
            return PriceResult.Invalid(UiText.Resource(R.string.error_price_empty))
        }
        val amount = normalized.toBigDecimalOrNull()
            ?: return PriceResult.Invalid(UiText.Resource(R.string.error_price_invalid))
        if (amount.signum() <= 0) {
            return PriceResult.Invalid(UiText.Resource(R.string.error_price_not_positive))
        }
        if (amount > MAX_PRICE) {
            // The limit is handed to the message instead of being written into it, so the two
            // cannot drift apart when the ceiling changes.
            return PriceResult.Invalid(
                UiText.Resource(R.string.error_price_too_large, listOf(MAX_PRICE.toLong()))
            )
        }
        // Trailing zeros do not count: "159.990" is two decimals written long, "159.999" is three.
        if (amount.stripTrailingZeros().scale() > MINOR_UNIT_DIGITS) {
            return PriceResult.Invalid(UiText.Resource(R.string.error_price_too_many_decimals))
        }
        val cents = amount
            .movePointRight(MINOR_UNIT_DIGITS)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
        return PriceResult.Valid(Money(cents))
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

    private data class ScreenState(
        val isAddSheetOpen: Boolean = false,
        val nameError: UiText? = null,
        val priceError: UiText? = null,
        val dateError: UiText? = null,
        val errorMessage: UiText? = null,
        val pendingUndo: Subscription? = null,
        val shouldRequestNotificationPermission: Boolean = false,
        /** Survives a rotation with the ViewModel, and dies with the process. */
        val categoryFilter: SubscriptionCategory? = null
    )

    /** Opening, dismissing and a successful save all leave the form without complaints. */
    private fun ScreenState.clearedErrors(open: Boolean) = copy(
        isAddSheetOpen = open,
        nameError = null,
        priceError = null,
        dateError = null
    )

    private sealed interface PriceResult {
        data class Valid(val money: Money) : PriceResult
        data class Invalid(val reason: UiText) : PriceResult
    }

    private companion object {
        /** Outlives a configuration change, expires on a real departure. */
        const val STOP_TIMEOUT_MS = 5_000L

        const val MINOR_UNIT_DIGITS = 2

        /** The list holds exactly one dated subscription only right after the first one lands. */
        const val FIRST_DATED_SUBSCRIPTION = 1

        /**
         * How far ahead a renewal date may be set.
         *
         * A product ceiling like [MAX_PRICE], not a technical one: ten years is
         * longer than any subscription anyone signs, so beyond it is a typed year.
         */
        const val MAX_YEARS_AHEAD = 10L

        /**
         * A product ceiling, not a Long limit.
         *
         * Long would not complain until roughly 92 quadrillion kuruş, so guarding against overflow
         * catches nothing a person could plausibly type. One million per billing period is already
         * three orders of magnitude above the priciest real subscription, and leaves room for
         * weaker currencies when phase 9 adds the choice - while still rejecting a slipped keypress
         * that adds digits.
         */
        val MAX_PRICE: BigDecimal = BigDecimal("1000000")
    }
}
