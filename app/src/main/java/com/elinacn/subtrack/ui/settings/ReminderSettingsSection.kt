package com.elinacn.subtrack.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.ui.common.SettingsRow
import com.elinacn.subtrack.ui.common.rememberTimeOfDayFormatter
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme
import java.time.LocalTime

/**
 * The reminder row, the reminder time under it, and while reminders are on, a one-line note.
 *
 * The row says whether reminders will come on time as far as the platform can tell. The note is
 * there in every on state because what the platform cannot tell - a manufacturer's battery rules,
 * a cleaner app - is exactly what delayed the testers' reminders (PROGRESS 16w). A null
 * [permission] is a state still being read: the row keeps its line empty and the note waits.
 *
 * The time row sits directly under the reminder row in every state, so it does not move when the
 * note comes and goes. It still shows the stored time while reminders are off, faded and not
 * tappable: the time is kept, only nothing is sent. A null [reminderTime] has not been read yet
 * and leaves the line empty rather than showing 09:00 (ARCHITECTURE section 29).
 */
@Composable
internal fun ReminderSettingsSection(
    permission: ReminderPermissionState?,
    delivery: ReminderDelivery,
    reminderTime: LocalTime?,
    is24HourFormat: Boolean,
    isTimeEnabled: Boolean,
    onRowTapped: () -> Unit,
    onTimeRowTapped: () -> Unit,
    onNoteTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = rememberTimeOfDayFormatter(is24Hour = is24HourFormat)
    Column(modifier = modifier) {
        SettingsRow(
            title = stringResource(id = R.string.reminder_notifications_title),
            description = permission?.let { stringResource(id = it.statusTextId(delivery)) },
            onClick = onRowTapped
        )
        SettingsRow(
            title = stringResource(id = R.string.reminder_time_title),
            description = reminderTime?.let(timeFormatter::format),
            onClick = onTimeRowTapped,
            enabled = isTimeEnabled
        )
        if (permission == ReminderPermissionState.ENABLED) {
            ReminderDeliveryNote(onClick = onNoteTapped)
        }
    }
}

/**
 * The note itself: a whole-width target at the accessibility floor, like the rows around it, with
 * the smaller type that tells it apart from a setting of its own.
 */
@Composable
private fun ReminderDeliveryNote(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = Dimens.MinTouchTarget),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = stringResource(id = R.string.reminder_delivery_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReminderSettingsSectionRestrictedPreview() {
    SubTrackTheme {
        ReminderSettingsSection(
            permission = ReminderPermissionState.ENABLED,
            delivery = ReminderDelivery.UNTIL_OPENED,
            reminderTime = LocalTime.of(9, 0),
            is24HourFormat = true,
            isTimeEnabled = true,
            onRowTapped = {},
            onTimeRowTapped = {},
            onNoteTapped = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReminderSettingsSectionOffPreview() {
    SubTrackTheme {
        ReminderSettingsSection(
            permission = ReminderPermissionState.SETTINGS_ONLY,
            delivery = ReminderDelivery.ON_TIME,
            reminderTime = LocalTime.of(20, 30),
            is24HourFormat = false,
            isTimeEnabled = false,
            onRowTapped = {},
            onTimeRowTapped = {},
            onNoteTapped = {}
        )
    }
}
