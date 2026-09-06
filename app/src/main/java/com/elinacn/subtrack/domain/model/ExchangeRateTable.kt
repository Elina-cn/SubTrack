package com.elinacn.subtrack.domain.model

/**
 * What one whole unit of each currency is worth, all quoted against a single anchor.
 *
 * Rates are scaled [Long] rather than Double. A rate multiplies every amount that passes through
 * it, so an inexact rate does not lose one kuruş once - it spreads a proportional error across the
 * whole total.
 *
 * Quoting everything against one anchor lets any pair convert in a single step, source rate over
 * target rate, so a cross conversion such as USD to EUR rounds once instead of twice.
 */
class ExchangeRateTable private constructor(private val ratesToAnchor: Map<Currency, Long>) {

    /** How many [RATE_SCALE]-scaled anchor units one whole unit of [currency] is worth. */
    fun rateOf(currency: Currency): Long = ratesToAnchor.getValue(currency)

    companion object {

        /**
         * Rates carry four decimal places.
         *
         * Six would be finer but leaves less room before a multiplication overflows Long, and four
         * is already well past the accuracy of a table that is typed in by hand.
         */
        const val RATE_SCALE = 10_000L

        /** Four decimal places, so the smallest rate that can be represented at all. */
        const val MIN_RATE = 1L

        /**
         * The largest rate the arithmetic can carry: 1.000,0000 units of the anchor per foreign
         * unit.
         *
         * The bound was set from a [Long] product in [CurrencyConverter]:
         * `groupTotalCents * sourceRate + targetRate / 2`, which at the per-subscription ceiling of
         * 10^8 kuruş reached Long.MAX_VALUE after **9.223 subscriptions in one currency**.
         *
         * The totals no longer run in a Long - normalising a weekly price multiplies it by 52
         * first, which left room for 177 rows, so the intermediate is a BigInteger and the product
         * has no ceiling of its own. What bounds a total now is the answer fitting in [Money]:
         * twenty-one million rows at these same ceilings. Both figures are pinned by tests rather
         * than left to this comment.
         *
         * The rate is capped here anyway, because [CurrencyConverter.convert] still answers about a
         * single amount in a Long, and because a rate above a thousand is a typo rather than a
         * currency.
         */
        const val MAX_RATE = 10_000_000L

        /**
         * A snapshot taken on 2026-08-29, anchored on TRY.
         *
         * **These go stale.** They are a starting point, not a source of truth: phase 9b lets the
         * user edit them and v1.1 fetches them. Anyone reading this later should assume the numbers
         * are wrong and check them.
         *
         * Every [Currency] has to appear here - [rateOf] reads the map directly, so a new constant
         * without a line below would fail at runtime rather than at compile time.
         */
        val Default = ExchangeRateTable(
            mapOf(
                Currency.TRY to 10_000L, // 1.0000, the anchor
                Currency.USD to 428_500L, // 42.8500 TRY
                Currency.EUR to 462_000L, // 46.2000 TRY
                Currency.GBP to 539_000L // 53.9000 TRY
            )
        )

        /**
         * Builds a table from edited rates, keeping [Default] for anything left out.
         *
         * This is where phase 9b's stored values arrive. Filling the gaps instead of demanding a
         * complete map means a half-written preference file cannot leave a currency unpriced.
         */
        fun of(rates: Map<Currency, Long>): ExchangeRateTable =
            ExchangeRateTable(Default.ratesToAnchor + rates)
    }
}
