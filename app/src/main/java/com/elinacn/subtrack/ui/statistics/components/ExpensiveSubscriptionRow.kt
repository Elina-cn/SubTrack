package com.elinacn.subtrack.ui.statistics.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme

/**
 * One line of the "most expensive" list: what it is, how it is billed, and what that works out to
 * per month.
 *
 * **The billing period is on the row on purpose.** The figure beside it is a monthly cost, not the
 * price on the subscription card, so a yearly subscription appears here at a twelfth of what the
 * user typed. Without the period the two numbers look like a contradiction; with it they read as
 * the same fact over different spans.
 */
@Composable
fun ExpensiveSubscriptionRow(
    name: String,
    billingPeriod: String,
    monthlyCost: String,
    modifier: Modifier = Modifier
) {
    // One stop that names the row, how it is billed and what it costs - in that order, because
    // the cost only means something once the span it covers has been said. Same reason the
    // dashboard card writes its own sentence rather than merging its children (phase 8a).
    val description = stringResource(
        id = R.string.statistics_cost_description,
        name,
        billingPeriod,
        monthlyCost
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.RowSpacing)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // weight(1f) and a spacer for the same reason as the breakdown row: at font scale 2.0
        // SpaceBetween runs out of space to give and the name touches the amount.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = billingPeriod,
                // onBackground, not onSurfaceVariant: that role is undefined in our scheme and
                // falls back to the Material baseline's purple-grey (ARCHITECTURE §12). The
                // smaller type already separates it from the name.
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.width(Dimens.SpacerSmall))
        Text(
            text = stringResource(id = R.string.statistics_monthly_cost, monthlyCost),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.End,
            // The same role the subscription card gives a price, so an amount looks like an
            // amount wherever it appears.
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExpensiveSubscriptionRowPreview() {
    SubTrackTheme {
        Column {
            ExpensiveSubscriptionRow(name = "Adobe", billingPeriod = "Yıllık", monthlyCost = "₺249,00")
            ExpensiveSubscriptionRow(name = "Netflix", billingPeriod = "Aylık", monthlyCost = "₺159,99")
        }
    }
}
