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
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme

/**
 * Picks the bucket a subscription is filed under.
 *
 * The same shape as [CurrencySelector] on purpose - a chip row, one tap, everything readable
 * without opening anything. Four closed options, and the user cannot add a fifth.
 *
 * No contentDescription override here, unlike the currency chips. Those show an ISO code a screen
 * reader would read as a word ("try"), so the full name had to replace it; a category chip already
 * shows the word it means.
 *
 * FlowRow rather than a horizontal scroll: if the four chips do not fit - a long translation, a
 * scaled-up font - they wrap to a second line, which stays reachable without a gesture.
 */
@Composable
fun CategorySelector(
    selected: SubscriptionCategory,
    onSelect: (SubscriptionCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacerSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacerSmall)
    ) {
        SubscriptionCategory.entries.forEach { category ->
            FilterChip(
                selected = category == selected,
                onClick = { onSelect(category) },
                label = { Text(stringResource(id = category.labelRes())) },
                // An unselected chip is drawn by its outline, and Material's default for that is
                // `outlineVariant` - a divider colour, 1.36:1 against our backdrop (measured on the
                // device). `outline` is the role meant for a boundary that identifies a control and
                // is 3.32:1 there, so the chip asks for that one by name.
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = false,
                    borderColor = MaterialTheme.colorScheme.outline
                ),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

/**
 * The name a category is shown under.
 *
 * An exhaustive when rather than a map, so adding a [SubscriptionCategory] fails to compile until
 * it has a name. The enum stays English and never reaches the screen; the mapping lives here
 * because a string resource id has no business in domain.
 */
@StringRes
fun SubscriptionCategory.labelRes(): Int = when (this) {
    SubscriptionCategory.ENTERTAINMENT -> R.string.category_entertainment
    SubscriptionCategory.PRODUCTIVITY -> R.string.category_productivity
    SubscriptionCategory.HEALTH -> R.string.category_health
    SubscriptionCategory.OTHER -> R.string.category_other
}

@Preview(showBackground = true)
@Composable
private fun CategorySelectorPreview() {
    SubTrackTheme {
        CategorySelector(selected = SubscriptionCategory.HEALTH, onSelect = {})
    }
}
