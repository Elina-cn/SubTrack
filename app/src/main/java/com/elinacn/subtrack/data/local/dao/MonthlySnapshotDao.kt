package com.elinacn.subtrack.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.elinacn.subtrack.data.local.entity.MonthlySnapshotEntity
import kotlinx.coroutines.flow.Flow

/** Reads and writes for the monthly snapshots table. Room generates the implementation. */
@Dao
interface MonthlySnapshotDao {

    /**
     * Oldest month first, re-emitted whenever the table changes.
     *
     * The opposite order to the subscriptions list, on purpose: this is read as a series, and a
     * trend runs left to right in time. Sorting it in the query rather than at the caller keeps
     * the promise in one place.
     */
    @Query("SELECT * FROM monthly_snapshots ORDER BY period ASC")
    fun observeAll(): Flow<List<MonthlySnapshotEntity>>

    /** The row for this month, or null when nothing has been recorded for it. */
    @Query("SELECT * FROM monthly_snapshots WHERE period = :period")
    suspend fun getByPeriod(period: Int): MonthlySnapshotEntity?

    /**
     * Stores the month's figure, replacing what was there.
     *
     * `@Upsert`, not `@Insert(onConflict = REPLACE)`: REPLACE resolves a conflict in SQLite by
     * deleting the existing row and inserting a new one, which fires delete triggers and would
     * take any child rows with it. `@Upsert` inserts, and on a key conflict updates in place - the
     * row keeps its identity, which is what "the same month, revised" means.
     */
    @Upsert
    suspend fun upsert(entity: MonthlySnapshotEntity)
}
