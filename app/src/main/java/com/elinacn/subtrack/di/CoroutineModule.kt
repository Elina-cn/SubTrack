package com.elinacn.subtrack.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Marks the one scope that lives as long as the process.
 *
 * A qualifier because CoroutineScope is far too common a type to hand out unqualified - anything
 * asking for one would silently get this, including something that should have died with a screen.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Supplies work that has to outlive every screen. */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    /**
     * For background bookkeeping that belongs to the app rather than to a screen - today, the
     * monthly snapshot recorder.
     *
     * SupervisorJob so one failing child cannot take the rest down with it, and Dispatchers.Default
     * because nothing started here touches the UI. It is never cancelled: the process ending is
     * what ends it.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
