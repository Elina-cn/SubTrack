package com.elinacn.subtrack.ui.settings.rates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.ui.common.UiText
import com.elinacn.subtrack.ui.common.reservedUntilKnown
import java.text.DateFormat
import java.util.Date

/**
 * "Last edited on ..." once the user has touched the rates, and a warning that the shipped numbers
 * are a guess until then.
 *
 * **Both sentences are always laid out, one on top of the other, and only one is drawn.** The line
 * is therefore as tall as the taller of the two whichever is showing, and the first save - which
 * swaps the two-line warning for a one-line date - no longer pulls every box up by a line. Phase
 * 16t measured that as a 42 px jump right under the user's finger. Until the store answers neither
 * is drawn (ARCHITECTURE section 29).
 *
 * The date is formatted here because it needs a Locale, which domain does not carry. With nothing
 * stored, the hidden date line is measured with the epoch in its place; it is never seen.
 */
@Composable
internal fun RatesUpdatedLine(isLoaded: Boolean, updatedAt: Long?, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    }
    val lastEdited = stringResource(id = R.string.rates_updated_at, format.format(Date(updatedAt ?: 0L)))

    Box(modifier = modifier) {
        Text(
            text = stringResource(id = R.string.rates_never_edited),
            modifier = Modifier.reservedUntilKnown(isKnown = isLoaded && updatedAt == null),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = lastEdited,
            modifier = Modifier.reservedUntilKnown(isKnown = isLoaded && updatedAt != null),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * One rate box.
 *
 * The keyboard's action key walks the form: "next" on every box but the last, and on the last it
 * does exactly what the Save button does - [onDone] - so a user who typed with the keyboard up
 * never has to scroll down to a button it is covering.
 *
 * [focusRequester] and [bringIntoViewRequester] let the screen point at this box after a rejected
 * save. The requester covers the whole field, error line included, so scrolling to it brings the
 * message into view along with the box.
 */
@Composable
internal fun RateField(
    currency: Currency,
    text: String?,
    error: UiText?,
    isLast: Boolean,
    focusRequester: FocusRequester,
    bringIntoViewRequester: BringIntoViewRequester,
    onEdit: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLoaded = text != null
    OutlinedTextField(
        // Until the store answers the box is laid out but not drawn, and it holds a stand-in
        // rather than nothing: an empty box puts its label in the middle, and the label would then
        // be seen sliding to the border as the rate arrived.
        value = text ?: LAYOUT_STAND_IN,
        onValueChange = onEdit,
        label = {
            Text(stringResource(id = R.string.rate_field_label, currency.symbol, Currency.Base.symbol))
        },
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .focusRequester(focusRequester)
            .reservedUntilKnown(isKnown = isLoaded),
        // Disabled while hidden, so a tap cannot focus a box nobody can see.
        enabled = isLoaded,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = if (isLast) ImeAction.Done else ImeAction.Next
        ),
        // Only Done is taken over. Next keeps its default, which moves focus to the next box.
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it.asString()) } }
    )
}

/**
 * Occupies a rate box while it is hidden, so the box is laid out as it will be once filled. Never
 * drawn and never announced.
 */
private const val LAYOUT_STAND_IN = "0"
