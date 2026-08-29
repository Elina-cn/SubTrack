package com.elinacn.subtrack.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme

/**
 * The monthly total, already formatted by the caller.
 *
 * [conversionNote] is shown only when there is something to explain - a list priced in more than
 * one currency. Left out of a single-currency list on purpose: a permanent line about exchange
 * rates would be noise for the many users who never leave TRY.
 */
@Composable
fun DashboardCard(
    totalAmount: String,
    modifier: Modifier = Modifier,
    conversionNote: String? = null
) {
    val label = stringResource(id = R.string.total_monthly)
    // Written out here rather than left to merging. mergeDescendants keeps every child in the
    // accessibility tree - the delegate walks the unmerged tree - so the card still offered three
    // stops, and the amount was one of them: "219.89 TL" with nothing saying what it totals.
    // clearAndSetSemantics drops the children and speaks one sentence in an order we choose.
    val description = if (conversionNote == null) {
        stringResource(id = R.string.dashboard_description, label, totalAmount)
    } else {
        stringResource(id = R.string.dashboard_description_converted, label, totalAmount, conversionNote)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.ScreenPadding)
            .clearAndSetSemantics { contentDescription = description },
        shape = RoundedCornerShape(Dimens.DashboardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.DashboardElevation)
    ) {
        Column(modifier = Modifier.padding(Dimens.DashboardPadding)) {
            Text(
                text = label,
                // No alpha: dimming this to 0.7 dropped it to 3.6:1. The size and weight gap
                // against the amount below already carries the hierarchy.
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(Dimens.SpacerSmall))
            Text(
                text = totalAmount,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.headlineMedium
            )
            if (conversionNote != null) {
                Spacer(modifier = Modifier.height(Dimens.SpacerSmall))
                Text(
                    text = conversionNote,
                    // Same pair as the label above, measured at 7.11:1 in both themes - well past
                    // the 4.5:1 small text needs, so this stays at full opacity too.
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardCardPreview() {
    SubTrackTheme {
        DashboardCard(totalAmount = "₺219,89")
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardCardEmptyPreview() {
    SubTrackTheme {
        DashboardCard(totalAmount = "₺0,00")
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardCardConvertedPreview() {
    SubTrackTheme {
        DashboardCard(
            totalAmount = "₺1.284,52",
            conversionNote = "Farklı para birimleri sabit kurla TRY cinsine çevrildi"
        )
    }
}
