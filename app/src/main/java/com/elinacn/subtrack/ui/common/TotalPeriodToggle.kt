package com.elinacn.subtrack.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.TotalPeriod
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme

/**
 * Switches the figure above between what it costs a month and what it costs a year.
 *
 * Two chips, the fourth chip row in the app and the same idiom as the other three: one tap, both
 * options visible, no menu to open. A segmented button would say the same thing in a control this
 * app uses nowhere else.
 *
 * It sits under the dashboard card rather than inside it because the card is one focus stop -
 * clearAndSetSemantics drops its children - and a control inside would be dropped with them.
 *
 * Two chips always fit: the widest pair is 360dp of "Aylık" and "Yıllık", so there is nothing to
 * wrap or scroll here.
 */
@Composable
fun TotalPeriodToggle(
    selected: TotalPeriod,
    onSelect: (TotalPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacerSmall)
    ) {
        TotalPeriod.entries.forEach { period ->
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
 * The word on the chip.
 *
 * The same two words the billing period uses, because they mean the same thing to the reader; a
 * separate pair would be two translations of one word waiting to drift apart.
 */
@StringRes
fun TotalPeriod.labelRes(): Int = when (this) {
    TotalPeriod.MONTHLY -> R.string.period_monthly
    TotalPeriod.YEARLY -> R.string.period_yearly
}

/** The heading over the figure itself, which names what the number is rather than the choice. */
@StringRes
fun TotalPeriod.totalLabelRes(): Int = when (this) {
    TotalPeriod.MONTHLY -> R.string.total_monthly
    TotalPeriod.YEARLY -> R.string.total_yearly
}

@Preview(showBackground = true)
@Composable
private fun TotalPeriodTogglePreview() {
    SubTrackTheme {
        TotalPeriodToggle(selected = TotalPeriod.MONTHLY, onSelect = {})
    }
}
