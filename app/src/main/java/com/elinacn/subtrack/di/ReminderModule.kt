package com.elinacn.subtrack.di

import com.elinacn.subtrack.reminder.AndroidReminderDeliveryStatus
import com.elinacn.subtrack.reminder.AndroidReminderNotificationStatus
import com.elinacn.subtrack.reminder.PaymentReminderScheduler
import com.elinacn.subtrack.reminder.ReminderDeliveryStatus
import com.elinacn.subtrack.reminder.ReminderNotificationStatus
import com.elinacn.subtrack.reminder.ReminderTimeChanger
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

    @Binds
    @Singleton
    abstract fun bindReminderDeliveryStatus(
        impl: AndroidReminderDeliveryStatus
    ): ReminderDeliveryStatus

    /** Unscoped here: the scheduler is a singleton of its own, and this hands out that one. */
    @Binds
    abstract fun bindReminderTimeChanger(impl: PaymentReminderScheduler): ReminderTimeChanger
}
