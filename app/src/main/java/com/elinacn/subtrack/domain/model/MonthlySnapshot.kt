package com.elinacn.subtrack.domain.model

import java.time.YearMonth

/**
 * What the subscriptions cost in one calendar month.
 *
 * Always a **monthly** figure, whatever the screen happens to be showing: a yearly total is the
 * same fact presented differently, and storing both spans would let them disagree. Phase 13 reads
 * this series to answer "what changed since last month".
 *
 * [YearMonth] rather than a date: there is no day here, and offering one would invite a caller to
 * read meaning into it. It is pure Java, so domain stays free of Android (§1).
 */
data class MonthlySnapshot(
    val period: YearMonth,
    /** The monthly total, in [currency]. */
    val total: Money,
    /**
     * The currency the figure was recorded in.
     *
     * Carried with the row rather than read from today's preference: the user can change which
     * currency totals are shown in, and an old month must not be silently relabelled.
     */
    val currency: Currency,
    /** Epoch millis of the write. */
    val recordedAt: Long
)
