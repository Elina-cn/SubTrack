package com.elinacn.subtrack.ui.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.ui.common.DelayedLoadingIndicator
import com.elinacn.subtrack.ui.common.EmptyState
import com.elinacn.subtrack.ui.common.SubscriptionFormFields
import com.elinacn.subtrack.ui.common.rememberSubscriptionFormState
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme
import java.time.LocalDate

/**
 * Changing a subscription that is already stored.
 *
 * A screen rather than a second bottom sheet: an edit is something the user can be halfway
 * through, and a sheet leaves both the back stack and that half-finished state ambiguous - a
 * scrim tap, a drag and the back gesture all mean "close", and none of them is a decision about
 * the edit. A destination has one way out and the system back button already means it.
 *
 * **No delete here.** Swiping a row away already deletes, with an undo attached to it. A second
 * door to the same room would need its own answer to "and can I take that back", and two undo
 * behaviours for one action is worse than one way of deleting.
 *
 * The fields are [SubscriptionFormFields], the same ones the add sheet draws. The form is seeded
 * from the stored row - including the **anchor** date, which is the day the user chose rather than
 * the advanced day the card counts towards (ARCHITECTURE §17).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSubscriptionScreen(
    uiState: EditSubscriptionUiState,
    onEvent: (EditSubscriptionEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorText = uiState.errorMessage?.asString()

    // The write landed, so the screen's one remaining job is to get out of the way. Nothing is
    // cleared afterwards: this destination is leaving the back stack either way.
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    LaunchedEffect(errorText) {
        if (errorText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(message = errorText, duration = SnackbarDuration.Short)
        onEvent(EditSubscriptionEvent.DismissError)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.edit_subscription_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.SheetPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DelayedLoadingIndicator(isLoading = uiState.isLoading)

            if (uiState.isMissing) {
                // The list glyph, the same one an empty list uses: this is a row that is not
                // there, and it adds no name to the icon set phase 16 has to narrow down.
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = stringResource(id = R.string.subscription_missing_title),
                    message = stringResource(id = R.string.subscription_missing_message)
                )
            }

            val loaded = uiState.subscription
            if (loaded != null) {
                // Seeded once, keyed by the row's id: see rememberSubscriptionFormState.
                val form = rememberSubscriptionFormState(loaded)

                SubscriptionFormFields(
                    state = form,
                    nameError = uiState.nameError,
                    priceError = uiState.priceError,
                    dateError = uiState.dateError,
                    onNameEdited = { onEvent(EditSubscriptionEvent.ClearNameError) },
                    onPriceEdited = { onEvent(EditSubscriptionEvent.ClearPriceError) },
                    onDateEdited = { onEvent(EditSubscriptionEvent.ClearDateError) }
                )

                // Inside the scrolling column, unlike the sheet's: a screen has the whole height to
                // work with, so the button does not have to be reserved out of a shrinking sheet.
                Button(
                    onClick = {
                        onEvent(
                            EditSubscriptionEvent.Save(
                                name = form.name,
                                rawPrice = form.rawPrice,
                                currency = form.currency,
                                nextPaymentDate = form.nextPaymentDate,
                                category = form.category,
                                billingPeriod = form.billingPeriod
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.SpacerXLarge, bottom = Dimens.SheetBottomPadding),
                    shape = RoundedCornerShape(Dimens.CardCorner)
                ) {
                    Text(stringResource(id = R.string.save))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditSubscriptionScreenPreview() {
    SubTrackTheme {
        EditSubscriptionScreen(
            uiState = EditSubscriptionUiState(
                subscription = Subscription(
                    id = 1,
                    name = "Netflix",
                    price = Money.of(159, 99),
                    currency = Currency.TRY,
                    billingPeriod = BillingPeriod.MONTHLY,
                    nextPaymentDate = LocalDate.of(2026, 9, 24),
                    category = SubscriptionCategory.ENTERTAINMENT,
                    iconKey = null,
                    createdAt = 0
                ),
                isLoading = false
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EditSubscriptionScreenMissingPreview() {
    SubTrackTheme {
        EditSubscriptionScreen(
            uiState = EditSubscriptionUiState(isLoading = false, isMissing = true),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}
