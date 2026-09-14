package com.elinacn.subtrack.ui.edit

import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.ui.common.UiText
import java.time.LocalDate

/**
 * Everything the edit screen draws.
 *
 * [subscription] is what was **loaded**, not what is being typed: the form owns the six fields
 * while they are being edited (§5), and this is only the starting point it is seeded from. That is
 * also why a re-emission cannot exist here - the row is read once, on the way in.
 *
 * Three states are mutually exclusive on the way in and the screen picks between them: still
 * loading, [isMissing], or loaded.
 */
data class EditSubscriptionUiState(
    val subscription: Subscription? = null,
    val nameError: UiText? = null,
    val priceError: UiText? = null,
    val dateError: UiText? = null,
    /** A failed write, shown without closing the screen so the entry is not lost. */
    val errorMessage: UiText? = null,
    val isLoading: Boolean = true,
    /**
     * The id does not name a subscription any more.
     *
     * Reachable in practice: a row can be swiped away while its edit screen is being opened, and
     * the back stack can outlive the row it points at.
     */
    val isMissing: Boolean = false,
    /** The write landed; the screen's one job now is to leave. */
    val isSaved: Boolean = false
)

/** The only things the edit screen can ask for. */
sealed interface EditSubscriptionEvent {

    /** Store the form as it stands. The price arrives as typed; parsing belongs to the ViewModel. */
    data class Save(
        val name: String,
        val rawPrice: String,
        val currency: Currency,
        val nextPaymentDate: LocalDate?,
        val category: SubscriptionCategory,
        val billingPeriod: BillingPeriod
    ) : EditSubscriptionEvent

    /** Sent as the user edits, so a stale error stops contradicting what is on screen. */
    data object ClearNameError : EditSubscriptionEvent

    data object ClearPriceError : EditSubscriptionEvent

    data object ClearDateError : EditSubscriptionEvent

    data object DismissError : EditSubscriptionEvent
}
