package com.elinacn.subtrack.ui.common

import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Pins the rule phase 14b settled: the locale decides the number, the currency decides the mark.
 *
 * Before this, the mark came from the platform, which writes whatever glyph the reader's locale
 * happens to have and falls back to the three-letter ISO code when it has none. That is how one
 * English screen came to show "TRY 1,785.45" at the top and "$10.99" on a card.
 *
 * No Android framework here: [MoneyFormatter] takes a Locale and uses java.text, so it runs as a
 * plain unit test.
 */
class MoneyFormatterTest {

    private val amount = Money.of(1_785, 45)

    @Test
    fun format_everyCurrencyInEveryLocale_writesTheSymbol() {
        for (locale in LOCALES) {
            val formatter = MoneyFormatter(locale)
            for (currency in Currency.entries) {
                val text = formatter.format(amount, currency)

                assertTrue(
                    "$locale/$currency wrote \"$text\", which has no ${currency.symbol}",
                    text.contains(currency.symbol)
                )
            }
        }
    }

    @Test
    fun format_everyCurrencyInEveryLocale_neverWritesTheIsoCode() {
        for (locale in LOCALES) {
            val formatter = MoneyFormatter(locale)
            for (currency in Currency.entries) {
                val text = formatter.format(amount, currency)

                assertFalse(
                    "$locale/$currency fell back to the code: \"$text\"",
                    text.contains(currency.name)
                )
            }
        }
    }

    /**
     * The case that used to be wrong, spelled out on its own.
     *
     * An English reader has no glyph for the lira in their locale data, so this is exactly where
     * the platform used to answer "TRY".
     */
    @Test
    fun format_liraForAnEnglishReader_isTheLiraSign() {
        val text = MoneyFormatter(Locale.forLanguageTag("en-US")).format(amount, Currency.TRY)

        assertTrue("was \"$text\"", text.contains("₺"))
        assertFalse("was \"$text\"", text.contains("TRY"))
    }

    /** The case that was already right stays right: nothing was traded away for the fix. */
    @Test
    fun format_dollarsForAnEnglishReader_isStillTheDollarSign() {
        val text = MoneyFormatter(Locale.forLanguageTag("en-US")).format(amount, Currency.USD)

        assertEquals("$1,785.45", text)
    }

    /**
     * Only the mark was taken away from the locale.
     *
     * Turkish groups with a point and separates decimals with a comma; English does the opposite.
     * Both write the same stored number, and the same lira sign.
     */
    @Test
    fun format_theSameAmount_keepsEachLocalesOwnSeparators() {
        val turkish = MoneyFormatter(Locale.forLanguageTag("tr-TR")).format(amount, Currency.TRY)
        val english = MoneyFormatter(Locale.forLanguageTag("en-US")).format(amount, Currency.TRY)

        assertTrue("was \"$turkish\"", turkish.contains("1.785,45"))
        assertTrue("was \"$english\"", english.contains("1,785.45"))
    }

    /** Two minor digits are always written, so a round amount does not read as a whole number. */
    @Test
    fun format_aRoundAmount_stillWritesBothMinorDigits() {
        val text = MoneyFormatter(Locale.forLanguageTag("tr-TR")).format(Money.of(10), Currency.TRY)

        assertTrue("was \"$text\"", text.contains("10,00"))
        assertTrue("was \"$text\"", text.contains("₺"))
    }

    /** Formats are cached per currency; the second call must not lose the forced mark. */
    @Test
    fun format_theSameCurrencyTwice_writesTheSymbolBothTimes() {
        val formatter = MoneyFormatter(Locale.forLanguageTag("en-US"))

        val first = formatter.format(amount, Currency.EUR)
        val second = formatter.format(Money.of(1), Currency.EUR)

        assertTrue("was \"$first\"", first.contains("€"))
        assertTrue("was \"$second\"", second.contains("€"))
    }

    private companion object {
        /**
         * The app's two languages, plus two that are neither.
         *
         * de-DE writes money the Turkish way but is not Turkish, and ja-JP puts the mark on the
         * other side of a number with no space - between them they cover the shapes a currency
         * format can take without the test being about any one of them.
         */
        val LOCALES = listOf(
            Locale.forLanguageTag("tr-TR"),
            Locale.forLanguageTag("en-US"),
            Locale.forLanguageTag("de-DE"),
            Locale.forLanguageTag("ja-JP")
        )
    }
}
