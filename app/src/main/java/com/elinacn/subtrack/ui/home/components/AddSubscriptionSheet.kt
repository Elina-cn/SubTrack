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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.elinacn.subtrack.R
import com.elinacn.subtrack.ui.theme.Dimens
import kotlinx.coroutines.launch

/**
 * Form for a new subscription.
 *
 * Owns what is typed into it - transient view state, which ARCHITECTURE section 3 allows a
 * composable to hold. rememberSaveable so a rotation mid-entry does not throw the text away.
 * Closing the sheet takes the composable out of composition, which clears the fields for the
 * next open without anyone resetting them.
 *
 * The raw price string is handed over untouched; parsing and validation belong to the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubscriptionSheet(
    onSave: (name: String, rawPrice: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable { mutableStateOf("") }
    var rawPrice by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

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
                onValueChange = { name = it },
                label = { Text(stringResource(id = R.string.subscription_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(Dimens.SpacerMedium))

            OutlinedTextField(
                value = rawPrice,
                onValueChange = { rawPrice = it },
                label = { Text(stringResource(id = R.string.subscription_price_label)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(Dimens.SpacerXLarge))

            Button(
                onClick = {
                    onSave(name, rawPrice)
                    // Animate out before leaving composition, otherwise the sheet vanishes.
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) onDismiss()
                    }
                },
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
