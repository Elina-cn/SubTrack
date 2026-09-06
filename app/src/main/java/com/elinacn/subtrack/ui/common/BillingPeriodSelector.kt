package com.elinacn.subtrack.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme

/**
 * How often the subscription is paid for.
 *
 * The third chip row in the form and the same shape as the other two, because a chip already means
 * one thing here: a closed set of options, one tap, everything readable without opening anything.
 *
 * FlowRow like [CategorySelector], not the horizontal scroll the filter bar uses. The form scrolls
 * already, so a second line costs nothing permanent; the filter bar sits above the list on every
 * screen forever, which is what made a gesture the cheaper trade there.
 *
 * Unlike the category, this one has no "did not answer" value - every subscription is billed on
 * some clock, and the default is the one nearly all of them use.
 */
@Composable
fun BillingPeriodSelector(
    selected: BillingPeriod,
    onSelect: (BillingPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacerSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacerSmall)
    ) {
        BillingPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = { Text(stringResource(id = period.labelRes())) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

/**
 * The name a billing period is shown under.
 *
 * An exhaustive when rather than a map, so adding a [BillingPeriod] fails to compile until it has
 * a name. The enum stays English and never reaches the screen; a string resource id has no
 * business in domain.
 */
@StringRes
fun BillingPeriod.labelRes(): Int = when (this) {
    BillingPeriod.MONTHLY -> R.string.period_monthly
    BillingPeriod.YEARLY -> R.string.period_yearly
    BillingPeriod.WEEKLY -> R.string.period_weekly
}

@Preview(showBackground = true)
@Composable
private fun BillingPeriodSelectorPreview() {
    SubTrackTheme {
        BillingPeriodSelector(selected = BillingPeriod.MONTHLY, onSelect = {})
    }
}
