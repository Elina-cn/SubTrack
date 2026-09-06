package com.elinacn.subtrack.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
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
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.ui.common.BillingPeriodSelector
import com.elinacn.subtrack.ui.common.CategorySelector
import com.elinacn.subtrack.ui.common.CurrencySelector
import com.elinacn.subtrack.ui.common.UiText
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
    var name by rememberSaveable { mutableStateOf("") }
    var rawPrice by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable(stateSaver = CurrencySaver) { mutableStateOf(Currency.Base) }
    var nextPaymentDate by rememberSaveable(stateSaver = LocalDateSaver) {
        mutableStateOf<LocalDate?>(null)
    }
    var category by rememberSaveable(stateSaver = CategorySaver) {
        mutableStateOf(SubscriptionCategory.OTHER)
    }
    var billingPeriod by rememberSaveable(stateSaver = BillingPeriodSaver) {
        mutableStateOf(BillingPeriod.MONTHLY)
    }
    var isDatePickerOpen by rememberSaveable { mutableStateOf(false) }

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

            Spacer(modifier = Modifier.height(Dimens.SpacerMedium))

            // Left aligned against the centred column, so the label sits over its chips rather
            // than floating in the middle of the sheet.
            Text(
                text = stringResource(id = R.string.currency_label),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.SpacerSmall))
            CurrencySelector(selected = currency, onSelect = { currency = it })

            Spacer(modifier = Modifier.height(Dimens.SpacerMedium))

            // Beside the currency, because the two of them are what the price above means: what
            // it is in, and how often it is paid. A price with neither is half an answer, and a
            // reader who has just typed one should settle both before moving on.
            Text(
                text = stringResource(id = R.string.billing_period_label),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.SpacerSmall))
            BillingPeriodSelector(selected = billingPeriod, onSelect = { billingPeriod = it })

            Spacer(modifier = Modifier.height(Dimens.SpacerMedium))

            // After the two that qualify the price: the category files the subscription and
            // changes nothing else on the form. Before the date, which opens a dialog and ends
            // the sequence.
            Text(
                text = stringResource(id = R.string.category_label),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.SpacerSmall))
            CategorySelector(selected = category, onSelect = { category = it })

            Spacer(modifier = Modifier.height(Dimens.SpacerMedium))

            // Read-only: the value only ever comes from the picker, so there is nothing
            // to type and no malformed date to validate. A read-only field does not take
            // taps, hence the transparent layer over it.
            val dateFormatter = rememberDateFormatter()
            val dateText = nextPaymentDate?.let(dateFormatter::format).orEmpty()
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

        // Outside the scrolling column, so it holds its place at the bottom of the sheet however
        // far the form is scrolled. The gap above it was a Spacer inside the form before; as
        // padding here it stays a constant separation instead of scrolling away.
        Button(
            onClick = { onSave(name, rawPrice, currency, nextPaymentDate, category, billingPeriod) },
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

    if (isDatePickerOpen) {
        // DatePicker reports the chosen day as UTC midnight, whatever the device zone is, so the
        // conversion on both sides of it is UTC. Using the system zone here would move the date by
        // a day for anyone west of Greenwich.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = nextPaymentDate?.atStartOfDay(ZoneOffset.UTC)
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
                            nextPaymentDate = Instant.ofEpochMilli(millis)
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
                        nextPaymentDate = null
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

/** The reader's own date format, rebuilt only when the language changes. */
@Composable
private fun rememberDateFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
}

/**
 * A date survives a rotation as its epoch day. Saving null stores nothing, and the field simply
 * comes back to its initial null - which is what an unset date is.
 */
private val LocalDateSaver = Saver<LocalDate?, Long>(
    save = { it?.toEpochDay() },
    restore = { LocalDate.ofEpochDay(it) }
)

/**
 * Enums cannot go into a Bundle on their own, so the choice is stored by its ISO code and looked
 * up again on the way back.
 */
private val CurrencySaver = Saver<Currency, String>(
    save = { it.name },
    restore = { Currency.fromCode(it) }
)

/**
 * Same shape as [CurrencySaver]: the name survives a rotation, not the constant itself.
 *
 * An unknown name falls back to OTHER rather than throwing, for the reason the mapper does the
 * same - a saved bundle can outlive the build that wrote it. See ARCHITECTURE section 12.
 */
private val CategorySaver = Saver<SubscriptionCategory, String>(
    save = { it.name },
    restore = { name ->
        SubscriptionCategory.entries.firstOrNull { it.name == name } ?: SubscriptionCategory.OTHER
    }
)

/** Same again for the billing period, falling back to the one the form opens on. */
private val BillingPeriodSaver = Saver<BillingPeriod, String>(
    save = { it.name },
    restore = { name ->
        BillingPeriod.entries.firstOrNull { it.name == name } ?: BillingPeriod.MONTHLY
    }
)

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
