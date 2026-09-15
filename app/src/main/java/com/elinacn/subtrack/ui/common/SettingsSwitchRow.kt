package com.elinacn.subtrack.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.toggleableState
import com.elinacn.subtrack.R
import com.elinacn.subtrack.ui.theme.Dimens

/**
 * A settings row that carries a switch: a title, a line of explanation, and the control.
 *
 * The same shape as [SettingsRow] and for the same reasons - the whole row is the target,
 * defaultMinSize holds it at the accessibility floor, and the merging semantics keep title and
 * description as one focus stop that reads as a sentence (the 10c-1 pattern).
 *
 * Two details differ because a switch is not a link. The switch itself takes a null
 * `onCheckedChange`, which hands the gesture to the row rather than leaving two targets stacked on
 * top of each other; and the merged node states the on/off value itself, because a screen reader
 * that reads a merged row would otherwise announce the sentence without saying which way the
 * switch is set.
 *
 * A row that is [enabled] = false still reads out: it says what the setting is and the description
 * says why it cannot be used. Removing it instead would leave the reason unsaid.
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val rowDescription = stringResource(id = R.string.settings_row_description, title, description)
    val titleColor = MaterialTheme.colorScheme.onBackground.atContentAlpha(enabled)
    val descriptionColor = MaterialTheme.colorScheme.onSurfaceVariant.atContentAlpha(enabled)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                onValueChange = onCheckedChange
            )
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
                toggleableState = ToggleableState(checked)
            }
            .defaultMinSize(minHeight = Dimens.MinTouchTarget)
            .padding(vertical = Dimens.SpacerMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = descriptionColor
            )
        }

        Spacer(modifier = Modifier.width(Dimens.SpacerMedium))

        // Null, not a lambda: the row above already owns the gesture, and a switch with its own
        // handler would be a second focus stop sitting inside the merged one.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * Dims a role's colour when the control it paints is unavailable.
 *
 * Still the theme's colour, only at Material's disabled opacity - the role decides the hue, this
 * decides whether the row reads as usable.
 */
private fun Color.atContentAlpha(enabled: Boolean): Color =
    if (enabled) this else copy(alpha = DISABLED_CONTENT_ALPHA)

/** Material's opacity for disabled content. */
private const val DISABLED_CONTENT_ALPHA = 0.38f
