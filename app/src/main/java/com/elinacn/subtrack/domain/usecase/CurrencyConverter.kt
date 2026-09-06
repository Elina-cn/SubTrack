package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.ExchangeRateTable
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.TotalPeriod
import com.elinacn.subtrack.domain.model.sum
import java.math.BigInteger

/**
 * Converts amounts between currencies and adds up a mixed list.
 *
 * The table arrives through the constructor instead of being reached for inside, so phase 9b can
 * hand in rates read from storage without this class changing at all.
 */
class CurrencyConverter(private val rates: ExchangeRateTable = ExchangeRateTable.Default) {

    /**
     * Converts [amount] from one currency to another, rounded to the nearest minor unit.
     *
     * Both rates carry the same scale, so it cancels and the conversion is one multiplication and
     * one division - a single rounding whichever pair is involved.
     *
     * The intermediate product stays inside Long up to roughly 170 billion whole units of a
     * currency, which a list built under the per-subscription price ceiling cannot reach.
     */
    fun convert(amount: Money, from: Currency, to: Currency): Money {
        // More than a shortcut: returning early means an unconverted amount comes back exactly as
        // it went in, having been through no rounding at all.
        if (from == to) return amount
        return Money(divideHalfUp(amount.cents * rates.rateOf(from), rates.rateOf(to)))
    }

    /**
     * Adds up [subscriptions] in [target], taking every price exactly as it stands.
     *
     * Amounts are summed within each currency first and that one sum is converted, rather than
     * converting every subscription and adding the results. The two orders differ by a kuruş or
     * two; this one was chosen because its error is bounded by how many currencies are on screen -
     * at most four - instead of by how many subscriptions there are. Converting row by row lets a
     * long list drift by up to half a kuruş per row.
     *
     * Nothing on screen is a per-row converted figure, so there is no parts-versus-total mismatch
     * for the user to notice: each card shows its own price in its own currency.
     *
     * **This adds prices, not costs.** A yearly price and a monthly one are added as they are, so
     * the result only means something when every subscription shares a period. The screen uses the
     * overload that takes a [TotalPeriod]; this one stays for the case where the caller genuinely
     * wants the prices themselves.
     */
    fun totalIn(subscriptions: List<Subscription>, target: Currency): Money =
        total(subscriptions, target, weight = { 1L }, parts = 1)

    /**
     * Adds up what [subscriptions] cost over one [period], in [target].
     *
     * Two conversions happen to every price on the way here - out of its billing period and out of
     * its currency - and **both round once, together, at the end**. Each is a fraction: yearly over
     * twelve, kuruş at one rate over another. Rounding them separately would round twice and the
     * halves would not cancel; the error shows up as a total that is a kuruş or two off what the
     * same numbers give on paper.
     *
     * What makes one rounding possible is [BillingPeriod.paymentsPerYear]: multiplying by it is
     * exact, so the yearly cost of a group is a plain sum with nothing lost yet. Everything after
     * it - the rate, and the twelve that makes a month - is one division.
     *
     * Multiply first, divide last, for the same reason.
     */
    fun totalIn(subscriptions: List<Subscription>, target: Currency, period: TotalPeriod): Money =
        total(
            subscriptions,
            target,
            weight = { it.billingPeriod.paymentsPerYear.toLong() },
            parts = period.partsOfAYear
        )

    /**
     * The shared body: weigh each price, sum per currency, then convert and divide in one step.
     *
     * [weight] scales a price before it is added - one for the prices as they are, the payments a
     * year for a cost. [parts] is what the sum is finally divided by, so the rate division and the
     * period division are the same division.
     *
     * **The intermediate is a BigInteger, the answer is still Long kuruş.** Weighing a weekly price
     * multiplies it by 52 before the rate does, and that product is the widest value in the app: at
     * the price and rate ceilings a Long ran out after 177 rows, where the same sum of unweighted
     * prices lasted 9.223. Nothing about the ceilings changed - the room to work in did. What is
     * left is the answer itself having to fit in [Money], which at those same ceilings takes over a
     * million rows to reach.
     *
     * The arithmetic is otherwise identical: multiply first, divide once, HALF_UP, and BigInteger
     * truncates toward zero exactly as Long does, so the half added before the division rounds the
     * same way on both.
     */
    private fun total(
        subscriptions: List<Subscription>,
        target: Currency,
        weight: (Subscription) -> Long,
        parts: Int
    ): Money =
        subscriptions
            .groupBy { it.currency }
            .map { (currency, group) ->
                val weighted = group.fold(BigInteger.ZERO) { running, subscription ->
                    running + subscription.price.cents.toBigInteger() * weight(subscription).toBigInteger()
                }
                val sameCurrency = currency == target
                // The rates cancel when nothing is being converted; leaving them out keeps an
                // unconverted amount out of a multiplication it does not need, and with parts = 1
                // out of any rounding at all.
                val numerator =
                    if (sameCurrency) weighted else weighted * rates.rateOf(currency).toBigInteger()
                val denominator =
                    if (sameCurrency) {
                        parts.toBigInteger()
                    } else {
                        rates.rateOf(target).toBigInteger() * parts.toBigInteger()
                    }
                Money(divideHalfUp(numerator, denominator).toLong())
            }
            .sum()

    /**
     * Integer division rounding half away from zero, which is how the price the user typed was
     * read in the first place - the same amount cannot mean one thing on entry and another on
     * display.
     *
     * HALF_EVEN exists to stop repeated rounding from drifting upward, but that needs many
     * roundings to show, and [totalIn] performs at most one per currency.
     *
     * There are two of these, one per width. BigInteger truncates toward zero exactly as Long does,
     * so adding half the divisor first rounds identically on both - which is what lets the totals
     * move to the wider type without any figure on screen changing.
     */
    private fun divideHalfUp(numerator: BigInteger, denominator: BigInteger): BigInteger {
        val half = denominator / TWO
        return if (numerator.signum() >= 0) {
            (numerator + half) / denominator
        } else {
            (numerator - half) / denominator
        }
    }

    /**
     * The narrow one, for [convert]: a single amount times a rate has always fitted in a Long, and
     * allocating BigIntegers to answer a question about one price would buy room it cannot use.
     */
    private fun divideHalfUp(numerator: Long, denominator: Long): Long {
        // Adding half the divisor before an integer division is what pushes a .5 away from zero;
        // the offset follows the sign so a negative amount rounds the same way.
        val half = denominator / 2
        return if (numerator >= 0) {
            (numerator + half) / denominator
        } else {
            (numerator - half) / denominator
        }
    }

    private companion object {
        /** BigInteger.TWO is API 31 and minSdk is 24, so the constant is our own. */
        val TWO: BigInteger = BigInteger.valueOf(2)
    }
}
