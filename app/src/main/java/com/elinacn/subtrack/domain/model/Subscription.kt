package com.elinacn.subtrack.domain.model

import java.time.LocalDate

/**
 * A subscription as the app talks about it, independent of how it is stored.
 *
 * [createdAt] is epoch milliseconds - a moment in time, which is what it is.
 *
 * [nextPaymentDate] is a [LocalDate] instead, because "three days left" is a difference in calendar
 * days, not in milliseconds. Storage keeps it as epoch millis; the mapper converts. See
 * ARCHITECTURE section 17.
 */
data class Subscription(
    val id: Long,
    val name: String,
    val price: Money,
    /** What [price] is denominated in. Totals convert into a single currency for display. */
    val currency: Currency,
    val billingPeriod: BillingPeriod,
    /** Null when the user did not give one; the countdown is simply not shown then. */
    val nextPaymentDate: LocalDate?,
    val category: SubscriptionCategory,
    /** Key for the built-in icon lookup, e.g. "netflix". Null falls back to a generic icon. */
    val iconKey: String?,
    val createdAt: Long
)
