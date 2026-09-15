package com.elinacn.subtrack.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.domain.usecase.PaymentCountdown
import com.elinacn.subtrack.ui.common.labelRes
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme

/**
 * One subscription: icon, name, how often it is billed, the already formatted price and, when
 * there is a date, how far off the next payment is.
 *
 * The row measures 56dp - 24dp of content between 16dp of padding top and bottom - so it clears
 * the 48dp Android touch target minimum that the swipe gesture needs. A countdown adds a line and
 * the row grows; rows without a date keep their old height rather than reserving space for
 * something that is not there.
 *
 * [billingPeriod] has no default and is drawn on every row, unlike the category. A price is a
 * different amount of money depending on how often it is paid, so a row without the period is not
 * a shorter answer, it is an ambiguous one - and the reader cannot tell an unmarked row from a
 * monthly one. Nothing about money is left to an implied rule.
 */
@Composable
fun SubscriptionCard(
    name: String,
    price: String,
    billingPeriod: BillingPeriod,
    modifier: Modifier = Modifier,
    countdown: PaymentCountdown? = null,
    category: SubscriptionCategory = SubscriptionCategory.OTHER
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.RowSpacing),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.CardElevation)
    ) {
        Row(
            modifier = Modifier.padding(Dimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                iconFor(name),
                // Decorative: the name sits right beside it, so a label would be read twice.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.IconSize)
            )
            Spacer(modifier = Modifier.width(Dimens.IconSpacing))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
                // Its own line under the name rather than a suffix on the price. The price sits
                // in the right-hand column and the name takes what is left, so lengthening the
                // price would narrow the name - and a name too long for its column is a known
                // problem at large font scales (phase 14). A line costs height, which the card
                // has, instead of width, which it does not.
                Text(
                    text = stringResource(id = billingPeriod.labelRes()),
                    // onSurface for the same reason as the category line below: onSurfaceVariant
                    // is undefined in our scheme (ARCHITECTURE section 12). The smaller type is
                    // what separates it from the name.
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall
                )
                if (countdown != null) {
                    Text(
                        text = countdown.asText(),
                        // One colour, because there is one kind of news left to give: the payment
                        // is coming. The error red was here for the overdue state, which the
                        // advancement in phase 12-2 made unreachable.
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // OTHER is the default nobody has to choose, so printing it on every row would
                // be a word that means "no answer" repeated down the whole list. Rows keep the
                // height they had unless the user actually filed the subscription somewhere -
                // the same rule the countdown already follows.
                if (category != SubscriptionCategory.OTHER) {
                    Text(
                        text = stringResource(id = category.labelRes()),
                        // onSurface, not onSurfaceVariant. Both are in the palette since phase
                        // 14a, but the smaller type already separates this line from the name, and
                        // dimming it as well would spend contrast to repeat something the size has
                        // said. 14.45:1 on a light card, 10.05:1 on a dark one.
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                text = price,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/** The countdown as the reader sees it; plurals because Turkish and English do not agree on them. */
@Composable
private fun PaymentCountdown.asText(): String = when (this) {
    is PaymentCountdown.Upcoming ->
        pluralStringResource(R.plurals.days_until_payment, days.toInt(), days)
    PaymentCountdown.DueToday -> stringResource(id = R.string.due_today)
}

/** Known services get their own icon; everything else falls back to a star. */
private fun iconFor(name: String): ImageVector = when (name.lowercase()) {
    "netflix" -> Icons.Default.PlayArrow
    "spotify" -> Icons.AutoMirrored.Filled.List
    "youtube" -> Icons.Default.PlayArrow
    "icloud", "drive" -> Icons.Default.Cloud
    else -> Icons.Default.Star
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionCardPreview() {
    SubTrackTheme {
        SubscriptionCard(
            name = "Netflix",
            price = "159.99 TL",
            billingPeriod = BillingPeriod.MONTHLY
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionCardUnknownServicePreview() {
    SubTrackTheme {
        SubscriptionCard(
            name = "Bir Başka Servis",
            price = "1299.00 TL",
            billingPeriod = BillingPeriod.YEARLY
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionCardUpcomingPreview() {
    SubTrackTheme {
        SubscriptionCard(
            name = "Netflix",
            price = "159.99 TL",
            billingPeriod = BillingPeriod.MONTHLY,
            countdown = PaymentCountdown.Upcoming(days = 3)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionCardDueTodayPreview() {
    SubTrackTheme {
        SubscriptionCard(
            name = "Spotify",
            price = "59.90 TL",
            billingPeriod = BillingPeriod.WEEKLY,
            countdown = PaymentCountdown.DueToday
        )
    }
}
