package com.elinacn.subtrack

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.elinacn.subtrack.reminder.PaymentReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Entry point of the app.
 *
 * It builds nothing by hand - the graph lives in di/ and @HiltAndroidApp assembles it. What it
 * does own is WorkManager: the worker takes injected dependencies, so WorkManager has to be told
 * which factory can build it, and that replaces the automatic startup initialiser.
 */
@HiltAndroidApp
class SubTrackApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderScheduler: PaymentReminderScheduler

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
        reminderScheduler.schedule()
    }
}
