package com.elinacn.subtrack.di

import com.elinacn.subtrack.ui.theme.AndroidDynamicColorSupport
import com.elinacn.subtrack.ui.theme.DynamicColorSupport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Points the theme interfaces at their platform-backed implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ThemeModule {

    /** @Binds for the same reason as the repositories: the implementation builds itself. */
    @Binds
    @Singleton
    abstract fun bindDynamicColorSupport(
        impl: AndroidDynamicColorSupport
    ): DynamicColorSupport
}
