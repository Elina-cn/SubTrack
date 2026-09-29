package com.elinacn.subtrack.ui.settings.rates

import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.ExchangeRateTable
import com.elinacn.subtrack.ui.common.UiText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The text a rate box shows and the rate that text stands for.
 *
 * Each case runs under both languages the app ships in: nothing here may depend on the default
 * locale, and these tests are what would notice if something started to.
 */
class RateTextTest {

    private val originalLocale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    // --- writing --------------------------------------------------------------------------

    @Test
    fun format_writesADotAndNoTrailingZerosInEveryLanguage() = inEveryLanguage {
        assertEquals("42.85", RateText.format(428_500L))
        assertEquals("46.2", RateText.format(462_000L))
        assertEquals("54", RateText.format(540_000L))
        assertEquals("0.0001", RateText.format(ExchangeRateTable.MIN_RATE))
    }

    @Test
    fun format_theCeiling_hasNoGroupingAndNoExponent() = inEveryLanguage {
        // Not "1.000" (a Turkish thousand the parser would read as one) and not "1E+3".
        assertEquals("1000", RateText.format(ExchangeRateTable.MAX_RATE))
    }

    // --- reading --------------------------------------------------------------------------

    @Test
    fun parse_commaAndDot_areTheSameRateInEveryLanguage() = inEveryLanguage {
        assertEquals(RateResult.Valid(428_500L), RateText.parse("42,85"))
        assertEquals(RateResult.Valid(428_500L), RateText.parse("42.85"))
        assertEquals(RateResult.Valid(428_500L), RateText.parse(" 42.85 "))
    }

    @Test
    fun parse_aGroupedThousand_isRefusedRatherThanMisread() = inEveryLanguage {
        assertInvalid("1.000,5", R.string.error_rate_invalid)
        assertInvalid("1,000.5", R.string.error_rate_invalid)
    }

    @Test
    fun parse_everyRejection_namesItsReason() = inEveryLanguage {
        assertInvalid("", R.string.error_rate_empty)
        assertInvalid("abc", R.string.error_rate_invalid)
        assertInvalid("0", R.string.error_rate_not_positive)
        assertInvalid("-5", R.string.error_rate_not_positive)
        assertInvalid("1,23456", R.string.error_rate_too_many_decimals)
        val tooLarge = RateText.parse("1000.0001") as RateResult.Invalid
        assertEquals(UiText.Resource(R.string.error_rate_too_large, listOf("1000")), tooLarge.reason)
    }

    @Test
    fun parse_theEdgesThatAreAllowed_areAccepted() = inEveryLanguage {
        assertEquals(RateResult.Valid(12_345L), RateText.parse("1.2345"))
        // Five written decimals but only four that mean anything.
        assertEquals(RateResult.Valid(428_500L), RateText.parse("42.85000"))
        assertEquals(RateResult.Valid(ExchangeRateTable.MIN_RATE), RateText.parse("0,0001"))
        assertEquals(RateResult.Valid(ExchangeRateTable.MAX_RATE), RateText.parse("1000"))
    }

    // --- both directions ------------------------------------------------------------------

    /**
     * The screen saves an untouched box by reading its text, so every value the store can hold has
     * to come back from its own text unchanged - otherwise saving would quietly move a rate nobody
     * edited.
     */
    @Test
    fun formatThenParse_givesBackTheSameRate() = inEveryLanguage {
        val samples = listOf(
            ExchangeRateTable.MIN_RATE, 9L, 10L, 9_999L, 10_000L, 10_001L, 428_500L, 462_000L,
            539_000L, 1_234_567L, ExchangeRateTable.MAX_RATE - 1, ExchangeRateTable.MAX_RATE
        ) + (1..500).map { it * 19_997L }
        samples.forEach { scaled ->
            assertEquals(RateResult.Valid(scaled), RateText.parse(RateText.format(scaled)))
        }
    }

    // --- helpers --------------------------------------------------------------------------

    private fun assertInvalid(rawRate: String, expected: Int) {
        val result = RateText.parse(rawRate)
        assertTrue("$rawRate should be refused", result is RateResult.Invalid)
        assertEquals(UiText.Resource(expected), (result as RateResult.Invalid).reason)
    }

    private fun inEveryLanguage(block: () -> Unit) {
        listOf(Locale.forLanguageTag("tr-TR"), Locale.US).forEach { locale ->
            Locale.setDefault(locale)
            block()
        }
    }
}
