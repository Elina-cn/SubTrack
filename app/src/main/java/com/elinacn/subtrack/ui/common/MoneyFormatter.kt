package com.elinacn.subtrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale
import java.util.Currency as PlatformCurrency

/**
 * Writes amounts the way the reader's language writes money, always with a currency symbol.
 *
 * This lives in the UI layer because it needs a Locale, and the domain stays free of platform
 * APIs. The value itself never changes shape: it goes in as whole minor units and only its
 * rendering is locale dependent.
 *
 * **The locale decides the number, never the currency mark.** Separators, digit grouping and which
 * side the mark sits on are the reader's, so a Turkish reader sees "159,99 ₺" and an English one
 * "₺159.99" for the same stored amount. The mark itself is [Currency.symbol] in every locale.
 *
 * That last part is what phase 14b changed, and the reason is that the platform's answer was not
 * consistent. `NumberFormat` writes whatever glyph the reader's locale has for the currency and
 * falls back to the three-letter ISO code when it has none, which put "TRY 1,785.45" in the
 * dashboard and "$10.99" on a card on the same English screen. A code is also wider than a mark,
 * and at 360dp and font scale 2.0 those extra characters are what push a total into being clipped.
 * See ARCHITECTURE section 6.
 */
class MoneyFormatter(private val locale: Locale) {

    // NumberFormat is expensive to build and not thread safe. Composition is single threaded, and
    // this object never leaves it, so a plain map is enough.
    private val formats = mutableMapOf<Currency, NumberFormat>()

    /** Renders [amount], which is assumed to be denominated in [currency]. */
    fun format(amount: Money, currency: Currency): String =
        formatFor(currency).format(BigDecimal.valueOf(amount.cents, MINOR_UNIT_DIGITS))

    private fun formatFor(currency: Currency): NumberFormat = formats.getOrPut(currency) {
        // The locale decides the separators and where the mark sits; the currency decides which
        // mark. Passing both is what lets a Turkish phone show a dollar amount correctly.
        NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = PlatformCurrency.getInstance(currency.name)

            // Set after the currency, because assigning a currency rewrites the symbol with the
            // locale's own answer for it - the one that is sometimes an ISO code. Only a
            // DecimalFormat carries symbols; NumberFormat does not promise to return one, so a
            // locale whose currency format is something else keeps the platform's rendering rather
            // than being forced through a cast that would throw.
            if (this is DecimalFormat) {
                decimalFormatSymbols = decimalFormatSymbols.apply {
                    currencySymbol = currency.symbol
                }
            }
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
