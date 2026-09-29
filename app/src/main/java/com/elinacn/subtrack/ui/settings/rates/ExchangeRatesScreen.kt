package com.elinacn.subtrack.ui.settings.rates

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.ui.common.UiText
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme
import kotlinx.coroutines.launch

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
    val snackbarScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val editable = ExchangeRatesViewModel.editableCurrencies
    val focusRequesters = remember { editable.associateWith { FocusRequester() } }
    val bringIntoViewRequesters = remember { editable.associateWith { BringIntoViewRequester() } }
    val errorText = uiState.errorMessage?.asString()
    val noticeText = uiState.notice?.let { stringResource(id = it.messageId()) }

    LaunchedEffect(errorText) {
        if (errorText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(message = errorText, duration = SnackbarDuration.Short)
        onEvent(ExchangeRatesEvent.DismissError)
    }

    // A write went through. The keyboard has nothing left to do and a cursor left in a box reads
    // as "still editing", so both go, and the message says what happened. It is shown from its own
    // scope so the notice is handed back at once and a second one replaces the first.
    LaunchedEffect(noticeText) {
        if (noticeText == null) return@LaunchedEffect
        focusManager.clearFocus()
        keyboardController?.hide()
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarScope.launch {
            snackbarHostState.showSnackbar(message = noticeText, duration = SnackbarDuration.Short)
        }
        onEvent(ExchangeRatesEvent.NoticeShown)
    }

    // A rejected save points at the first bad box. Focus first, then one frame so the error line
    // under it is laid out, then scroll: the requester covers the message too, so it comes into
    // view with the box instead of staying below the fold on a short screen.
    LaunchedEffect(uiState.fieldToFocus) {
        val target = uiState.fieldToFocus ?: return@LaunchedEffect
        focusRequesters.getValue(target).requestFocus()
        withFrameNanos { }
        bringIntoViewRequesters.getValue(target).bringIntoView()
        onEvent(ExchangeRatesEvent.FieldFocused)
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
                .fillMaxSize()
                // Outside the scroll on purpose, unlike the home list. This screen ends in two
                // buttons, and a button that slides under the gesture bar is only half tappable -
                // there is nothing to gain here from letting content run past the edge.
                .padding(paddingValues)
                // Says the bar insets above are already spent, so imePadding below measures the
                // keyboard from where this column actually ends rather than from the window edge.
                // Without it the two stack and the keyboard leaves a dead strip above it.
                .consumeWindowInsets(paddingValues)
                // Before verticalScroll, so the keyboard shrinks the scrolling viewport instead of
                // covering it. This is the line ARCHITECTURE section 16 could not use: with the
                // window still fitting the decor, WindowInsets.ime read zero on every device, and
                // padding by zero does nothing. Edge-to-edge is what makes it a real number.
                .imePadding()
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

            RatesUpdatedLine(isLoaded = uiState.isLoaded, updatedAt = uiState.updatedAt)

            Spacer(modifier = Modifier.height(Dimens.SpacerLarge))

            editable.forEach { currency ->
                RateField(
                    currency = currency,
                    text = uiState.rateTexts?.get(currency),
                    error = uiState.fieldErrors[currency],
                    isLast = currency == editable.last(),
                    focusRequester = focusRequesters.getValue(currency),
                    bringIntoViewRequester = bringIntoViewRequesters.getValue(currency),
                    onEdit = { onEvent(ExchangeRatesEvent.RateEdited(currency, it)) },
                    onDone = {
                        // With nothing to save, the action key only puts the keyboard away.
                        if (uiState.isSaveEnabled) {
                            onEvent(ExchangeRatesEvent.Save)
                        } else {
                            focusManager.clearFocus()
                        }
                    }
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
                enabled = uiState.isSaveEnabled,
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
                enabled = uiState.isResetEnabled,
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

/** The message each kind of finished write shows. */
private fun RatesNotice.messageId(): Int = when (this) {
    RatesNotice.SAVED -> R.string.rates_saved
    RatesNotice.RESTORED -> R.string.rates_restored
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ExchangeRatesScreenPreview() {
    SubTrackTheme {
        ExchangeRatesScreen(
            uiState = ExchangeRatesUiState(
                rateTexts = mapOf(
                    Currency.USD to "42.85",
                    Currency.EUR to "46.2",
                    Currency.GBP to "53.9"
                ),
                isResetEnabled = true
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
                rateTexts = mapOf(
                    Currency.USD to "0",
                    Currency.EUR to "46.2",
                    Currency.GBP to "53.9"
                ),
                fieldErrors = mapOf(
                    Currency.USD to UiText.Resource(R.string.error_rate_not_positive)
                ),
                updatedAt = 0L,
                isSaveEnabled = true,
                isResetEnabled = true
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}
