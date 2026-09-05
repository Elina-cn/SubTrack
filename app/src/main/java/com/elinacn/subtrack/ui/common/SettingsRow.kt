package com.elinacn.subtrack.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.elinacn.subtrack.R
import com.elinacn.subtrack.ui.theme.Dimens

/**
 * A tappable settings row: a title with a line of explanation under it.
 *
 * The whole row is the target rather than the text alone, and defaultMinSize keeps it at the
 * accessibility floor even when the label happens to be shorter than that.
 *
 * The semantics block names the *node*. clickable's onClickLabel names only the action, and a
 * screen reader would still announce an unnamed button - the mistake phase 10a made twice. Title
 * and description are merged so the row is one focus stop that reads as a sentence.
 */
@Composable
fun SettingsRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rowDescription = stringResource(id = R.string.settings_row_description, title, description)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = Dimens.MinTouchTarget)
            .padding(vertical = Dimens.SpacerMedium)
            .semantics(mergeDescendants = true) { contentDescription = rowDescription },
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
