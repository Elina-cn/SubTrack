package com.elinacn.subtrack.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.ui.common.CurrencySelector
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme

/**
 * The settings screen. Stateless: it renders [uiState] and reports back through [onEvent].
 *
 * Navigation arrives as lambdas rather than a NavController, so the screen has no opinion about
 * where it sits in the graph and previews without one.
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

    LaunchedEffect(errorText) {
        if (errorText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(message = errorText, duration = SnackbarDuration.Short)
        onEvent(SettingsEvent.DismissError)
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

            // A whole row is the target rather than the text alone, and defaultMinSize keeps it at
            // the accessibility floor even when the label happens to be shorter than that.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToExchangeRates)
                    .defaultMinSize(minHeight = Dimens.MinTouchTarget)
                    .padding(vertical = Dimens.SpacerMedium),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(id = R.string.exchange_rates_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(id = R.string.exchange_rates_row_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    SubTrackTheme {
        SettingsScreen(
            uiState = SettingsUiState(mainCurrency = Currency.USD),
            onEvent = {},
            onNavigateBack = {},
            onNavigateToExchangeRates = {}
        )
    }
}
