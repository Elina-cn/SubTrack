package com.elinacn.subtrack.ui.settings.rates

import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.ExchangeRateTable
import com.elinacn.subtrack.ui.common.UiText
import java.math.BigDecimal

/** A typed rate read into a scaled one, or the reason it could not be. */
internal sealed interface RateResult {
    data class Valid(val scaledRate: Long) : RateResult
    data class Invalid(val reason: UiText) : RateResult
}

/**
 * The two directions between a rate box and a stored rate.
 *
 * **Written with a dot in every language**, the rule the price field already follows
 * (ARCHITECTURE section 22): "42.85", never "42,85". Localised numbers go through a formatter that
 * also groups thousands, and a Turkish "1.000" read back here, where a dot is a decimal mark, would
 * be one - so the box keeps the plain form and no grouping at all.
 *
 * **Both decimal marks are read in every language**, as the price field does: "42,85" and "42.85"
 * are the same rate whichever keyboard typed them. The flip side is that a grouping separator
 * cannot be told apart and is refused as a second decimal mark - and [format] never writes one, so
 * nothing the app shows is refused.
 *
 * The round trip is exact: [parse] of [format] gives back the same scaled rate for every value the
 * store can hold. The screen relies on that - a box the user never touched is saved by reading its
 * text, like every other box, so what is written is always what was on screen.
 */
internal object RateText {

    /** [ExchangeRateTable.MAX_RATE] expressed in whole units, for comparing against input. */
    private val MAX_RATE_UNITS: BigDecimal =
        BigDecimal.valueOf(ExchangeRateTable.MAX_RATE, ExchangeRatesViewModel.RATE_DECIMAL_DIGITS)
            .stripTrailingZeros()

    /** Renders a [ExchangeRateTable.RATE_SCALE]-scaled rate: "42.85", not "42.8500". */
    fun format(scaledRate: Long): String =
        BigDecimal.valueOf(scaledRate, ExchangeRatesViewModel.RATE_DECIMAL_DIGITS)
            .stripTrailingZeros()
            .toPlainString()

    /**
     * Reads what the user typed into a scaled rate, with the same care as the price field.
     *
     * BigDecimal rather than Double: a rate multiplies every amount that passes through it, so an
     * inexact reading spreads a proportional error across the whole total rather than losing a
     * kuruş once.
     */
    fun parse(rawRate: String): RateResult {
        val normalized = rawRate.trim().replace(',', '.')
        if (normalized.isEmpty()) {
            return RateResult.Invalid(UiText.Resource(R.string.error_rate_empty))
        }
        val amount = normalized.toBigDecimalOrNull()
            ?: return RateResult.Invalid(UiText.Resource(R.string.error_rate_invalid))
        if (amount.signum() <= 0) {
            // The hard one: CurrencyConverter divides by the target rate, so a zero here is a
            // division by zero rather than a merely odd number.
            return RateResult.Invalid(UiText.Resource(R.string.error_rate_not_positive))
        }
        // Trailing zeros do not count: "42.8500" is two decimals written long.
        if (amount.stripTrailingZeros().scale() > ExchangeRatesViewModel.RATE_DECIMAL_DIGITS) {
            return RateResult.Invalid(UiText.Resource(R.string.error_rate_too_many_decimals))
        }
        if (amount > MAX_RATE_UNITS) {
            // The limit is handed to the message instead of being written into it, so the two
            // cannot drift apart when the ceiling changes.
            return RateResult.Invalid(
                UiText.Resource(R.string.error_rate_too_large, listOf(MAX_RATE_UNITS.toPlainString()))
            )
        }
        val scaled = amount.movePointRight(ExchangeRatesViewModel.RATE_DECIMAL_DIGITS).toLong()
        if (scaled < ExchangeRateTable.MIN_RATE) {
            // Unreachable given the two checks above - a positive number with at most four
            // decimals is at least one scale unit. Kept because what it protects is a correctness
            // requirement, not a nicety: nothing below this line may hand a zero divisor onward.
            return RateResult.Invalid(UiText.Resource(R.string.error_rate_not_positive))
        }
        return RateResult.Valid(scaled)
    }
}
