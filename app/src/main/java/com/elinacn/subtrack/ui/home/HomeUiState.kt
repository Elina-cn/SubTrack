package com.elinacn.subtrack.ui.home

import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.domain.usecase.PaymentCountdown
import java.time.LocalDate
import com.elinacn.subtrack.ui.common.UiText

/**
 * Everything the home screen draws, in one value.
 *
 * The screen renders this and nothing else - no second source of truth, no computation of its own.
 *
 * Field errors are separate from [errorMessage] on purpose: a bad name belongs under the name box,
 * not in a banner that does not say which box is wrong. [errorMessage] is for failures with no
 * field to point at, such as the database refusing a write.
 */
data class HomeUiState(
    val subscriptions: List<Subscription> = emptyList(),
    /**
     * How far off each subscription's next payment is, keyed by id, for the ones that have a date.
     *
     * Kept beside the list rather than inside [Subscription] because it is not a property of the
     * subscription - it is a property of the subscription and today together, and the ViewModel is
     * where "today" is known. Recomputed on every emission; a session left open past midnight
     * keeps yesterday's numbers until something else changes.
     */
    val countdowns: Map<Long, PaymentCountdown> = emptyMap(),
    /** Every subscription converted into [baseCurrency] and added up. */
    val monthlyTotal: Money = Money.ZERO,
    /** What [monthlyTotal] is denominated in. Read from the stored main-currency preference. */
    val baseCurrency: Currency = Currency.Base,
    /**
     * True when at least one subscription is priced in something other than [baseCurrency].
     *
     * Drives a line under the total saying so. Without it a mixed list produces a number with no
     * explanation of how prices in three currencies became one figure.
     */
    val isTotalConverted: Boolean = false,
    val isLoading: Boolean = true,
    /** Owned here rather than by the composable: whether it may close depends on validation. */
    val isAddSheetOpen: Boolean = false,
    /**
     * The category the add form has selected.
     *
     * Optional for the user: leaving it alone stores [SubscriptionCategory.OTHER], because
     * PROJECT_SPEC section 3 puts a fifteen second ceiling on adding a subscription and a
     * required field on a bucket nobody has to care about would spend part of it.
     */
    val selectedCategory: SubscriptionCategory = SubscriptionCategory.OTHER,
    val nameError: UiText? = null,
    val priceError: UiText? = null,
    val dateError: UiText? = null,
    val errorMessage: UiText? = null,
    /** Set for as long as a deletion can still be undone; drives the snackbar. */
    val pendingUndo: Subscription? = null,
    /**
     * Set once, after the first dated subscription is stored, when the notification permission
     * can still be asked for.
     *
     * A one-shot flag rather than a Channel: ARCHITECTURE section 5 keeps the screen reading one
     * value, and the screen clears this through an event as soon as it has acted on it, so a
     * rotation cannot fire the request a second time.
     */
    val shouldRequestNotificationPermission: Boolean = false
)

/** Everything the home screen can ask for. One channel instead of a lambda per action. */
sealed interface HomeEvent {

    data object OpenAddSheet : HomeEvent

    data object DismissAddSheet : HomeEvent

    /**
     * Store a new subscription. The price arrives as typed; parsing belongs to the ViewModel.
     *
     * [nextPaymentDate] is null when the user left the date empty, which is allowed - the card
     * simply shows no countdown then.
     */
    data class Save(
        val name: String,
        val rawPrice: String,
        val currency: Currency,
        val nextPaymentDate: LocalDate? = null
    ) : HomeEvent

    /** The user picked a category in the add form. */
    data class SelectCategory(val category: SubscriptionCategory) : HomeEvent

    /** Sent as the user edits, so a stale error stops contradicting what is on screen. */
    data object ClearNameError : HomeEvent

    data object ClearPriceError : HomeEvent

    data object ClearDateError : HomeEvent

    /** Remove the subscription with this id. Undoable until the snackbar goes. */
    data class Delete(val id: Long) : HomeEvent

    /** Put the last deleted subscription back. */
    data object UndoDelete : HomeEvent

    /** The undo window closed without being used; the deletion is now final. */
    data object DismissUndo : HomeEvent

    data object DismissError : HomeEvent

    /** The screen has shown the permission request; the trigger must not fire again. */
    data object NotificationRequestHandled : HomeEvent
}
