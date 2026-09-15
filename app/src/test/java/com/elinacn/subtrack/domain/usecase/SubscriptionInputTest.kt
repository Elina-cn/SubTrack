package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The rules both forms are held to.
 *
 * They were the home screen's private business until phase 15, and the tests that went through
 * `HomeViewModel` still exercise them from up there. These sit directly on the rule instead, which
 * is where the edges live: what counts as two decimal places, what a comma means, where the
 * ceiling is. One copy of the rule, one place its edges are pinned.
 */
class SubscriptionInputTest {

    private val today = LocalDate.of(2026, 9, 15)

    // --- the name --------------------------------------------------------------------------------

    @Test
    fun validateName_whitespaceOnly_isEmpty() {
        assertEquals(NameProblem.EMPTY, SubscriptionInput.validateName("   "))
        assertEquals(NameProblem.EMPTY, SubscriptionInput.validateName(""))
    }

    @Test
    fun validateName_aNameWithSpaceAroundIt_isAName() {
        assertNull(SubscriptionInput.validateName("  Netflix  "))
        assertEquals("Netflix", SubscriptionInput.trimName("  Netflix  "))
    }

    // --- the price -------------------------------------------------------------------------------

    @Test
    fun parsePrice_aPlainAmount_isWholeMinorUnits() {
        assertEquals(PriceResult.Valid(Money(15999)), SubscriptionInput.parsePrice("159.99"))
    }

    @Test
    fun parsePrice_aCommaIsADecimalPoint() {
        // What a Turkish keyboard offers first, and it has to mean the same thing.
        assertEquals(PriceResult.Valid(Money(15999)), SubscriptionInput.parsePrice("159,99"))
    }

    @Test
    fun parsePrice_surroundingSpace_isIgnored() {
        assertEquals(PriceResult.Valid(Money(1000)), SubscriptionInput.parsePrice(" 10 "))
    }

    @Test
    fun parsePrice_trailingZeros_doNotCountAsDecimals() {
        // "159.990" is two decimals written long; "159.999" is three and is refused.
        assertEquals(PriceResult.Valid(Money(15999)), SubscriptionInput.parsePrice("159.990"))
        assertEquals(
            PriceResult.Invalid(PriceProblem.TOO_MANY_DECIMALS),
            SubscriptionInput.parsePrice("159.999")
        )
    }

    @Test
    fun parsePrice_nothingTyped_isEmptyRatherThanMalformed() {
        assertEquals(PriceResult.Invalid(PriceProblem.EMPTY), SubscriptionInput.parsePrice("  "))
    }

    @Test
    fun parsePrice_notANumber_isMalformed() {
        assertEquals(PriceResult.Invalid(PriceProblem.MALFORMED), SubscriptionInput.parsePrice("abc"))
        assertEquals(PriceResult.Invalid(PriceProblem.MALFORMED), SubscriptionInput.parsePrice("1.2.3"))
    }

    @Test
    fun parsePrice_zeroAndBelow_areNotPrices() {
        assertEquals(PriceResult.Invalid(PriceProblem.NOT_POSITIVE), SubscriptionInput.parsePrice("0"))
        assertEquals(PriceResult.Invalid(PriceProblem.NOT_POSITIVE), SubscriptionInput.parsePrice("-5"))
    }

    @Test
    fun parsePrice_theCeilingItselfIsAllowed_anythingOverItIsNot() {
        val ceiling = SubscriptionInput.MAX_PRICE.toPlainString()
        assertEquals(PriceResult.Valid(Money(100_000_000)), SubscriptionInput.parsePrice(ceiling))
        assertEquals(
            PriceResult.Invalid(PriceProblem.TOO_LARGE),
            SubscriptionInput.parsePrice("1000000.01")
        )
    }

    // --- the date --------------------------------------------------------------------------------

    @Test
    fun validateDate_noDate_isFine() {
        assertNull(SubscriptionInput.validateDate(null, today))
    }

    @Test
    fun validateDate_aPastDate_isFine() {
        // The date is an anchor, not a deadline: someone entering a subscription they already have
        // knows when it last renewed.
        assertNull(SubscriptionInput.validateDate(today.minusYears(3), today))
    }

    @Test
    fun validateDate_theLastAllowedDay_isAllowed() {
        val furthest = today.plusYears(SubscriptionInput.MAX_YEARS_AHEAD)
        assertNull(SubscriptionInput.validateDate(furthest, today))
        assertEquals(
            DateProblem.TOO_FAR_AHEAD,
            SubscriptionInput.validateDate(furthest.plusDays(1), today)
        )
    }
}
