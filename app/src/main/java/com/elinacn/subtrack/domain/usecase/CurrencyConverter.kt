package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.ExchangeRateTable
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.sum

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
     * Adds up [subscriptions] in [target].
     *
     * Amounts are summed within each currency first and that one sum is converted, rather than
     * converting every subscription and adding the results. The two orders differ by a kuruş or
     * two; this one was chosen because its error is bounded by how many currencies are on screen -
     * at most four - instead of by how many subscriptions there are. Converting row by row lets a
     * long list drift by up to half a kuruş per row.
     *
     * Nothing on screen is a per-row converted figure, so there is no parts-versus-total mismatch
     * for the user to notice: each card shows its own price in its own currency.
     */
    fun totalIn(subscriptions: List<Subscription>, target: Currency): Money =
        subscriptions
            .groupBy { it.currency }
            .map { (currency, group) -> convert(group.map { it.price }.sum(), currency, target) }
            .sum()

    /**
     * Integer division rounding half away from zero, which is how the price the user typed was
     * read in the first place - the same amount cannot mean one thing on entry and another on
     * display.
     *
     * HALF_EVEN exists to stop repeated rounding from drifting upward, but that needs many
     * roundings to show, and [totalIn] performs at most one per currency.
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
}
