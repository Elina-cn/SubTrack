package com.elinacn.subtrack.di

import android.content.Context
import androidx.room.Room
import com.elinacn.subtrack.data.local.SubTrackDatabase
import com.elinacn.subtrack.data.local.dao.MonthlySnapshotDao
import com.elinacn.subtrack.data.local.dao.SubscriptionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Supplies the Room objects, all living as long as the application itself. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * One database per process. Without @Singleton every injection point would open its own
     * connection to the same file.
     *
     * @ApplicationContext is what makes this safe to hold forever - an activity context would leak
     * the activity for the lifetime of the app.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SubTrackDatabase =
        Room.databaseBuilder(
            context,
            SubTrackDatabase::class.java,
            SubTrackDatabase.NAME
        ).build()

    /** A view onto the database above, so it needs no scope of its own. */
    @Provides
    fun provideSubscriptionDao(database: SubTrackDatabase): SubscriptionDao =
        database.subscriptionDao()

    /** The same, for the monthly totals table. */
    @Provides
    fun provideMonthlySnapshotDao(database: SubTrackDatabase): MonthlySnapshotDao =
        database.monthlySnapshotDao()
}
