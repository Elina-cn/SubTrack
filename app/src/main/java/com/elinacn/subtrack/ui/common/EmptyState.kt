package com.elinacn.subtrack.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme

/**
 * What a list shows when it has nothing in it: a glyph, a line saying so, and a line saying what
 * to do about it.
 *
 * Every part is a parameter because the app will have more than one empty list - phase 11 adds a
 * category filter that can match nothing, and that is the same shape with different words.
 *
 * No button. The screen already has a floating action button in the corner, and a second way in
 * would only make the user choose between two doors to the same room.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    // clearAndSetSemantics, not mergeDescendants. Merging leaves the children in the tree - the
    // accessibility delegate walks the unmerged one - so the block would still offer two stops,
    // the second of them a bare instruction with nothing saying what it refers to. The dashboard
    // total hit exactly this in phase 8a and was fixed the same way.
    val description = stringResource(id = R.string.empty_state_description, title, message)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.EmptyStatePadding)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            // Decorative: the two lines below say everything the glyph is standing in for.
            contentDescription = null,
            modifier = Modifier.size(Dimens.EmptyStateIconSize),
            // primary, not onSurfaceVariant. Both are defined since phase 14a, and both are in
            // the palette; primary is the one this app uses for ink and icons, and a glyph this
            // size is the loudest thing on an otherwise empty screen. 5.98:1 on the light
            // backdrop, 8.50:1 on the dark one.
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(Dimens.SpacerLarge))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.SpacerSmall))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            // Same role as the title, no alpha. The size and weight gap carries the hierarchy;
            // dimming it would buy nothing and cost contrast, the lesson from the dashboard card.
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The empty state for a subscription list that has never had anything in it.
 *
 * The list glyph is deliberate: [Icons.AutoMirrored.Filled.List] is already pulled in for the
 * subscription cards, so this adds nothing to the icon set that phase 16 has to narrow down.
 */
@Composable
fun EmptySubscriptions(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.AutoMirrored.Filled.List,
        title = stringResource(id = R.string.empty_subscriptions_title),
        message = stringResource(id = R.string.empty_subscriptions_message),
        modifier = modifier
    )
}

/**
 * The empty state for a category filter that matches nothing.
 *
 * The same glyph as [EmptySubscriptions] on purpose: the two states are the same situation seen
 * through different windows, and the words are what tell them apart. It also keeps the icon set
 * phase 16 has to narrow down at nine names.
 */
@Composable
fun EmptyCategory(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.AutoMirrored.Filled.List,
        title = stringResource(id = R.string.empty_category_title),
        message = stringResource(id = R.string.empty_category_message),
        modifier = modifier
    )
}

/**
 * The empty state for the statistics screen before there is anything to chart.
 *
 * The bar-chart glyph rather than the list one: it is the icon the screen was entered by, so the
 * empty page still looks like the place the user asked for. It adds nothing to the icon set phase
 * 16 has to narrow down - the top bar already carries this name.
 */
@Composable
fun EmptyStatistics(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Default.BarChart,
        title = stringResource(id = R.string.empty_statistics_title),
        message = stringResource(id = R.string.empty_statistics_message),
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatisticsPreview() {
    SubTrackTheme {
        EmptyStatistics()
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyCategoryPreview() {
    SubTrackTheme {
        EmptyCategory()
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptySubscriptionsPreview() {
    SubTrackTheme {
        EmptySubscriptions()
    }
}
