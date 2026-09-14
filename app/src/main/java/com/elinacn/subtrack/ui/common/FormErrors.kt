package com.elinacn.subtrack.ui.common

import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.usecase.DateProblem
import com.elinacn.subtrack.domain.usecase.NameProblem
import com.elinacn.subtrack.domain.usecase.PriceProblem
import com.elinacn.subtrack.domain.usecase.SubscriptionInput

/**
 * The sentence a refused field is explained with.
 *
 * The rule and its wording are deliberately apart: [SubscriptionInput] decides what is acceptable
 * and answers with a reason, and this file - in ui/, where string resources belong - turns that
 * reason into text. Both forms that write a subscription go through both halves, so a rule can
 * never be enforced in one screen and worded differently in the other.
 *
 * The two limits travel as arguments rather than being written into the messages, so a message
 * and the constant behind it cannot drift apart when the ceiling changes.
 */
fun NameProblem.asUiText(): UiText = when (this) {
    NameProblem.EMPTY -> UiText.Resource(R.string.error_name_empty)
}

fun PriceProblem.asUiText(): UiText = when (this) {
    PriceProblem.EMPTY -> UiText.Resource(R.string.error_price_empty)
    PriceProblem.MALFORMED -> UiText.Resource(R.string.error_price_invalid)
    PriceProblem.NOT_POSITIVE -> UiText.Resource(R.string.error_price_not_positive)
    PriceProblem.TOO_LARGE -> UiText.Resource(
        R.string.error_price_too_large,
        listOf(SubscriptionInput.MAX_PRICE.toLong())
    )
    PriceProblem.TOO_MANY_DECIMALS -> UiText.Resource(R.string.error_price_too_many_decimals)
}

fun DateProblem.asUiText(): UiText = when (this) {
    DateProblem.TOO_FAR_AHEAD -> UiText.Resource(
        R.string.error_date_too_far,
        listOf(SubscriptionInput.MAX_YEARS_AHEAD)
    )
}
