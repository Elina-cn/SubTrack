package com.elinacn.subtrack.domain.model

/**
 * How often a subscription renews.
 *
 * [paymentsPerYear] is the whole of the normalisation: a price is what it costs once, and a year
 * is the one span every period divides into a whole number of times. Multiplying by it turns any
 * price into a yearly cost exactly, with no division and so no rounding; the monthly figure is
 * that yearly cost over twelve, rounded once.
 *
 * **Weekly counts 52 payments, not 365,25 / 7 = 52,18.** The exact figure looks more careful and
 * is worse: nobody is billed a fifth of a week, the extra decimals cannot be explained to a user
 * who checks the arithmetic by hand, and they change the answer by a fraction of a percent that no
 * subscription price is accurate to anyway. Fifty-two is what "weekly" means to the person paying.
 */
enum class BillingPeriod(val paymentsPerYear: Int) {
    MONTHLY(12),
    YEARLY(1),
    WEEKLY(52)
}
