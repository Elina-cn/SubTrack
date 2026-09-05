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
            // primary, not onSurfaceVariant. That role is undefined in our scheme and falls back
            // to the Material baseline's purple-grey, outside the palette (ARCHITECTURE §12).
            // primary is the family this app uses for ink and icons, and it is measured.
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

@Preview(showBackground = true)
@Composable
private fun EmptySubscriptionsPreview() {
    SubTrackTheme {
        EmptySubscriptions()
    }
}
