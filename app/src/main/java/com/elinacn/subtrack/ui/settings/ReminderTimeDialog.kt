package com.elinacn.subtrack.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.ui.theme.SubTrackTheme
import java.time.LocalTime

/**
 * The Material 3 clock for choosing the reminder time.
 *
 * Opens on the stored time, on the phone's 24- or 12-hour clock. Nothing is written until Save:
 * Cancel, a tap outside and Back all leave the stored time as it was. The dial's position while
 * the user turns it is the picker's own state, the way the add sheet's date picker keeps its day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderTimeDialog(
    initialTime: LocalTime,
    is24Hour: Boolean,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = is24Hour
    )
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { TimePickerDialogDefaults.Title(displayMode = TimePickerDisplayMode.Picker) },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) {
                Text(stringResource(id = R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        },
        modifier = modifier
    ) {
        TimePicker(state = pickerState)
    }
}

@Preview
@Composable
private fun ReminderTimeDialogPreview() {
    SubTrackTheme {
        ReminderTimeDialog(
            initialTime = LocalTime.of(9, 0),
            is24Hour = true,
            onConfirm = { _, _ -> },
            onDismiss = {}
        )
    }
}
