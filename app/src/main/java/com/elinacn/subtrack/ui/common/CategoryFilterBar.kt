package com.elinacn.subtrack.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
 * Narrows the list to one category, or shows everything.
 *
 * Scrolls sideways rather than wrapping, unlike the two chip rows in the add form. Five chips do
 * not fit a 360dp screen - four already needed 788px against 624px available - and this strip sits
 * above the list permanently. Wrapping would cost a second line of vertical space on every screen
 * forever; scrolling costs a gesture only when the user wants the chip that is off the edge.
 *
 * [selected] is null for "all", which is the absence of a filter rather than a fifth category.
 */
@Composable
fun CategoryFilterBar(
    selected: SubscriptionCategory?,
    onSelect: (SubscriptionCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpacerSmall),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacerSmall)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(id = R.string.filter_all)) },
            border = filterBorder(),
            colors = filterColors()
        )
        SubscriptionCategory.entries.forEach { category ->
            FilterChip(
                selected = category == selected,
                onClick = { onSelect(category) },
                label = { Text(stringResource(id = category.labelRes())) },
                border = filterBorder(),
                colors = filterColors()
            )
        }
    }
}

/** The same selected colours as the two pickers in the add form, so a chip means one thing. */
/**
 * The boundary of an unselected chip.
 *
 * `outline`, not Material's default `outlineVariant`: the second is a divider colour and measures
 * 1.36:1 against this screen's backdrop, while a border that identifies a control wants 3:1.
 * `outline` is 3.32:1 there.
 */
@Composable
private fun filterBorder() = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = false,
    borderColor = MaterialTheme.colorScheme.outline
)

@Composable
private fun filterColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
)

@Preview(showBackground = true)
@Composable
private fun CategoryFilterBarPreview() {
    SubTrackTheme {
        CategoryFilterBar(selected = SubscriptionCategory.HEALTH, onSelect = {})
    }
}
