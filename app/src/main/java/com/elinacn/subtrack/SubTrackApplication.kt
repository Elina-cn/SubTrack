package com.elinacn.subtrack

import android.app.Application
import androidx.room.Room
import com.elinacn.subtrack.data.local.SubTrackDatabase
import com.elinacn.subtrack.data.repository.SubscriptionRepositoryImpl
import com.elinacn.subtrack.domain.repository.SubscriptionRepository
import dagger.hilt.android.HiltAndroidApp

/**
 * Builds the object graph by hand.
 *
 * Both properties are `by lazy`, so nothing is constructed until something first asks for it and
 * opening the database does not slow down a cold start.
 *
 * Phase 4 replaces this class body with Hilt. Keeping it manual first is deliberate: the wiring
 * below is what @Module and @Provides will be doing, and doing it by hand once makes it visible.
 */
@HiltAndroidApp
class SubTrackApplication : Application() {

    private val database: SubTrackDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            SubTrackDatabase::class.java,
            SubTrackDatabase.NAME
        ).build()
    }

    /**
     * Reached from elsewhere by casting the application context:
     * `(context.applicationContext as SubTrackApplication).subscriptionRepository`.
     * That cast is the manual-DI tax; Hilt removes it in phase 4.
     */
    val subscriptionRepository: SubscriptionRepository by lazy {
        SubscriptionRepositoryImpl(database.subscriptionDao())
    }
}
