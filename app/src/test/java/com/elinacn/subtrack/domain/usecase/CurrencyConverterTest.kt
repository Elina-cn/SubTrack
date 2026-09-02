package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.ExchangeRateTable
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Expected values are worked out from the shipped rate table by hand and written as literals.
 *
 * Recomputing them from the same table the code reads would let a wrong rate agree with itself:
 * the test would pass whatever the table said.
 */
class CurrencyConverterTest {

    private val converter = CurrencyConverter()

    // --- single conversions ---------------------------------------------------------------

    @Test
    fun convert_sameCurrency_returnsTheAmountUntouched() {
        val amount = Money(15_999)

        assertEquals(amount, converter.convert(amount, Currency.TRY, Currency.TRY))
    }

    @Test
    fun convert_usdToTry_usesTheDollarRate() {
        // 100.00 USD at 42.8500 is 4285.00 TRY
        assertEquals(Money(428_500), converter.convert(Money(10_000), Currency.USD, Currency.TRY))
    }

    @Test
    fun convert_eurToTry_usesTheEuroRate() {
        // 24.99 EUR at 46.2000 is 1154.538, rounded to 1154.54 TRY
        assertEquals(Money(115_454), converter.convert(Money(2_499), Currency.EUR, Currency.TRY))
    }

    @Test
    fun convert_gbpToTry_usesThePoundRate() {
        // 9.99 GBP at 53.9000 is 538.461, rounded to 538.46 TRY
        assertEquals(Money(53_846), converter.convert(Money(999), Currency.GBP, Currency.TRY))
    }

    @Test
    fun convert_tryToUsd_dividesByTheDollarRate() {
        // 100.00 TRY at 42.8500 is 2.3337 USD, rounded to 2.33
        assertEquals(Money(233), converter.convert(Money(10_000), Currency.TRY, Currency.USD))
    }

    @Test
    fun convert_usdToEur_crossesWithoutPassingThroughTheAnchorTwice() {
        // 100.00 USD is 42.85 / 46.20 = 92.7489... EUR, rounded to 92.75
        assertEquals(Money(9_275), converter.convert(Money(10_000), Currency.USD, Currency.EUR))
    }

    @Test
    fun convert_exactlyHalfAMinorUnit_roundsAwayFromZero() {
        // 0.10 USD at 42.8500 is 4.285 TRY exactly - the tie case. HALF_UP takes it to 4.29.
        assertEquals(Money(429), converter.convert(Money(10), Currency.USD, Currency.TRY))
    }

    // --- totals ---------------------------------------------------------------------------

    @Test
    fun totalIn_emptyList_isZero() {
        assertEquals(Money.ZERO, converter.totalIn(emptyList(), Currency.TRY))
    }

    @Test
    fun totalIn_onlyBaseCurrency_addsUpWithoutConverting() {
        val subscriptions = listOf(
            subscription(cents = 15_999),
            subscription(cents = 5_990),
            subscription(cents = 1)
        )

        // Exactly the raw sum: nothing went through a rate, so nothing could have been rounded.
        assertEquals(Money(21_990), converter.totalIn(subscriptions, Currency.TRY))
    }

    @Test
    fun totalIn_mixedCurrencies_convertsEachIntoTheTarget() {
        val subscriptions = listOf(
            subscription(cents = 15_999), // 159.99 TRY
            subscription(cents = 1_099, currency = Currency.USD), // 470.92 TRY
            subscription(cents = 2_499, currency = Currency.EUR) // 1154.54 TRY
        )

        assertEquals(Money(178_545), converter.totalIn(subscriptions, Currency.TRY))
    }

    @Test
    fun totalIn_foreignCurrencyOnly_convertsTheGroupOnce() {
        val subscriptions = listOf(
            subscription(cents = 10_000, currency = Currency.USD),
            subscription(cents = 10_000, currency = Currency.USD)
        )

        assertEquals(Money(857_000), converter.totalIn(subscriptions, Currency.TRY))
    }

    @Test
    fun totalIn_targetIsNotTheAnchor_stillConvertsEveryGroup() {
        val subscriptions = listOf(
            subscription(cents = 10_000, currency = Currency.USD), // 92.75 EUR
            subscription(cents = 4_620, currency = Currency.TRY) // 1.00 EUR
        )

        assertEquals(Money(9_375), converter.totalIn(subscriptions, Currency.EUR))
    }

    // --- rounding order -------------------------------------------------------------------

