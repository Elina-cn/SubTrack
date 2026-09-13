package com.elinacn.subtrack.fake

import com.elinacn.subtrack.domain.model.MonthlySnapshot
import com.elinacn.subtrack.domain.repository.MonthlySnapshotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.YearMonth

/**
 * In-memory stand-in for the recorded monthly totals.
 *
 * Written by hand rather than mocked, per ARCHITECTURE section 11. It upserts the way the real
 * table does - keyed by the month, one row per month - so a test can assert that a second change
 * in the same month revised the row instead of opening another one.
 *
 * [upserts] records every write in order, which is how a test tells "nothing was written" apart
 * from "the same value was written again".
 */
class FakeMonthlySnapshotRepository : MonthlySnapshotRepository {

    private val stored = MutableStateFlow<Map<YearMonth, MonthlySnapshot>>(emptyMap())

    val upserts = mutableListOf<MonthlySnapshot>()

    /** Thrown by [upsert] when set, so the failure path can be exercised. */
    var failOnUpsert: Exception? = null

    override fun observeAll(): Flow<List<MonthlySnapshot>> =
        stored.map { rows -> rows.values.sortedBy { it.period } }

    override suspend fun getByPeriod(period: YearMonth): MonthlySnapshot? = stored.value[period]

    override suspend fun upsert(snapshot: MonthlySnapshot) {
        failOnUpsert?.let { throw it }
        upserts += snapshot
        stored.value = stored.value + (snapshot.period to snapshot)
    }

    /** Every row currently held, oldest month first. */
    val rows: List<MonthlySnapshot>
        get() = stored.value.values.sortedBy { it.period }
}
