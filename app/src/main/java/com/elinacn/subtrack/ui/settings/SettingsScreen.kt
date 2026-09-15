package com.elinacn.subtrack.ui.settings

import android.Manifest
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.ThemeMode
import com.elinacn.subtrack.ui.common.CurrencySelector
import com.elinacn.subtrack.ui.common.SettingsRow
import com.elinacn.subtrack.ui.common.SettingsSwitchRow
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme

/**
 * The settings screen. Stateless: it renders [uiState] and reports back through [onEvent].
 *
 * Navigation arrives as lambdas rather than a NavController, so the screen has no opinion about
 * where it sits in the graph and previews without one.
 *
 * The reminder row is the one place the screen touches the platform, because asking for a
 * permission and opening the system settings both need an Activity. It still decides nothing:
 * it hands the ViewModel the one fact only an Activity knows and carries out what comes back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToExchangeRates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorText = uiState.errorMessage?.asString()
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Registered unconditionally. A launcher created inside an if would already be gone by the
    // time the system handed the answer back.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onEvent(SettingsEvent.RefreshReminderPermission(activity.canShowNotificationRationale()))
    }

    LaunchedEffect(errorText) {
        if (errorText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(message = errorText, duration = SnackbarDuration.Short)
        onEvent(SettingsEvent.DismissError)
    }

    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            // Coming back from the system settings is the case that matters: without this the row
            // would still read "off" right after the user switched reminders on.
            if (event == Lifecycle.Event.ON_RESUME) {
                onEvent(
                    SettingsEvent.RefreshReminderPermission(activity.canShowNotificationRationale())
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.pendingReminderAction) {
        when (uiState.pendingReminderAction) {
            ReminderPermissionAction.REQUEST_PERMISSION ->
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

            ReminderPermissionAction.OPEN_SYSTEM_SETTINGS ->
                activity?.openNotificationSettings()

            null -> return@LaunchedEffect
        }
        onEvent(SettingsEvent.ReminderActionHandled)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            // AutoMirrored: the arrow has to point the other way in a
                            // right-to-left layout, and the plain ArrowBack is deprecated for
                            // exactly that reason.
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        // Scrollable so a scaled-up font cannot push the selector off a short screen.
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding)
        ) {
            Text(
                text = stringResource(id = R.string.main_currency_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(Dimens.SpacerSmall))

            Text(
                text = stringResource(id = R.string.main_currency_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.SpacerMedium))

            CurrencySelector(
                selected = uiState.mainCurrency,
                onSelect = { onEvent(SettingsEvent.SelectMainCurrency(it)) }
            )

            Spacer(modifier = Modifier.height(Dimens.SpacerXLarge))

            HorizontalDivider()

            SettingsRow(
                title = stringResource(id = R.string.exchange_rates_title),
                description = stringResource(id = R.string.exchange_rates_row_description),
                onClick = onNavigateToExchangeRates
            )

            HorizontalDivider()

            SettingsRow(
                title = stringResource(id = R.string.reminder_notifications_title),
                description = stringResource(id = uiState.reminderPermission.statusTextId()),
                onClick = { onEvent(SettingsEvent.ReminderRowTapped) }
            )

            HorizontalDivider()

            SettingsRow(
                title = stringResource(id = R.string.theme_mode_title),
                description = stringResource(id = uiState.themeMode.labelId()),
                onClick = { onEvent(SettingsEvent.ThemeRowTapped) }
            )

            HorizontalDivider()

            // Shown below Android 12 rather than hidden, and switched off rather than removed.
            // A setting that simply is not there cannot say why: a reader who has met Material You
            // elsewhere would be left deciding whether this app lacks it or their install is
            // broken. The description answers that, which is the same choice the reminder row
            // makes when it says "only the system settings can turn this on".
            SettingsSwitchRow(
                title = stringResource(id = R.string.dynamic_color_title),
                description = stringResource(id = uiState.dynamicColorTextId()),
                checked = uiState.isDynamicColorEnabled,
                onCheckedChange = { onEvent(SettingsEvent.SetDynamicColor(it)) },
                enabled = uiState.isDynamicColorSupported
            )
        }
    }

    if (uiState.isThemeDialogVisible) {
        ThemeModeDialog(
            selected = uiState.themeMode,
            onSelect = { onEvent(SettingsEvent.SelectThemeMode(it)) },
            onDismiss = { onEvent(SettingsEvent.ThemeDialogDismissed) }
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

/**
 * The one line of the wallpaper-colours row that changes.
 *
 * Unsupported outranks on and off: below Android 12 the stored value is real but has no effect,
 * and saying "off" for it would be a different claim than the truth.
 */
private fun SettingsUiState.dynamicColorTextId(): Int = when {
    !isDynamicColorSupported -> R.string.dynamic_color_unsupported
    isDynamicColorEnabled -> R.string.dynamic_color_on
    else -> R.string.dynamic_color_off
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    SubTrackTheme {
        SettingsScreen(
            uiState = SettingsUiState(
                mainCurrency = Currency.USD,
                themeMode = ThemeMode.DARK,
                isDynamicColorEnabled = true,
                isDynamicColorSupported = true,
                reminderPermission = ReminderPermissionState.CAN_REQUEST
            ),
            onEvent = {},
            onNavigateBack = {},
            onNavigateToExchangeRates = {}
        )
    }
}
