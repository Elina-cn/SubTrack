package com.elinacn.subtrack.data.repository

import com.elinacn.subtrack.data.local.dao.MonthlySnapshotDao
import com.elinacn.subtrack.data.mapper.toDomain
import com.elinacn.subtrack.data.mapper.toDomainOrNull
import com.elinacn.subtrack.data.mapper.toEntity
import com.elinacn.subtrack.data.mapper.toStoredPeriod
import com.elinacn.subtrack.domain.model.MonthlySnapshot
import com.elinacn.subtrack.domain.repository.MonthlySnapshotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

/**
 * Room-backed implementation.
 *
 * The same boundary [SubscriptionRepositoryImpl] draws: MonthlySnapshotEntity stops here, so no
 * caller can come to depend on a column name or on how a month is encoded.
 *
 * No withContext anywhere - Room already runs suspend queries and Flow queries off the main
 * thread (ARCHITECTURE section 8).
 */
class MonthlySnapshotRepositoryImpl @Inject constructor(
    private val dao: MonthlySnapshotDao
) : MonthlySnapshotRepository {

    override fun observeAll(): Flow<List<MonthlySnapshot>> =
        dao.observeAll().map { entities -> entities.toDomain() }

    override suspend fun getByPeriod(period: YearMonth): MonthlySnapshot? =
        dao.getByPeriod(period.toStoredPeriod())?.toDomainOrNull()

    override suspend fun upsert(snapshot: MonthlySnapshot) = dao.upsert(snapshot.toEntity())
}
