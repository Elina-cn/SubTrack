package com.elinacn.subtrack

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.elinacn.subtrack.di.ApplicationScope
import com.elinacn.subtrack.domain.usecase.MonthlySnapshotRecorder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * Entry point of the app.
 *
 * It builds nothing by hand - the graph lives in di/ and @HiltAndroidApp assembles it. What it
 * does own is WorkManager: the worker takes injected dependencies, so WorkManager has to be told
 * which factory can build it, and that replaces the automatic startup initialiser.
 *
 * Queuing the reminder job is no longer done here (phase 16x). A process also starts for the job
 * itself and for boot, when there is nothing to check; the check belongs to the moment the user
 * opens the app, which is MainActivity.onStart.
 */
@HiltAndroidApp
class SubTrackApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var snapshotRecorder: MonthlySnapshotRecorder

    /** Owned here rather than inside the recorder, so the process decides how long it watches. */
    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    /**
     * A property, not a function: androidx.work turned this member of Configuration.Provider into
     * one in 2.9 and the old override no longer compiles.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        // Hilt injects the fields above during super.onCreate(), so nothing here may run before it.
        super.onCreate()
        // Watches what the subscriptions cost and keeps the current month's row in step. Started
        // here because it belongs to no screen: a change made anywhere has to be recorded.
        snapshotRecorder.start(applicationScope)
    }
}
