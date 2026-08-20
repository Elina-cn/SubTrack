package com.elinacn.subtrack.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.elinacn.subtrack.data.local.dao.SubscriptionDao
import com.elinacn.subtrack.data.local.entity.SubscriptionEntity

/**
 * The app's SQLite database.
 *
 * exportSchema is on. Room then writes a JSON description of every version under app/schemas, and
 * that file is what later migrations are checked against - without it Room cannot tell what
 * version 1 looked like once the entity has moved on, and the DAO instrumentation tests planned
 * for phase 7 have nothing to validate against. The cost is one committed JSON file per version.
 *
 * No instance is built here. Phase 3 constructs it by hand in the Application class, phase 4 hands
 * that job to Hilt.
 */
@Database(
    entities = [SubscriptionEntity::class],
    version = 1,
    exportSchema = true
)
abstract class SubTrackDatabase : RoomDatabase() {

    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        const val NAME = "subtrack.db"
    }
}
