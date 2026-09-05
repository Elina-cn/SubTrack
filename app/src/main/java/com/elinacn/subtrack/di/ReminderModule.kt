package com.elinacn.subtrack.di

import com.elinacn.subtrack.reminder.AndroidReminderNotificationStatus
import com.elinacn.subtrack.reminder.ReminderNotificationStatus
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Points the reminder interfaces at their platform-backed implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {

    /** @Binds for the same reason as the repositories: the implementation builds itself. */
    @Binds
    @Singleton
    abstract fun bindReminderNotificationStatus(
        impl: AndroidReminderNotificationStatus
    ): ReminderNotificationStatus
}
