package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.ExchangeRateTable
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.domain.model.TotalPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/**
 * Turning prices billed on different clocks into one comparable figure.
 *
 * Every expected value below is written as the arithmetic that produces it, so a reader can check
 * it without running anything.
 */
class PeriodNormalisationTest {

    private val converter = CurrencyConverter()

    // --- one subscription -----------------------------------------------------------------

    @Test
    fun monthlyPrice_isAlreadyTheMonthlyCost() {
        val subscriptions = listOf(subscription(cents = 15_999, period = BillingPeriod.MONTHLY))

        val total = converter.totalIn(subscriptions, Currency.TRY, TotalPeriod.MONTHLY)

        assertEquals(Money(15_999), total)
    }

    @Test
    fun yearlyPrice_thatDividesEvenly_losesNothing() {
        // 1.200,00 a year is 100,00 a month with nothing left over.
        val subscriptions = listOf(subscription(cents = 120_000, period = BillingPeriod.YEARLY))

        val total = converter.totalIn(subscriptions, Currency.TRY, TotalPeriod.MONTHLY)

        assertEquals(Money(10_000), total)
    }

    @Test
    fun yearlyPrice_thatDoesNotDivide_roundsHalfUp() {
        // 1.199,00 / 12 = 99,91666... kuruş cinsinden 9991,66 -> HALF_UP -> 9992.
        val subscriptions = listOf(subscription(cents = 119_900, period = BillingPeriod.YEARLY))

        val total = converter.totalIn(subscriptions, Currency.TRY, TotalPeriod.MONTHLY)

        assertEquals(Money(9_992), total)
    }

    @Test
    fun weeklyPrice_isFiftyTwoPaymentsOverTwelveMonths() {
        // 10,00 x 52 / 12 = 43,3333... -> 43,33.
        val subscriptions = listOf(subscription(cents = 1_000, period = BillingPeriod.WEEKLY))

        val total = converter.totalIn(subscriptions, Currency.TRY, TotalPeriod.MONTHLY)

        assertEquals(Money(4_333), total)
    }

    @Test
    fun weeklyPrice_overAYear_isFiftyTwoTimesThePrice() {
        val subscriptions = listOf(subscription(cents = 1_000, period = BillingPeriod.WEEKLY))

        val total = converter.totalIn(subscriptions, Currency.TRY, TotalPeriod.YEARLY)

        assertEquals(Money(52_000), total)
    }

    // --- a list of them -------------------------------------------------------------------

    @Test
    fun mixedPeriods_addUpWithoutDrift() {
        // 100,00 monthly + 1.200,00 yearly + 10,00 weekly
        //   yearly cost = 10.000 x 12 + 120.000 x 1 + 1.000 x 52 = 292.000 kuruş
        //   monthly     = 292.000 / 12 = 24.333,33 -> 24.333 -> 243,33
        val total = converter.totalIn(mixedList(), Currency.TRY, TotalPeriod.MONTHLY)

        assertEquals(Money(24_333), total)
    }

    @Test
    fun mixedPeriods_overAYear_areExact() {
        // The same 292.000 kuruş, divided by nothing: a yearly total never rounds at all when the
        // list is already in the currency asked for.
        val total = converter.totalIn(mixedList(), Currency.TRY, TotalPeriod.YEARLY)

        assertEquals(Money(292_000), total)
    }

    @Test
    fun yearlyTotal_isNotTheRoundedMonthlyTotalTimesTwelve() {
        val monthly = converter.totalIn(mixedList(), Currency.TRY, TotalPeriod.MONTHLY)
        val yearly = converter.totalIn(mixedList(), Currency.TRY, TotalPeriod.YEARLY)

        // 24.333 x 12 = 291.996, four kuruş under the real yearly cost. The screen shows the real
        // one: the yearly figure comes from the same unrounded sum the monthly figure came from,
        // not from the monthly figure itself.
        assertEquals(Money(291_996), monthly * 12)
        assertNotEquals(monthly * 12, yearly)
        assertEquals(4L, yearly.cents - (monthly * 12).cents)
    }

    @Test
    fun monthlyTotal_timesTwelve_staysWithinOneRoundingOfTheYearlyTotal() {
        // The gap can never be more than half of the twelve kuruş a single rounding can move,
        // whatever the list holds - a bound worth pinning, because a second rounding would break it.
        val awkward = listOf(
            subscription(cents = 119_901, period = BillingPeriod.YEARLY),
            subscription(cents = 7, period = BillingPeriod.WEEKLY),
            subscription(cents = 3_333, period = BillingPeriod.MONTHLY)
        )

        val monthly = converter.totalIn(awkward, Currency.TRY, TotalPeriod.MONTHLY)
        val yearly = converter.totalIn(awkward, Currency.TRY, TotalPeriod.YEARLY)

        val gap = yearly.cents - (monthly * 12).cents
        assertTrue("gap was $gap", gap in -6..6)
    }

