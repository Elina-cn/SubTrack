package com.elinacn.subtrack.data.mapper

import com.elinacn.subtrack.data.local.entity.SubscriptionEntity
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory

/**
 * Rebuilds the domain model from a stored row.
 *
 * Enum names are matched rather than passed to enumValueOf, which throws. A row written by a newer
 * build, or edited by hand, would otherwise crash the read; falling back keeps the list usable and
 * loses only that one field.
 */
fun SubscriptionEntity.toDomain(): Subscription = Subscription(
    id = id,
    name = name,
    price = Money(priceInCents),
    currencyCode = currencyCode,
    billingPeriod = billingPeriod.toBillingPeriod(),
    nextPaymentDate = nextPaymentDate,
    category = category.toCategory(),
    iconKey = iconKey,
    createdAt = createdAt
)

/** Flattens the domain model into a storable row. */
fun Subscription.toEntity(): SubscriptionEntity = SubscriptionEntity(
    id = id,
    name = name,
    priceInCents = price.cents,
    currencyCode = currencyCode,
    billingPeriod = billingPeriod.name,
    nextPaymentDate = nextPaymentDate,
    category = category.name,
    iconKey = iconKey,
    createdAt = createdAt
)

/** Maps each stored row to its domain model. */
fun List<SubscriptionEntity>.toDomain(): List<Subscription> = map { it.toDomain() }

private fun String.toBillingPeriod(): BillingPeriod =
    BillingPeriod.entries.firstOrNull { it.name == this } ?: BillingPeriod.MONTHLY

private fun String.toCategory(): SubscriptionCategory =
    SubscriptionCategory.entries.firstOrNull { it.name == this } ?: SubscriptionCategory.OTHER
