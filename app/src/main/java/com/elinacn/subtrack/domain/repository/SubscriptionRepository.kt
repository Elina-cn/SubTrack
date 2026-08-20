package com.elinacn.subtrack.domain.repository

import com.elinacn.subtrack.domain.model.Subscription
import kotlinx.coroutines.flow.Flow

/**
 * The only way the rest of the app reaches stored subscriptions.
 *
 * Declared here, in domain, so nothing above needs to know a database exists. The Room-backed
 * implementation lives in data and can be swapped without touching a caller.
 */
interface SubscriptionRepository {

    /** Emits the whole list again whenever it changes. */
    fun observeAll(): Flow<List<Subscription>>

    /** The subscription with this id, or null when no such subscription exists. */
    suspend fun getById(id: Long): Subscription?

    /** Stores a new subscription and returns the id it was given. */
    suspend fun insert(subscription: Subscription): Long

    /** Overwrites the stored subscription carrying the same id. */
    suspend fun update(subscription: Subscription)

    /** Removes the subscription with this id. A no-op when the id is unknown. */
    suspend fun deleteById(id: Long)
}
