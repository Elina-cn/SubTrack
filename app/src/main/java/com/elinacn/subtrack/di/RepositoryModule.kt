package com.elinacn.subtrack.di

import com.elinacn.subtrack.data.repository.SubscriptionRepositoryImpl
import com.elinacn.subtrack.domain.repository.SubscriptionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Points the repository interface at its Room-backed implementation. */
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
}
