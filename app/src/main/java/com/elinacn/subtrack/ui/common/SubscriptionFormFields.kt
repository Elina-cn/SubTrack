package com.elinacn.subtrack.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The six fields a subscription is made of: name, price, currency, billing period, category, date.
 *
 * **One copy, two screens.** The add sheet and the edit screen draw exactly this, in exactly this
 * order, so the two forms cannot drift into looking or behaving differently. The rules behind them
 * are shared the same way, a layer down:
 * [SubscriptionInput][com.elinacn.subtrack.domain.usecase.SubscriptionInput] decides what is
 * acceptable for both.
 *
 * It decides nothing itself. What is typed goes into [state] untouched and the errors come back in
 * as text; whether a save may proceed is the ViewModel's call, because that depends on whether the
 * entry validated.
 *
 * No padding of its own, and no title: the caller supplies both. A sheet and a screen frame their
 * content differently, and this composable has no opinion about which it is inside.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionFormFields(
    state: SubscriptionFormState,
    nameError: UiText?,
    priceError: UiText?,
    dateError: UiText?,
    onNameEdited: () -> Unit,
    onPriceEdited: () -> Unit,
    onDateEdited: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDatePickerOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = {
                state.name = it
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
            value = state.rawPrice,
            onValueChange = {
                state.rawPrice = it
                if (priceError != null) onPriceEdited()
            },
            label = { Text(stringResource(id = R.string.subscription_price_label)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = priceError != null,
            supportingText = priceError?.let { { Text(it.asString()) } }
        )

        Spacer(modifier = Modifier.height(Dimens.SpacerMedium))

        // Left aligned against the centred column, so the label sits over its chips rather
        // than floating in the middle of the form.
        FieldLabel(text = stringResource(id = R.string.currency_label))
        CurrencySelector(selected = state.currency, onSelect = { state.currency = it })

        Spacer(modifier = Modifier.height(Dimens.SpacerMedium))

        // Beside the currency, because the two of them are what the price above means: what
        // it is in, and how often it is paid. A price with neither is half an answer, and a
        // reader who has just typed one should settle both before moving on.
        FieldLabel(text = stringResource(id = R.string.billing_period_label))
        BillingPeriodSelector(selected = state.billingPeriod, onSelect = { state.billingPeriod = it })

        Spacer(modifier = Modifier.height(Dimens.SpacerMedium))

        // After the two that qualify the price: the category files the subscription and
        // changes nothing else on the form. Before the date, which opens a dialog and ends
        // the sequence.
        FieldLabel(text = stringResource(id = R.string.category_label))
        CategorySelector(selected = state.category, onSelect = { state.category = it })

        Spacer(modifier = Modifier.height(Dimens.SpacerMedium))

        // Read-only: the value only ever comes from the picker, so there is nothing
        // to type and no malformed date to validate. A read-only field does not take
        // taps, hence the transparent layer over it.
        val dateFormatter = rememberDateFormatter()
        val dateText = state.nextPaymentDate?.let(dateFormatter::format).orEmpty()
        val dateLabel = stringResource(id = R.string.next_payment_date_label)
        // The tap layer below covers the field, so the tree only ever sees that one node. It
        // has to carry the whole sentence itself: without this it reports as an unnamed button
        // and a screen reader user is told nothing about what the control is or holds.
        val dateValueText = dateText.ifEmpty { stringResource(id = R.string.date_not_set) }
        val dateDescription = stringResource(
            id = R.string.next_payment_date_description,
            dateLabel,
            // The rejection message is drawn under the field but merged out of the tree with
            // everything else, so it has to be spoken as part of the field's own label.
            dateError?.let { "$dateValueText, ${it.asString()}" } ?: dateValueText
        )
        Box {
            OutlinedTextField(
                value = dateText,
                onValueChange = {},
                readOnly = true,
                label = { Text(dateLabel) },
                placeholder = { Text(stringResource(id = R.string.date_not_set)) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        // Decorative: the label already says what the field is.
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = dateError != null,
                supportingText = dateError?.let { { Text(it.asString()) } }
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .semantics { contentDescription = dateDescription }
                    .clickable(
                        onClickLabel = stringResource(id = R.string.pick_date),
                        onClick = {
                            if (dateError != null) onDateEdited()
                            isDatePickerOpen = true
                        }
                    )
            )
        }
    }

    if (isDatePickerOpen) {
        // DatePicker reports the chosen day as UTC midnight, whatever the device zone is, so the
        // conversion on both sides of it is UTC. Using the system zone here would move the date by
        // a day for anyone west of Greenwich.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.nextPaymentDate?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { isDatePickerOpen = false },
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            state.nextPaymentDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        isDatePickerOpen = false
                    }
                ) {
                    Text(stringResource(id = R.string.save))
                }
            },
            dismissButton = {
                // Clearing lives here rather than as a trailing icon on the field: the field is
                // covered by the tap layer above, so an icon inside it could not be reached.
                TextButton(
                    onClick = {
                        state.nextPaymentDate = null
                        isDatePickerOpen = false
                    }
                ) {
                    Text(stringResource(id = R.string.clear_date))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** The line over a chip row, left aligned and spaced the same way for all three of them. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(Dimens.SpacerSmall))
}

/** The reader's own date format, rebuilt only when the language changes. */
@Composable
private fun rememberDateFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
}

/** A filled-in form, the shape the edit screen opens on. */
@Preview(showBackground = true)
@Composable
private fun SubscriptionFormFieldsPreview() {
    SubTrackTheme {
        SubscriptionFormFields(
            state = SubscriptionFormState(
                name = "Netflix",
                rawPrice = "159.99",
                nextPaymentDate = LocalDate.of(2026, 9, 24)
            ),
            nameError = null,
            priceError = null,
            dateError = null,
            onNameEdited = {},
            onPriceEdited = {},
            onDateEdited = {}
        )
    }
}
