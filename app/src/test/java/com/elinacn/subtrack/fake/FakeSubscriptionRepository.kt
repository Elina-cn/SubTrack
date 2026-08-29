package com.elinacn.subtrack.fake

import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory stand-in for the real repository.
 *
 * Written by hand rather than mocked, per ARCHITECTURE section 11: the interface is already
 * defined in domain, and a fake that actually stores rows lets a test assert on the resulting
 * list instead of on which methods were called.
 *
 * [inserted] and [deletedIds] record the arguments as they arrived, so a test can check that a
 * write was skipped entirely - which is the interesting case for validation.
 */
class FakeSubscriptionRepository : SubscriptionRepository {

    private val stored = MutableStateFlow<List<Subscription>>(emptyList())
    private var nextId = 1L

    val inserted = mutableListOf<Subscription>()
    val deletedIds = mutableListOf<Long>()

    override fun observeAll(): Flow<List<Subscription>> = stored.asStateFlow()

    override suspend fun getById(id: Long): Subscription? = stored.value.firstOrNull { it.id == id }

    /** Mirrors Room: id 0 means "assign one", anything else is kept as given. */
    override suspend fun insert(subscription: Subscription): Long {
        inserted += subscription
        val assignedId = if (subscription.id == 0L) nextId++ else subscription.id
        stored.value = stored.value + subscription.copy(id = assignedId)
        return assignedId
    }

    override suspend fun update(subscription: Subscription) {
        stored.value = stored.value.map { if (it.id == subscription.id) subscription else it }
    }

    override suspend fun deleteById(id: Long) {
        deletedIds += id
        stored.value = stored.value.filterNot { it.id == id }
    }

    /** Seeds rows without going through insert, so the recorded calls stay meaningful. */
    fun setSubscriptions(subscriptions: List<Subscription>) {
        stored.value = subscriptions
    }
}
