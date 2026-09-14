package com.elinacn.subtrack.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.ui.common.SubscriptionFormFields
import com.elinacn.subtrack.ui.common.UiText
import com.elinacn.subtrack.ui.common.rememberSubscriptionFormState
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme
import java.time.LocalDate

/**
 * Form for a new subscription.
 *
 * The fields themselves are [SubscriptionFormFields], shared with the edit screen; this composable
 * is the sheet around them - the title, the scrolling, and the save button that stays put. What is
 * typed lives in a [rememberSubscriptionFormState] here, so it survives a rotation mid-entry and
 * dies with the sheet, which is what makes the next opening start empty.
 *
 * It does not decide anything: the raw strings go up untouched, and whether the sheet may close is
 * the ViewModel's call, because that depends on whether the entry validated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubscriptionSheet(
    sheetState: SheetState,
    nameError: UiText?,
    priceError: UiText?,
    dateError: UiText?,
    onSave: (
        name: String,
        rawPrice: String,
        currency: Currency,
        nextPaymentDate: LocalDate?,
        category: SubscriptionCategory,
        billingPeriod: BillingPeriod
    ) -> Unit,
    onNameEdited: () -> Unit,
    onPriceEdited: () -> Unit,
    onDateEdited: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val form = rememberSubscriptionFormState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        // Two siblings in the sheet's own ColumnScope: a form that scrolls, and a save button that
        // does not. weight(fill = false) is what splits them - it hands the scrolling half an upper
        // bound of whatever height is left over, with a minimum of zero, so a short form still
        // measures to its own height and the sheet stays content-sized. Plain weight(1f) would set
        // that minimum to the full leftover height and make the sheet fill the screen every time.
        //
        // The button is measured without a weight, so its height is reserved before the form gets
        // the remainder. That is the whole point: on a 360dp screen with the keyboard up the form
        // no longer pushes the button past the bottom edge, because the button was never competing
        // for that space. ModalBottomSheet already applies imePadding() to its root box, so the
        // leftover height shrinks with the keyboard on its own.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.SheetPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.add_subscription_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(Dimens.SpacerLarge))

            SubscriptionFormFields(
                state = form,
                nameError = nameError,
                priceError = priceError,
                dateError = dateError,
                onNameEdited = onNameEdited,
                onPriceEdited = onPriceEdited,
                onDateEdited = onDateEdited
            )
        }

        // Outside the scrolling column, so it holds its place at the bottom of the sheet however
        // far the form is scrolled. The gap above it was a Spacer inside the form before; as
        // padding here it stays a constant separation instead of scrolling away.
        Button(
            onClick = {
                onSave(
                    form.name,
                    form.rawPrice,
                    form.currency,
                    form.nextPaymentDate,
                    form.category,
                    form.billingPeriod
                )
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SheetPadding)
                .padding(top = Dimens.SpacerXLarge, bottom = Dimens.SheetBottomPadding),
            shape = RoundedCornerShape(Dimens.CardCorner)
        ) {
            Text(stringResource(id = R.string.save))
        }
    }
}

/** Field errors as they appear after a rejected save. */
@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun AddSubscriptionSheetErrorPreview() {
    SubTrackTheme {
        AddSubscriptionSheet(
            sheetState = rememberModalBottomSheetState(),
            nameError = UiText.Resource(R.string.error_name_empty),
            priceError = UiText.Resource(R.string.error_price_invalid),
            dateError = null,
            onSave = { _, _, _, _, _, _ -> },
            onNameEdited = {},
            onPriceEdited = {},
            onDateEdited = {},
            onDismiss = {}
        )
    }
}
