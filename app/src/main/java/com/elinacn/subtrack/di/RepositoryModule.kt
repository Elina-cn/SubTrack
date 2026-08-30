package com.elinacn.subtrack.di

import com.elinacn.subtrack.data.repository.SettingsRepositoryImpl
import com.elinacn.subtrack.data.repository.SubscriptionRepositoryImpl
import com.elinacn.subtrack.domain.repository.SettingsRepository
import com.elinacn.subtrack.domain.repository.SubscriptionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Points each repository interface at its storage-backed implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * @Binds, not @Provides: the implementation already knows how to build itself through its
     * @Inject constructor, so the only missing piece is which type to hand out when someone asks
     * for the interface. Dagger answers that with a cast and generates no factory, whereas a
     * @Provides body is a method it has to keep and call.
     */
    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(
        impl: SubscriptionRepositoryImpl
    ): SubscriptionRepository

    /** Same reasoning as above; the implementation is DataStore-backed rather than Room-backed. */
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository
}
