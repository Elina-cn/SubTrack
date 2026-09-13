package com.elinacn.subtrack.data.mapper

import com.elinacn.subtrack.data.local.entity.MonthlySnapshotEntity
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.MonthlySnapshot
import com.elinacn.subtrack.domain.model.Money
import java.time.YearMonth

/**
 * Rebuilds the domain model from a stored row, or null when the row does not describe a month.
 *
 * Nullable for the same reason [SubscriptionEntity][com.elinacn.subtrack.data.local.entity.SubscriptionEntity]
 * falls back on an unknown enum name: a row written by a newer build, or edited by hand, must not
 * crash the read of the whole series. There is no sensible fallback for a month that does not
 * exist, though - inventing one would put a made-up point on a chart - so the row is dropped.
 */
fun MonthlySnapshotEntity.toDomainOrNull(): MonthlySnapshot? {
    val month = period.toYearMonthOrNull() ?: return null
    return MonthlySnapshot(
        period = month,
        total = Money(totalInCents),
        currency = Currency.fromCode(currencyCode),
        recordedAt = recordedAt
    )
}

/** Flattens the domain model into a storable row. */
fun MonthlySnapshot.toEntity(): MonthlySnapshotEntity = MonthlySnapshotEntity(
    period = period.toStoredPeriod(),
    totalInCents = total.cents,
    currencyCode = currency.name,
    recordedAt = recordedAt
)

/** Maps each readable row to its domain model, skipping any that is not a month. */
fun List<MonthlySnapshotEntity>.toDomain(): List<MonthlySnapshot> = mapNotNull { it.toDomainOrNull() }

/**
 * The month as one sortable integer: September 2026 is `202609`.
 *
 * The month always takes two decimal digits, so comparing the numbers compares the months.
 */
fun YearMonth.toStoredPeriod(): Int = year * MONTHS_PER_STORED_YEAR + monthValue

/** Reads a stored period back, or null when it is not a month anyone could have written. */
private fun Int.toYearMonthOrNull(): YearMonth? {
    val month = this % MONTHS_PER_STORED_YEAR
    val year = this / MONTHS_PER_STORED_YEAR
    return if (month in 1..12 && year > 0) YearMonth.of(year, month) else null
}

/** Not twelve: the month occupies the last two decimal digits, so a year is a hundred of them. */
private const val MONTHS_PER_STORED_YEAR = 100
