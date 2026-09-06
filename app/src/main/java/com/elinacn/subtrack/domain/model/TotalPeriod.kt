package com.elinacn.subtrack.domain.model

/**
 * Which span a total is expressed over.
 *
 * Only two, and neither is [BillingPeriod]: a subscription can be billed weekly, but nobody asks
 * what their subscriptions cost per week. Reusing [BillingPeriod] here would offer a third
 * constant the screen has no answer for.
 *
 * [partsOfAYear] is how many of these spans a year holds, which is exactly what the yearly cost
 * has to be divided by to reach one.
 */
enum class TotalPeriod(val partsOfAYear: Int) {
    MONTHLY(12),
    YEARLY(1)
}
