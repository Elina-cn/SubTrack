package com.elinacn.subtrack.ui.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.domain.usecase.PaymentCountdown
import com.elinacn.subtrack.ui.common.labelRes

/**
 * The whole row as the single sentence a screen reader reads out.
 *
 * The row is one focus stop (phase 8a), so whatever is left out of this sentence is simply not
 * announced - the card's lines are not separately reachable.
 *
 * Built by appending one fact at a time rather than as one resource per combination: the row can
 * carry a countdown or not and a category or not, which with the period would be four strings to
 * keep in step in every language. The order below is the order the card draws them in.
 */
@Composable
fun subscriptionRowDescription(
    subscription: Subscription,
    price: String,
    countdown: PaymentCountdown?
): String {
    val named = stringResource(
        id = R.string.subscription_row_description,
        subscription.name,
        price
    )
    val withPeriod = named.and(stringResource(id = subscription.billingPeriod.labelRes()))
    val withCountdown = countdown?.let { withPeriod.and(it.asString()) } ?: withPeriod
    // Left out when it is OTHER, exactly as the card leaves it out: a screen reader should hear
    // the row the sighted user sees, not a default nobody chose.
    val category = subscription.category.takeIf { it != SubscriptionCategory.OTHER }
    return category?.let { withCountdown.and(stringResource(id = it.labelRes())) } ?: withCountdown
}

/** One more fact on the end of the sentence so far. */
@Composable
private fun String.and(fact: String): String =
    stringResource(id = R.string.subscription_row_description_more, this, fact)

/** The countdown as one phrase, resolved here because the row's whole label is built here. */
@Composable
private fun PaymentCountdown.asString(): String = when (this) {
    is PaymentCountdown.Upcoming ->
        pluralStringResource(R.plurals.days_until_payment, days.toInt(), days)
    PaymentCountdown.DueToday -> stringResource(id = R.string.due_today)
}
