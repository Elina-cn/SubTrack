package com.elinacn.subtrack.ui.home.components

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
import com.elinacn.subtrack.ui.theme.Dimens

/**
 * One subscription: icon, name and the already formatted price.
 *
 * The row measures 56dp - 24dp of content between 16dp of padding top and bottom - so it clears
 * the 48dp Android touch target minimum that the swipe gesture needs.
 */
@Composable
fun SubscriptionCard(
    name: String,
    price: String,
    modifier: Modifier = Modifier
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
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = price,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/** Known services get their own icon; everything else falls back to a star. */
private fun iconFor(name: String): ImageVector = when (name.lowercase()) {
    "netflix" -> Icons.Default.PlayArrow
    "spotify" -> Icons.AutoMirrored.Filled.List
    "youtube" -> Icons.Default.PlayArrow
    "icloud", "drive" -> Icons.Default.Cloud
    else -> Icons.Default.Star
}
