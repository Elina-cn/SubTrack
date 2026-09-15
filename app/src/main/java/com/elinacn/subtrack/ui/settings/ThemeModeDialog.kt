package com.elinacn.subtrack.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.ThemeMode
import com.elinacn.subtrack.ui.theme.Dimens

/** What each choice is called. */
internal fun ThemeMode.labelId(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_mode_system
    ThemeMode.LIGHT -> R.string.theme_mode_light
    ThemeMode.DARK -> R.string.theme_mode_dark
}

/**
 * The three-way theme chooser.
 *
 * A dialog rather than chips or a segmented row, and the reason is width. The main-currency
 * selector gets away with chips because "TRY USD EUR GBP" is four short words; "Sistemi takip et"
 * alone is wider than a third of a 360dp screen, and at font scale 2.0 none of the three fit
 * beside each other. A dialog gives each choice its own full-width line at any scale, and it is
 * already the shape this screen uses for the rationale and the rate reset.
 *
 * Picking applies immediately and closes, so there is no confirm button - only a way back out.
 * Each line is one focus stop at the 48dp floor: the [RadioButton] takes a null onClick so the
 * row, not the button, is the target.
 */
@Composable
internal fun ThemeModeDialog(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.theme_mode_title)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = mode == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(mode) }
                            )
                            .defaultMinSize(minHeight = Dimens.MinTouchTarget),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == selected, onClick = null)
                        Spacer(modifier = Modifier.width(Dimens.SpacerMedium))
                        Text(
                            text = stringResource(id = mode.labelId()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}
