package com.elinacn.subtrack.ui.settings.rates

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.ui.common.UiText
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme
import java.text.DateFormat
import java.util.Date

/**
 * Lets the user correct the fixed exchange rates by hand.
 *
 * v1.0 has no network, so this screen is what stands in for automatic updates: the shipped rates
 * are a guess with a date on them, and this is where that guess gets replaced. Stateless with
 * respect to data - it renders [uiState] and reports back through [onEvent].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeRatesScreen(
    uiState: ExchangeRatesUiState,
    onEvent: (ExchangeRatesEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorText = uiState.errorMessage?.asString()

    LaunchedEffect(errorText) {
        if (errorText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(message = errorText, duration = SnackbarDuration.Short)
        onEvent(ExchangeRatesEvent.DismissError)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.exchange_rates_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                // The three fields plus the keyboard do not fit a short screen at a large font
                // scale; scrolling is what keeps the save button reachable.
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding)
        ) {
            Text(
                // Symbols here too, for the same reason as every amount in the app: one mark for
                // one currency, wherever the reader meets it. A screen that names the anchor "TRY"
                // and then totals in "₺" is asking the reader to hold two names for one thing.
                text = stringResource(id = R.string.exchange_rates_description, Currency.Base.symbol),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(Dimens.SpacerSmall))

            Text(
                text = uiState.updatedAtText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(Dimens.SpacerLarge))

            ExchangeRatesViewModel.editableCurrencies.forEach { currency ->
                val error = uiState.fieldErrors[currency]
                OutlinedTextField(
                    value = uiState.drafts[currency].orEmpty(),
                    onValueChange = { onEvent(ExchangeRatesEvent.RateEdited(currency, it)) },
                    label = {
                        Text(
                            stringResource(
                                id = R.string.rate_field_label,
                                currency.symbol,
                                Currency.Base.symbol
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it.asString()) } }
                )

                Spacer(modifier = Modifier.height(Dimens.SpacerMedium))
            }

            // Shown but not editable: every other rate is quoted against it, so a TRY of anything
            // but one would make the whole table meaningless.
            Text(
                text = stringResource(id = R.string.rate_anchor_note, Currency.Base.symbol),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(Dimens.SpacerXLarge))

            Button(
                onClick = { onEvent(ExchangeRatesEvent.Save) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimens.CardCorner)
            ) {
                Text(stringResource(id = R.string.save))
            }

            Spacer(modifier = Modifier.height(Dimens.SpacerMedium))

            OutlinedButton(
                onClick = { onEvent(ExchangeRatesEvent.ShowResetConfirmation) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimens.CardCorner)
            ) {
                Text(stringResource(id = R.string.reset_rates))
            }
        }
    }

    if (uiState.isResetConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { onEvent(ExchangeRatesEvent.DismissResetConfirmation) },
            title = { Text(stringResource(id = R.string.reset_rates_confirm_title)) },
            text = { Text(stringResource(id = R.string.reset_rates_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { onEvent(ExchangeRatesEvent.ConfirmReset) }) {
                    Text(stringResource(id = R.string.reset_rates_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(ExchangeRatesEvent.DismissResetConfirmation) }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }
}

/**
 * "Last edited on ..." once the user has touched the rates, and a warning that the shipped numbers
 * are a guess until then. The date is formatted here because it needs a Locale, which domain does
 * not carry.
 */
@Composable
private fun ExchangeRatesUiState.updatedAtText(): String {
    val updated = updatedAt ?: return stringResource(id = R.string.rates_never_edited)
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    }
    return stringResource(id = R.string.rates_updated_at, format.format(Date(updated)))
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ExchangeRatesScreenPreview() {
    SubTrackTheme {
        ExchangeRatesScreen(
            uiState = ExchangeRatesUiState(
                drafts = mapOf(
                    Currency.USD to "42.85",
                    Currency.EUR to "46.2",
                    Currency.GBP to "53.9"
                )
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}

/** A rejected entry, with the message under the field it belongs to. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ExchangeRatesScreenErrorPreview() {
    SubTrackTheme {
        ExchangeRatesScreen(
            uiState = ExchangeRatesUiState(
                drafts = mapOf(
                    Currency.USD to "0",
                    Currency.EUR to "46.2",
                    Currency.GBP to "53.9"
                ),
                fieldErrors = mapOf(
                    Currency.USD to UiText.Resource(R.string.error_rate_not_positive)
                ),
                updatedAt = 0L
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}
