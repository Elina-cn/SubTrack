package com.elinacn.subtrack.domain.model

/**
 * An amount of money held as whole minor units - kuruş for TRY, cents for USD.
 *
 * Storing minor units as [Long] keeps arithmetic exact. Doing the same sums in Double loses
 * fractions of a kuruş on every operation, and those losses accumulate down a list.
 */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(cents + other.cents)

    operator fun minus(other: Money): Money = Money(cents - other.cents)

    /** Scales the amount, e.g. a yearly price spread over twelve months. */
    operator fun times(factor: Int): Money = Money(cents * factor)

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    companion object {
        val ZERO = Money(0)

        /** Builds an amount from whole and minor units: `of(159, 99)` is 159,99. */
        fun of(units: Long, minorUnits: Long = 0): Money = Money(units * MINOR_UNITS_PER_UNIT + minorUnits)

        private const val MINOR_UNITS_PER_UNIT = 100L
    }
}

/** Adds up amounts, yielding [Money.ZERO] for an empty source. */
fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { running, amount -> running + amount }
