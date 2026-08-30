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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme

/**
 * Picks the currency a new subscription is priced in.
 *
 * A chip row rather than a dropdown because it costs one tap instead of two, and all four options
 * are readable without opening anything - PROJECT_SPEC section 3 puts a fifteen second ceiling on
 * adding a subscription, and a menu spends part of that on a step that shows nothing new.
 *
 * Not segmented buttons, which would be the closer Material match for a four-way single choice:
 * SegmentedButton reserves 18dp for a check icon plus 8dp of spacing in every segment whether an
 * icon is drawn or not, so four of them need about 320dp and do not fit a 360dp phone once the
 * sheet's own padding is taken off. A chip is roughly 58dp here and four fit with room to spare.
 * FlowRow wraps them to a second line instead of clipping when the user has scaled their font up.
 *
 * Past five or six currencies this stops being the right shape and a dropdown wins.
 */
@Composable
fun CurrencySelector(
    selected: Currency,
    onSelect: (Currency) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacerSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacerSmall)
    ) {
        Currency.entries.forEach { currency ->
            val label = stringResource(id = currency.labelRes())
            FilterChip(
                selected = currency == selected,
                onClick = { onSelect(currency) },
                // The chip reads "TRY", which a screen reader would say as a word. The full name
                // replaces it; Material still announces the selected state and the control type,
                // so only the name needed replacing.
                modifier = Modifier.semantics { contentDescription = label },
                label = { Text(currency.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

/**
 * The spoken name of a currency.
 *
 * An exhaustive when rather than a map, so adding a [Currency] fails to compile until it has a
 * name to be read out.
 */
@StringRes
private fun Currency.labelRes(): Int = when (this) {
    Currency.TRY -> R.string.currency_try
    Currency.USD -> R.string.currency_usd
    Currency.EUR -> R.string.currency_eur
    Currency.GBP -> R.string.currency_gbp
}

@Preview(showBackground = true)
@Composable
private fun CurrencySelectorPreview() {
    SubTrackTheme {
        CurrencySelector(selected = Currency.USD, onSelect = {})
    }
}