    /**
     * The documented reason for summing inside a currency before converting.
     *
     * Four rows of 0.01 USD are 42.85 kuruş each. Rounded one at a time that is 43 kuruş four
     * times, or 1.72 TRY. The exact total is 1.714 TRY, which rounds to 1.71 - and that is what
     * the converter returns, because it converts the group's 0.04 USD in one step.
     */
    @Test
    fun totalIn_manySmallRows_roundsTheGroupRatherThanEachRow() {
        val subscriptions = List(4) { subscription(cents = 1, currency = Currency.USD) }

        val perRow = subscriptions
            .map { converter.convert(it.price, Currency.USD, Currency.TRY) }
            .fold(Money.ZERO) { running, amount -> running + amount }

        assertEquals(Money(171), converter.totalIn(subscriptions, Currency.TRY))
        assertEquals(Money(172), perRow)
        assertNotEquals(perRow, converter.totalIn(subscriptions, Currency.TRY))
    }

    // --- swappable table ------------------------------------------------------------------

    /** What phase 9b will do: hand in edited rates and leave the rest alone. */
    @Test
    fun convert_editedTable_usesTheNewRateAndKeepsTheOthers() {
        val edited = CurrencyConverter(ExchangeRateTable.of(mapOf(Currency.USD to 500_000L)))

        // 10.00 USD at the edited 50.0000 is 500.00 TRY
        assertEquals(Money(50_000), edited.convert(Money(1_000), Currency.USD, Currency.TRY))
        // EUR was not overridden, so it still converts at 46.2000
        assertEquals(Money(462_000), edited.convert(Money(10_000), Currency.EUR, Currency.TRY))
    }

    // --- overflow -------------------------------------------------------------------------

    /**
     * The bound behind [ExchangeRateTable.MAX_RATE], exercised rather than asserted in a comment.
     *
     * The widest value inside convert is `groupTotalCents * sourceRate`, in a Long. A thousand
     * subscriptions at the per-subscription ceiling is 10^11 kuruş; at the maximum rate that is
     * 10^18, comfortably inside Long.MAX_VALUE (about 9.22 x 10^18).
     */
    @Test
    fun totalIn_atThePriceAndRateCeilings_staysPositive() {
        val converter = CurrencyConverter(
            ExchangeRateTable.of(mapOf(Currency.USD to ExchangeRateTable.MAX_RATE))
        )
        val subscriptions = List(SUBSCRIPTION_BOUND) {
            subscription(cents = MAX_PRICE_CENTS, currency = Currency.USD)
        }

        val total = converter.totalIn(subscriptions, Currency.TRY)

        // 10^11 kuruş at 1.000,0000 TRY per dollar, back down by the anchor's own scale.
        assertEquals(Money(100_000_000_000_000L), total)
        assertTrue(total.cents > 0)
    }

    /**
     * The true worst case: the largest rate as the source and the smallest as the target, so the
     * division does not shrink the product at all.
     */
    @Test
    fun totalIn_maxRateSourceIntoMinRateTarget_isTheWidestProductAndStillFits() {
        val converter = CurrencyConverter(
            ExchangeRateTable.of(
                mapOf(
                    Currency.EUR to ExchangeRateTable.MAX_RATE,
                    Currency.USD to ExchangeRateTable.MIN_RATE
                )
            )
        )
        val subscriptions = List(SUBSCRIPTION_BOUND) {
            subscription(cents = MAX_PRICE_CENTS, currency = Currency.EUR)
        }

        val total = converter.totalIn(subscriptions, Currency.USD)

        assertEquals(Money(1_000_000_000_000_000_000L), total)
        assertTrue(total.cents > 0)
    }

    /**
     * States the headroom as arithmetic rather than prose, so raising [ExchangeRateTable.MAX_RATE]
     * without thinking breaks a test instead of a total.
     */
    @Test
    fun maxRate_leavesRoomForFarMoreSubscriptionsThanAnyoneWillHave() {
        val widestPerSubscription = MAX_PRICE_CENTS * ExchangeRateTable.MAX_RATE
        val affordableSubscriptions = Long.MAX_VALUE / widestPerSubscription

        assertEquals(9_223L, affordableSubscriptions)
        assertTrue(affordableSubscriptions > SUBSCRIPTION_BOUND)
    }

    private fun subscription(
        cents: Long,
        currency: Currency = Currency.TRY
    ) = Subscription(
        id = 0,
        name = "Test",
        price = Money(cents),
        currency = currency,
        billingPeriod = BillingPeriod.MONTHLY,
        nextPaymentDate = null,
        category = SubscriptionCategory.OTHER,
        iconKey = null,
        createdAt = 0
    )

    private companion object {
        /** HomeViewModel.MAX_PRICE of 1.000.000 whole units, in minor units. */
        const val MAX_PRICE_CENTS = 100_000_000L

        /** Far past any plausible list; the headroom above it is what MAX_RATE was chosen for. */
        const val SUBSCRIPTION_BOUND = 1_000
    }
}
