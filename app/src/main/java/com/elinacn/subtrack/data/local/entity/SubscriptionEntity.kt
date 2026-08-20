package com.elinacn.subtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row of the subscriptions table.
 *
 * Deliberately made of primitives only: enums are stored by name and money as minor units, so the
 * table stays readable and Room needs no type converters. Turning those back into real types is
 * the mapper's job, and this class never leaves the data layer.
 */
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    /** Assigned by SQLite. AUTOINCREMENT never reuses an id, unlike the counter it replaces. */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val priceInCents: Long,
    /** ISO 4217 code, e.g. "TRY". */
    val currencyCode: String,
    /** BillingPeriod.name. */
    val billingPeriod: String,
    /** Epoch millis, null until the user sets a renewal date. */
    val nextPaymentDate: Long?,
    /** SubscriptionCategory.name. */
    val category: String,
    val iconKey: String?,
    /** Epoch millis. */
    val createdAt: Long
)
