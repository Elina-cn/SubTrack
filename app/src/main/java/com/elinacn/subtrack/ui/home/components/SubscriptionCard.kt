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
import com.elinacn.subtrack.domain.usecase.PaymentCountdown
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme

/**
 * One subscription: icon, name, the already formatted price and, when there is a date, how far
 * off the next payment is.
 *
 * The row measures 56dp - 24dp of content between 16dp of padding top and bottom - so it clears
 * the 48dp Android touch target minimum that the swipe gesture needs. A countdown adds a second
 * line and the row grows; rows without a date keep their old height rather than reserving space
 * for something that is not there.
 */
@Composable
fun SubscriptionCard(
    name: String,
    price: String,
    modifier: Modifier = Modifier,
    countdown: PaymentCountdown? = null
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
                if (countdown != null) {
                    Text(
                        text = countdown.asText(),
                        // error is the only colorScheme role that carries "something is wrong"
                        // without a new colour being invented. Our scheme does not define it, so
                        // it falls back to the Material baseline red - noted for phase 14 along
                        // with outline and onSurfaceVariant.
                        color = if (countdown is PaymentCountdown.Overdue) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
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
    is PaymentCountdown.Overdue ->
        pluralStringResource(R.plurals.days_overdue, days.toInt(), days)
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
        SubscriptionCard(name = "Netflix", price = "159.99 TL")
    }
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionCardUnknownServicePreview() {
    SubTrackTheme {
        SubscriptionCard(name = "Bir Başka Servis", price = "1299.00 TL")
    }
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionCardUpcomingPreview() {
    SubTrackTheme {
        SubscriptionCard(
            name = "Netflix",
            price = "159.99 TL",
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
            countdown = PaymentCountdown.DueToday
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionCardOverduePreview() {
    SubTrackTheme {
        SubscriptionCard(
            name = "Adobe",
            price = "249.00 TL",
            countdown = PaymentCountdown.Overdue(days = 5)
        )
    }
}
