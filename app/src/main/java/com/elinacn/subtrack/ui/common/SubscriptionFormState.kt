package com.elinacn.subtrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.domain.usecase.SubscriptionInput
import java.math.BigDecimal
import java.time.LocalDate

/**
 * What a subscription form is holding at the moment.
 *
 * Transient view state, which ARCHITECTURE §5 leaves to the composable: these six values belong to
 * whoever is typing them and mean nothing to the rest of the app until a save is asked for. The
 * ViewModel is handed the raw strings and decides; nothing here validates anything.
 *
 * One holder rather than six `rememberSaveable` calls per screen, because there are two screens
 * now: the add sheet and the edit screen. Six savers copied into both is six chances for them to
 * come apart.
 *
 * The price stays a **String**. It is what was typed, not what it means - "159," is a legal thing
 * to be in the middle of typing and no `Money` can hold it (§6).
 */
@Stable
class SubscriptionFormState(
    name: String = "",
    rawPrice: String = "",
    currency: Currency = Currency.Base,
    nextPaymentDate: LocalDate? = null,
    category: SubscriptionCategory = SubscriptionCategory.OTHER,
    billingPeriod: BillingPeriod = BillingPeriod.MONTHLY
) {
    var name by mutableStateOf(name)
    var rawPrice by mutableStateOf(rawPrice)
    var currency by mutableStateOf(currency)
    var nextPaymentDate by mutableStateOf(nextPaymentDate)
    var category by mutableStateOf(category)
    var billingPeriod by mutableStateOf(billingPeriod)

    companion object {

        /**
         * Survives a rotation as six strings.
         *
         * All six on purpose: `listSaver` refuses a null entry, and an empty string is a shape a
         * Bundle already knows. So an unset date is written as `""` and read back as no date -
         * which is what an unset date is.
         *
         * Enums cannot go into a Bundle on their own either, so each is stored by its name and
         * looked up again on the way back; an unknown name falls back to the value the form opens
         * on rather than throwing, because a saved bundle can outlive the build that wrote it
         * (§12).
         */
        val Saver: Saver<SubscriptionFormState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.name,
                    state.rawPrice,
                    state.currency.name,
                    state.nextPaymentDate?.toEpochDay()?.toString().orEmpty(),
                    state.category.name,
                    state.billingPeriod.name
                )
            },
            restore = { saved ->
                SubscriptionFormState(
                    name = saved[0],
                    rawPrice = saved[1],
                    currency = Currency.fromCode(saved[2]),
                    nextPaymentDate = saved[3].toLongOrNull()?.let(LocalDate::ofEpochDay),
                    category = SubscriptionCategory.entries
                        .firstOrNull { it.name == saved[4] } ?: SubscriptionCategory.OTHER,
                    billingPeriod = BillingPeriod.entries
                        .firstOrNull { it.name == saved[5] } ?: BillingPeriod.MONTHLY
                )
            }
        )
    }
}

/** An empty form, kept across a rotation mid-entry. */
@Composable
fun rememberSubscriptionFormState(): SubscriptionFormState =
    rememberSaveable(saver = SubscriptionFormState.Saver) { SubscriptionFormState() }

/**
 * A form already filled in with what is stored.
 *
 * Seeded from [subscription] once and then left alone: what resets the form is the subscription's
 * **id**, not the subscription, so a row re-emitted from the database - which happens the moment
 * this form's own save lands - cannot overwrite what the user is in the middle of typing.
 *
 * **The stored date goes in as it is.** It is the anchor the user chose, not the advanced date the
 * card counts towards; showing the advanced one would offer to save a date nobody entered
 * (ARCHITECTURE §17).
 */
@Composable
fun rememberSubscriptionFormState(subscription: Subscription): SubscriptionFormState =
    rememberSaveable(subscription.id, saver = SubscriptionFormState.Saver) {
        SubscriptionFormState(
            name = subscription.name,
            rawPrice = subscription.price.asRawInput(),
            currency = subscription.currency,
            nextPaymentDate = subscription.nextPaymentDate,
            category = subscription.category,
            billingPeriod = subscription.billingPeriod
        )
    }

/**
 * A stored amount written back as something the price field can hold and the parser can read.
 *
 * A plain point and always two decimals: the field's own label offers "159.99" as the example in
 * both languages, and this is the one place in the app where an amount is put back into an
 * **editable** field rather than displayed. Locale-aware money goes through
 * [MoneyFormatter][com.elinacn.subtrack.ui.common.MoneyFormatter], which writes a currency symbol
 * and grouping separators - neither of which the parser accepts back.
 *
 * BigDecimal, not arithmetic on a Double: the digits that went in have to come back out exactly.
 */
private fun Money.asRawInput(): String =
    BigDecimal.valueOf(cents, SubscriptionInput.MINOR_UNIT_DIGITS).toPlainString()