    // --- the two conversions together -----------------------------------------------------

    @Test
    fun periodAndCurrency_roundOnceTogether_notOnceEach() {
        // 10,00 USD a week, totalled in TRY at the shipped 42,8500.
        //
        // Together:  1.000 x 52 x 428.500 / (10.000 x 12) = 185.683,83 -> 185.683 -> 1.856,83 TRY
        // Separately: 52.000 / 12 = 4.333,33 -> 4.333, then x 42,85    -> 185.669 -> 1.856,69 TRY
        //
        // Fourteen kuruş apart on a single subscription, and the error grows with the list.
        val subscriptions = listOf(
            subscription(cents = 1_000, period = BillingPeriod.WEEKLY, currency = Currency.USD)
        )

        val together = converter.totalIn(subscriptions, Currency.TRY, TotalPeriod.MONTHLY)

        val roundedMonthlyPrice = converter.totalIn(subscriptions, Currency.USD, TotalPeriod.MONTHLY)
        val separately = converter.convert(roundedMonthlyPrice, Currency.USD, Currency.TRY)

        assertEquals(Money(185_683), together)
        assertEquals(Money(185_669), separately)
        assertEquals(14L, together.cents - separately.cents)
    }

    @Test
    fun mixedCurrenciesAndPeriods_convertOncePerCurrency() {
        // Each currency is weighed, summed and divided on its own, once:
        //   TRY: 10.000 x 12 = 120.000 a year, no rate involved -> / 12 = 10.000
        //   USD: 12.000 x  1 =  12.000 a year, x 428.500 / (10.000 x 12) = 42.850
        // 10.000 + 42.850 = 52.850 kuruş, or 528,50 TRY a month.
        val subscriptions = listOf(
            subscription(cents = 10_000, period = BillingPeriod.MONTHLY),
            subscription(cents = 12_000, period = BillingPeriod.YEARLY, currency = Currency.USD)
        )

        val total = converter.totalIn(subscriptions, Currency.TRY, TotalPeriod.MONTHLY)

        assertEquals(Money(52_850), total)
    }

    @Test
    fun emptyList_isZeroInEitherView() {
        assertEquals(Money.ZERO, converter.totalIn(emptyList(), Currency.TRY, TotalPeriod.MONTHLY))
        assertEquals(Money.ZERO, converter.totalIn(emptyList(), Currency.TRY, TotalPeriod.YEARLY))
    }

    // --- overflow -------------------------------------------------------------------------

    /**
     * The ceilings, on the widest path there is: weekly prices, the largest rate, into the anchor.
     */
    @Test
    fun atThePriceAndRateCeilings_theWeeklyPathStaysPositive() {
        val converter = CurrencyConverter(
            ExchangeRateTable.of(mapOf(Currency.USD to ExchangeRateTable.MAX_RATE))
        )
        val subscriptions = List(PLAUSIBLE_LIST_BOUND) {
            subscription(
                cents = MAX_PRICE_CENTS,
                period = BillingPeriod.WEEKLY,
                currency = Currency.USD
            )
        }

        val total = converter.totalIn(subscriptions, Currency.TRY, TotalPeriod.MONTHLY)

        // 100 x 10^8 x 52 = 5,2 x 10^11 kuruş a year, at 1.000,0000 TRY per dollar, over twelve.
        assertEquals(Money(43_333_333_333_333L), total)
        assertTrue(total.cents > 0)
    }

    /**
     * States the headroom as arithmetic, the way [CurrencyConverterTest] does for the unweighted
     * path, so a change to either ceiling breaks a test instead of a total.
     *
     * Normalisation multiplies the widest value by [BillingPeriod.WEEKLY]'s payments a year, so
     * the room left over drops by the same factor: 9.223 subscriptions become 177. Both numbers
     * are the absolute ceiling - every row at 1.000.000 whole units, weekly, in a currency the
     * user has edited up to 1.000,0000. At the rates the app ships with, the same list fits 3.290
     * rows.
     *
     * **These two numbers are history, not the converter's limit.** 177 is what a Long intermediate
     * would leave, and it is why the intermediate is a BigInteger instead; the figures stay pinned
     * because they are the reason for that choice, and because a ceiling moving without anyone
     * meaning to should still break something. What actually bounds the totals now is
     * [whatIsLeftIsTheAnswerHavingToFitInMoney].
     */
    @Test
    fun weeklyNormalisation_costsHeadroom_andWhatIsLeftIsPinned() {
        val unweighted = Long.MAX_VALUE / (MAX_PRICE_CENTS * ExchangeRateTable.MAX_RATE)
        val weighted = Long.MAX_VALUE /
            (MAX_PRICE_CENTS * BillingPeriod.WEEKLY.paymentsPerYear * ExchangeRateTable.MAX_RATE)

        assertEquals(9_223L, unweighted)
        assertEquals(177L, weighted)
        assertTrue(weighted > PLAUSIBLE_LIST_BOUND)
    }

