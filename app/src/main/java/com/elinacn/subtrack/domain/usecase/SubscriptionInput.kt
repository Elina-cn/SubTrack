package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Why a typed name was refused. */
enum class NameProblem { EMPTY }

/** Why a typed price was refused. */
enum class PriceProblem {
    EMPTY,
    MALFORMED,
    NOT_POSITIVE,
    TOO_LARGE,
    TOO_MANY_DECIMALS
}

/** Why a chosen date was refused. */
enum class DateProblem { TOO_FAR_AHEAD }

/** A parsed price, or the reason it could not be parsed. */
sealed interface PriceResult {
    data class Valid(val money: Money) : PriceResult
    data class Invalid(val problem: PriceProblem) : PriceResult
}

/**
 * What the app will accept into a subscription, wherever it is typed.
 *
 * **One copy of the rules.** Two forms now feed the same table - the add sheet and the edit screen
 * (phase 15) - and a second copy of "what counts as a price" would drift the day one of them
 * changed. So the rules live here, once, and both ViewModels call them.
 *
 * Here rather than in ui/ because these are facts about a subscription, not about a screen: a
 * price of nought is not a subscription whichever form typed it. The **messages** are a screen's
 * business and stay there - this object answers with a reason, and `ui/common/FormErrors.kt` turns
 * a reason into a sentence. That split is also what keeps domain free of Android (§1): a
 * `UiText` here would drag a string resource id into a package that compiles without androidx.
 *
 * Pure functions over their arguments; today's date arrives as a parameter rather than being read
 * from a clock inside (§17).
 */
object SubscriptionInput {

    /** Minor units per unit, true for all four supported currencies; see [Money]. */
    const val MINOR_UNIT_DIGITS = 2

    /**
     * How far ahead a renewal date may be set.
     *
     * A product ceiling like [MAX_PRICE], not a technical one: ten years is longer than any
     * subscription anyone signs, so beyond it is a typed year.
     */
    const val MAX_YEARS_AHEAD = 10L

    /**
     * A product ceiling, not a Long limit.
     *
     * Long would not complain until roughly 92 quadrillion kuruş, so guarding against overflow
     * catches nothing a person could plausibly type. One million per billing period is already
     * three orders of magnitude above the priciest real subscription, and leaves room for weaker
     * currencies - while still rejecting a slipped keypress that adds digits.
     */
    val MAX_PRICE: BigDecimal = BigDecimal("1000000")

    /** The name as it would be stored: surrounding whitespace is not part of it. */
    fun trimName(rawName: String): String = rawName.trim()

    /** Refuses a name that is nothing but whitespace. */
    fun validateName(rawName: String): NameProblem? =
        if (trimName(rawName).isEmpty()) NameProblem.EMPTY else null

    /**
     * Reads what the user typed into whole minor units.
     *
     * BigDecimal, never Double: "159,99" has to come back out as exactly 15999 kuruş, and binary
     * floating point cannot promise that.
     *
     * Both the ceiling and the decimal count are checked before converting, so nothing is ever
     * quietly reshaped on the way to storage.
     */
    fun parsePrice(rawPrice: String): PriceResult {
        val normalized = rawPrice.trim().replace(',', '.')
        if (normalized.isEmpty()) return PriceResult.Invalid(PriceProblem.EMPTY)

        val amount = normalized.toBigDecimalOrNull()
            ?: return PriceResult.Invalid(PriceProblem.MALFORMED)
        if (amount.signum() <= 0) return PriceResult.Invalid(PriceProblem.NOT_POSITIVE)
        if (amount > MAX_PRICE) return PriceResult.Invalid(PriceProblem.TOO_LARGE)
        // Trailing zeros do not count: "159.990" is two decimals written long, "159.999" is three.
        if (amount.stripTrailingZeros().scale() > MINOR_UNIT_DIGITS) {
            return PriceResult.Invalid(PriceProblem.TOO_MANY_DECIMALS)
        }

        val cents = amount
            .movePointRight(MINOR_UNIT_DIGITS)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
        return PriceResult.Valid(Money(cents))
    }

    /**
     * A past date is fine - someone entering a subscription they already have knows when it last
     * renewed, and the date is an anchor rather than a deadline (§17). Only the far future is
     * refused, for the same reason as [MAX_PRICE]: it catches a slipped keystroke in the year, not
     * a plausible entry.
     */
    fun validateDate(nextPaymentDate: LocalDate?, today: LocalDate): DateProblem? {
        if (nextPaymentDate == null) return null
        val furthest = today.plusYears(MAX_YEARS_AHEAD)
        return if (nextPaymentDate.isAfter(furthest)) DateProblem.TOO_FAR_AHEAD else null
    }
}
