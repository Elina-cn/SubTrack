package com.elinacn.subtrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import java.util.Currency as PlatformCurrency

/**
 * Writes amounts the way the reader's language writes money.
 *
 * This lives in the UI layer because it needs a Locale, and the domain stays free of platform
 * APIs. The value itself never changes shape: it goes in as whole minor units and only its
 * rendering is locale dependent, so a Turkish reader sees "159,99 ₺" and an English one "$12.99"
 * for exactly the same stored numbers.
 */
class MoneyFormatter(private val locale: Locale) {

    // NumberFormat is expensive to build and not thread safe. Composition is single threaded, and
    // this object never leaves it, so a plain map is enough.
    private val formats = mutableMapOf<Currency, NumberFormat>()

    /** Renders [amount], which is assumed to be denominated in [currency]. */
    fun format(amount: Money, currency: Currency): String =
        formatFor(currency).format(BigDecimal.valueOf(amount.cents, MINOR_UNIT_DIGITS))

    private fun formatFor(currency: Currency): NumberFormat = formats.getOrPut(currency) {
        // The locale decides the separators and where the symbol sits; the currency decides which
        // symbol. Passing both is what lets a Turkish phone show a dollar amount correctly.
        NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = PlatformCurrency.getInstance(currency.name)
        }
    }

    private companion object {
        /** True for all four supported currencies; see the note on [Currency]. */
        const val MINOR_UNIT_DIGITS = 2
    }
}

/**
 * Rebuilt only when the language changes, so the NumberFormat objects behind it are reused across
 * recompositions instead of being created per row.
 */
@Composable
fun rememberMoneyFormatter(): MoneyFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { MoneyFormatter(locale) }
}