    /**
     * Five hundred rows at both ceilings - well past the 177 a Long intermediate allowed.
     *
     * The product inside is 2,6 x 10^19, which does not fit in a Long at all; the test asserts that
     * first, so it is measuring what it claims to. The answer still fits in [Money], and it is the
     * exact figure, not a wrapped one.
     */
    @Test
    fun farPastTheOldLongCeiling_theTotalIsStillExact() {
        val converter = CurrencyConverter(
            ExchangeRateTable.of(mapOf(Currency.USD to ExchangeRateTable.MAX_RATE))
        )
        val subscriptions = List(WIDE_LIST_BOUND) {
            subscription(
                cents = MAX_PRICE_CENTS,
                period = BillingPeriod.WEEKLY,
                currency = Currency.USD
            )
        }

        val total = converter.totalIn(subscriptions, Currency.TRY, TotalPeriod.MONTHLY)

        val widestProduct = BigInteger.valueOf(WIDE_LIST_BOUND * MAX_PRICE_CENTS * 52L) *
            BigInteger.valueOf(ExchangeRateTable.MAX_RATE)
        assertTrue(widestProduct > BigInteger.valueOf(Long.MAX_VALUE))
        // 500 x 10^8 x 52 = 2,6 x 10^12 kuruş a year, at 1.000,0000 TRY per dollar, over twelve.
        assertEquals(Money(216_666_666_666_667L), total)
        assertTrue(total.cents > 0)
    }

    /**
     * The bound that is left once the intermediate cannot overflow: the answer is a [Money], and a
     * Money is a Long.
     *
     * At both ceilings, converting into the anchor, one weekly row costs 433.333.333.333 kuruş a
     * month - so the monthly view runs out after twenty-one million rows and the yearly view after
     * one and three quarter million. Room a list held in memory and drawn in a lazy column will
     * not see; the number is here so that if it ever changes, it changes on purpose.
     */
    @Test
    fun whatIsLeftIsTheAnswerHavingToFitInMoney() {
        val perRowMonthly = MAX_PRICE_CENTS *
            BillingPeriod.WEEKLY.paymentsPerYear * ExchangeRateTable.MAX_RATE /
            (ANCHOR_RATE * TotalPeriod.MONTHLY.partsOfAYear)
        val perRowYearly = MAX_PRICE_CENTS *
            BillingPeriod.WEEKLY.paymentsPerYear * ExchangeRateTable.MAX_RATE / ANCHOR_RATE

        assertEquals(433_333_333_333L, perRowMonthly)
        assertEquals(21_284_704L, Long.MAX_VALUE / perRowMonthly)
        assertEquals(1_773_725L, Long.MAX_VALUE / perRowYearly)
    }

    private fun mixedList() = listOf(
        subscription(cents = 10_000, period = BillingPeriod.MONTHLY),
        subscription(cents = 120_000, period = BillingPeriod.YEARLY),
        subscription(cents = 1_000, period = BillingPeriod.WEEKLY)
    )

    private fun subscription(
        cents: Long,
        period: BillingPeriod,
        currency: Currency = Currency.TRY
    ) = Subscription(
        id = 0,
        name = "Test",
        price = Money(cents),
        currency = currency,
        billingPeriod = period,
        nextPaymentDate = null,
        category = SubscriptionCategory.OTHER,
        iconKey = null,
        createdAt = 0
    )

    private companion object {
        /** HomeViewModel.MAX_PRICE of 1.000.000 whole units, in minor units. */
        const val MAX_PRICE_CENTS = 100_000_000L

        /** Past any list a person keeps, and inside the room the weighted path leaves. */
        const val PLAUSIBLE_LIST_BOUND = 100

        /** Past what a Long intermediate could have carried at the ceilings, which was 177. */
        const val WIDE_LIST_BOUND = 500

        /** TRY's own rate: the anchor is quoted against itself at 1,0000. */
        const val ANCHOR_RATE = 10_000L
    }
}
