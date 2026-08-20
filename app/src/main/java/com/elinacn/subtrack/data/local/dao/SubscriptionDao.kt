package com.elinacn.subtrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.elinacn.subtrack.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

/** Reads and writes for the subscriptions table. Room generates the implementation. */
@Dao
interface SubscriptionDao {

    /**
     * Newest first, re-emitted whenever the table changes.
     *
     * Not suspend: a Flow query keeps observing, so the caller collects instead of awaiting. Room
     * runs it off the main thread on its own.
     */
    @Query("SELECT * FROM subscriptions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    /** The row with this id, or null when no such row exists. */
    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getById(id: Long): SubscriptionEntity?

    /** Stores a new row and returns the id SQLite assigned it. */
    @Insert
    suspend fun insert(entity: SubscriptionEntity): Long

    /** Overwrites the row carrying the same id. */
    @Update
    suspend fun update(entity: SubscriptionEntity)

    /** Removes the row with this id. A no-op when the id is unknown. */
    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
