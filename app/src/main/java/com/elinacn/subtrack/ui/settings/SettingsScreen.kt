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
import com.elinacn.subtrack.ui.common.reservedUntilKnown
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
        onEvent(SettingsEvent.ReminderPermissionAnswered(activity.canShowNotificationRationale()))
    }

    LaunchedEffect(errorText) {
        if (errorText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(message = errorText, duration = SnackbarDuration.Short)
        onEvent(SettingsEvent.DismissError)
    }

    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            // ON_START is the first one. The observer is added while the destination is already
            // STARTED, so it arrives straight away, a frame after the first composition - where
            // ON_RESUME waits for the 700 ms enter transition to finish (phase 16t). The row needs
            // the rationale answer only after a refusal, but that is exactly the case it would
            // otherwise leave blank.
            //
            // ON_RESUME stays for the way back: without it the row would still read "off" right
            // after the user switched reminders on in the system settings, or answered the
            // permission dialog, which pauses the app without stopping it.
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
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

            ReminderPermissionAction.OPEN_APP_DETAILS -> activity?.openAppDetails()

            ReminderPermissionAction.OPEN_BATTERY_SAVER_SETTINGS ->
                activity?.openBatterySaverSettings()

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
        //
        // The bar insets stay outside the scroll: every row here is a target, and a row sliding
        // under the gesture bar would be half tappable. No imePadding, because nothing on this
        // screen opens a keyboard - the currency is chosen from chips and the theme from a dialog,
        // which brings its own insets.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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

            // Laid out but not drawn until the stored currency is known. Drawn with a guess, the
            // chips would mark one currency as the choice before anyone had read it; left out, the
            // whole screen would jump down when they appeared. The selection handed in meanwhile is
            // never seen, and a tap on the invisible chips goes nowhere.
            val mainCurrency = uiState.mainCurrency
            CurrencySelector(
                selected = mainCurrency ?: Currency.Base,
                onSelect = { currency ->
                    if (mainCurrency != null) onEvent(SettingsEvent.SelectMainCurrency(currency))
                },
                modifier = Modifier.reservedUntilKnown(isKnown = mainCurrency != null)
            )

            Spacer(modifier = Modifier.height(Dimens.SpacerXLarge))

            HorizontalDivider()

            SettingsRow(
                title = stringResource(id = R.string.exchange_rates_title),
                description = stringResource(id = R.string.exchange_rates_row_description),
                onClick = onNavigateToExchangeRates
            )

            HorizontalDivider()

            ReminderSettingsSection(
                permission = uiState.reminderPermission,
                delivery = uiState.reminderDelivery,
                reminderTime = uiState.reminderTime,
                is24HourFormat = uiState.is24HourFormat,
                isTimeEnabled = uiState.isReminderTimeEnabled,
                onRowTapped = { onEvent(SettingsEvent.ReminderRowTapped) },
                onTimeRowTapped = { onEvent(SettingsEvent.ReminderTimeRowTapped) },
                onNoteTapped = { onEvent(SettingsEvent.ReminderNoteTapped) }
            )

            HorizontalDivider()

            SettingsRow(
                title = stringResource(id = R.string.theme_mode_title),
                description = uiState.themeMode?.let { stringResource(id = it.labelId()) },
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
                description = uiState.dynamicColorTextId()?.let { stringResource(id = it) },
                checked = uiState.isDynamicColorEnabled,
                onCheckedChange = { onEvent(SettingsEvent.SetDynamicColor(it)) },
                enabled = uiState.isDynamicColorSupported
            )
        }
    }

    SettingsDialogs(uiState = uiState, onEvent = onEvent)
}

/**
 * The one line of the wallpaper-colours row that changes, or null while it cannot be said yet.
 *
 * Unsupported outranks on and off: below Android 12 the stored value is real but has no effect,
 * and saying "off" for it would be a different claim than the truth. It is also the one answer
 * that needs no stored value, so on those devices the line is right from the first frame.
 */
private fun SettingsUiState.dynamicColorTextId(): Int? = when {
    !isDynamicColorSupported -> R.string.dynamic_color_unsupported
    isDynamicColorEnabled == null -> null
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
