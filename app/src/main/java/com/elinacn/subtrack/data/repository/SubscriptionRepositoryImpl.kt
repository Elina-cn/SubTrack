package com.elinacn.subtrack.data.repository

import com.elinacn.subtrack.data.local.dao.SubscriptionDao
import com.elinacn.subtrack.data.mapper.toDomain
import com.elinacn.subtrack.data.mapper.toEntity
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed implementation.
 *
 * This is the boundary where SubscriptionEntity stops: every value handed upwards is a domain
 * Subscription, so no caller can come to depend on a column name.
 *
 * No withContext anywhere - Room already runs suspend queries and Flow queries off the main
 * thread, so adding a dispatcher would only move work twice (ARCHITECTURE section 8).
 */
class SubscriptionRepositoryImpl @Inject constructor(
    private val dao: SubscriptionDao
) : SubscriptionRepository {

    override fun observeAll(): Flow<List<Subscription>> =
        dao.observeAll().map { entities -> entities.toDomain() }

    override suspend fun getById(id: Long): Subscription? = dao.getById(id)?.toDomain()

    override suspend fun insert(subscription: Subscription): Long =
        dao.insert(subscription.toEntity())

    override suspend fun update(subscription: Subscription) = dao.update(subscription.toEntity())

    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}
