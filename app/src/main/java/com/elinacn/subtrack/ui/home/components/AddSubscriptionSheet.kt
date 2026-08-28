package com.elinacn.subtrack.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.elinacn.subtrack.R
import com.elinacn.subtrack.ui.common.UiText
import com.elinacn.subtrack.ui.theme.Dimens

/**
 * Form for a new subscription.
 *
 * Owns what is typed into it - transient view state, which ARCHITECTURE section 3 allows a
 * composable to hold. rememberSaveable so a rotation mid-entry does not throw the text away.
 *
 * It does not decide anything: the raw strings go up untouched, and whether the sheet may close
 * is the ViewModel's call, because that depends on whether the entry validated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubscriptionSheet(
    sheetState: SheetState,
    nameError: UiText?,
    priceError: UiText?,
    onSave: (name: String, rawPrice: String) -> Unit,
    onNameEdited: () -> Unit,
    onPriceEdited: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable { mutableStateOf("") }
    var rawPrice by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SheetPadding)
                .padding(bottom = Dimens.SheetBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.add_subscription_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(Dimens.SpacerLarge))

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    // Drop the error as soon as the field changes; leaving it up would keep
                    // contradicting what is now on screen.
                    if (nameError != null) onNameEdited()
                },
                label = { Text(stringResource(id = R.string.subscription_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it.asString()) } }
            )

            Spacer(modifier = Modifier.height(Dimens.SpacerMedium))

            OutlinedTextField(
                value = rawPrice,
                onValueChange = {
                    rawPrice = it
                    if (priceError != null) onPriceEdited()
                },
                label = { Text(stringResource(id = R.string.subscription_price_label)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = priceError != null,
                supportingText = priceError?.let { { Text(it.asString()) } }
            )

            Spacer(modifier = Modifier.height(Dimens.SpacerXLarge))

            Button(
                onClick = { onSave(name, rawPrice) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimens.CardCorner)
            ) {
                Text(stringResource(id = R.string.save))
            }
        }
    }
}
