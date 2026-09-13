package com.elinacn.subtrack.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.elinacn.subtrack.data.local.dao.MonthlySnapshotDao
import com.elinacn.subtrack.data.local.dao.SubscriptionDao
import com.elinacn.subtrack.data.local.entity.MonthlySnapshotEntity
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
 *
 * Still version 1 with the snapshots table added in phase 12a. Nothing has shipped, so there is no
 * user data to carry across and no migration to write; the version is regenerated instead and
 * app/schemas/1.json goes with it (ARCHITECTURE, "Şema sürümlemesi"). That rule ends at release.
 */
@Database(
    entities = [SubscriptionEntity::class, MonthlySnapshotEntity::class],
    version = 1,
    exportSchema = true
)
abstract class SubTrackDatabase : RoomDatabase() {

    abstract fun subscriptionDao(): SubscriptionDao

    abstract fun monthlySnapshotDao(): MonthlySnapshotDao

    companion object {
        const val NAME = "subtrack.db"
    }
}
