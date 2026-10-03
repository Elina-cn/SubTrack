package com.elinacn.subtrack.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.elinacn.subtrack.R

/**
 * The settings screen's dialogs, each shown while its flag in [uiState] is set.
 *
 * A file of its own only to keep the screen under the size limit (CLAUDE.md section 4).
 */
@Composable
internal fun SettingsDialogs(uiState: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    // Only once the stored mode is known: the chooser marks the current choice, and a tap on the
    // row in the first few frames would otherwise open it with a guess marked. The tap is not lost
    // - the dialog appears as soon as the mode arrives.
    val themeMode = uiState.themeMode
    if (uiState.isThemeDialogVisible && themeMode != null) {
        ThemeModeDialog(
            selected = themeMode,
            onSelect = { onEvent(SettingsEvent.SelectThemeMode(it)) },
            onDismiss = { onEvent(SettingsEvent.ThemeDialogDismissed) }
        )
    }

    // Same rule as the theme chooser: the clock opens on the stored time, so it waits for it.
    val reminderTime = uiState.reminderTime
    if (uiState.isReminderTimePickerVisible && reminderTime != null) {
        ReminderTimeDialog(
            initialTime = reminderTime,
            is24Hour = uiState.is24HourFormat,
            onConfirm = { hour, minute -> onEvent(SettingsEvent.SelectReminderTime(hour, minute)) },
            onDismiss = { onEvent(SettingsEvent.ReminderTimeDialogDismissed) }
        )
    }

    if (uiState.isReminderRationaleVisible) {
        AlertDialog(
            onDismissRequest = { onEvent(SettingsEvent.ReminderRationaleDismissed) },
            title = { Text(stringResource(id = R.string.reminder_rationale_title)) },
            text = { Text(stringResource(id = R.string.reminder_rationale_message)) },
            confirmButton = {
                TextButton(onClick = { onEvent(SettingsEvent.ReminderRationaleConfirmed) }) {
                    Text(stringResource(id = R.string.reminder_rationale_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(SettingsEvent.ReminderRationaleDismissed) }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }
}
