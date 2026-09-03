package com.elinacn.subtrack.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/** Supplies the clock the app reads "today" from. */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    /**
     * Injected rather than read through LocalDate.now() at the call site, so a test can hand in a
     * fixed clock and assert on a countdown without waiting for a particular date to come round.
     *
     * systemDefaultZone, not UTC: a renewal date is a day on the user's own calendar.
     */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
