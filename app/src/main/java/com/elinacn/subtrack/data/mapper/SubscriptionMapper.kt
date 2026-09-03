package com.elinacn.subtrack.data.mapper

import com.elinacn.subtrack.data.local.entity.SubscriptionEntity
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    currency = Currency.fromCode(currencyCode),
    billingPeriod = billingPeriod.toBillingPeriod(),
    nextPaymentDate = nextPaymentDate?.toLocalDate(),
    category = category.toCategory(),
    iconKey = iconKey,
    createdAt = createdAt
)

/** Flattens the domain model into a storable row. */
fun Subscription.toEntity(): SubscriptionEntity = SubscriptionEntity(
    id = id,
    name = name,
    priceInCents = price.cents,
    currencyCode = currency.name,
    billingPeriod = billingPeriod.name,
    nextPaymentDate = nextPaymentDate?.toEpochMillis(),
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

/**
 * Reads a stored instant as the calendar day it fell on here.
 *
 * The system zone is deliberate: a renewal date is something the user picked off a calendar, so it
 * has to come back as the day they saw. Storing the instant and reading it in UTC would shift the
 * date by one for anyone east or west of Greenwich for part of the day.
 */
private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** Writes a calendar day back as the instant its local midnight fell on. */
private fun LocalDate.toEpochMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
