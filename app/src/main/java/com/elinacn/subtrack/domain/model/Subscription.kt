package com.elinacn.subtrack.domain.model

/**
 * A subscription as the app talks about it, independent of how it is stored.
 *
 * [nextPaymentDate] and [createdAt] are epoch milliseconds. They are plain Long rather than a date
 * type so this layer stays free of platform APIs; formatting is the UI's job.
 */
data class Subscription(
    val id: Long,
    val name: String,
    val price: Money,
    /** ISO 4217 code, e.g. "TRY". */
    val currencyCode: String,
    val billingPeriod: BillingPeriod,
    /** Null until the user sets a renewal date; the field ships in v1.0 to avoid a migration. */
    val nextPaymentDate: Long?,
    val category: SubscriptionCategory,
    /** Key for the built-in icon lookup, e.g. "netflix". Null falls back to a generic icon. */
    val iconKey: String?,
    val createdAt: Long
)
