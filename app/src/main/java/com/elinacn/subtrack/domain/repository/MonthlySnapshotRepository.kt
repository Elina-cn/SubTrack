package com.elinacn.subtrack.domain.repository

import com.elinacn.subtrack.domain.model.MonthlySnapshot
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/**
 * The only way the rest of the app reaches recorded monthly totals.
 *
 * Declared here, in domain, so nothing above needs to know a database exists - the same split the
 * app already uses for subscriptions.
 */
interface MonthlySnapshotRepository {

    /** Emits the whole series, oldest month first, again whenever it changes. */
    fun observeAll(): Flow<List<MonthlySnapshot>>

    /** The figure recorded for this month, or null when none has been. */
    suspend fun getByPeriod(period: YearMonth): MonthlySnapshot?

    /** Records the month's figure, replacing whatever was there for that month. */
    suspend fun upsert(snapshot: MonthlySnapshot)
}
