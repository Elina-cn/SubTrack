package com.elinacn.subtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What the subscriptions added up to in one calendar month.
 *
 * One row per month, overwritten as the total moves, so the table is a history of months rather
 * than a log of edits (ARCHITECTURE §19). Primitives only, like [SubscriptionEntity]: money in
 * minor units, the currency as its code, no type converters.
 */
@Entity(tableName = "monthly_snapshots")
data class MonthlySnapshotEntity(
    /**
     * The month, written as `year * 100 + month` - September 2026 is `202609`.
     *
     * **Sortable and unique in one column.** Numeric order is chronological because the month is
     * always two digits, and one value can only mean one calendar month, so it makes a natural
     * primary key: an upsert lands on the row that is already there instead of opening a second
     * one for the same month.
     *
     * Not an instant: a month is a calendar fact, not a point in time, and epoch millis would need
     * a time zone to be read back - the wrong month for anyone near a boundary. Not two columns
     * either: that would need a composite key and a two-column ORDER BY to say the same thing.
     * It also stays readable in a `run-as` dump, which is how this table gets checked on a device.
     */
    @PrimaryKey
    val period: Int,
    /** The monthly total in minor units, in [currencyCode]. */
    val totalInCents: Long,
    /**
     * ISO 4217 code, e.g. "TRY".
     *
     * Stored rather than assumed: the user can change the currency totals are shown in, and a row
     * written last month has to keep saying which currency its figure was in. Without it a later
     * reader would have to guess, and would guess with today's preference.
     */
    val currencyCode: String,
    /** Epoch millis of the write, so a reader can tell how fresh the month's figure is. */
    val recordedAt: Long
)
